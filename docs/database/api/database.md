# Database

Entry point of the library: `com.enderbyte.enderlib.database.api.Database`.

An instance owns a connection pool, an executor and optionally caches and metrics. Create one per
logical database and keep it for the plugin's lifetime. There is no global singleton, and several
instances (and several plugins) coexist in one JVM.

Implementations are thread-safe. Everything except `connect()` is asynchronous.

```java
Database database = Database.builder()
        .type(DatabaseType.POSTGRESQL)
        .name("players")
        .host("localhost")
        .port(5432)
        .database("minecraft")
        .username("minecraft")
        .password(password)
        .poolSize(10)
        .build()
        .connect();
```

`Database.builder()` is the only supported way to construct one. Full options:
[Configuration](configuration.md).

## Identity

| Method | Returns |
| --- | --- |
| `name()` | Logical name, used in pool names, thread names and metrics |
| `type()` | `DatabaseType` |
| `config()` | The `DatabaseConfig` this instance was built from |
| `dialect()` | `SqlDialect` used to compile query builders for this engine |
| `executor()` | The executor that runs database work |
| `isConnected()` | `true` after a successful `connect()` and before `close()` |

## Lifecycle

```java
database.connect();                          // blocks; for onEnable
database.connectAsync();                     // CompletableFuture<Database>
database.pingAsync();                        // CompletableFuture<Boolean> — never throws
database.close();                            // onDisable; idempotent
database.closeAsync();                       // CompletableFuture<Void>
```

`connect()` opens the pool and, unless `validateOnConnect(false)` was set, verifies the database
answers. It blocks until the database responds or the connect timeout elapses.

`pingAsync()` is the health check. It completes with `true` when the database answered and `false`
when it did not — suitable for a `/myplugin status` command. See
[Health and metrics](../cookbook/health-and-metrics.md).

## Queries and updates

```java
CompletableFuture<QueryResult> rows =
        database.queryAsync("SELECT * FROM players WHERE balance > ?", 1000);

CompletableFuture<List<PlayerData>> mapped =
        database.queryAsync(Query.selectFrom("players").where("uuid", uuid), PlayerData.class);

CompletableFuture<Optional<PlayerData>> first =
        database.queryFirstAsync(Query.selectFrom("players").where("uuid", uuid), PlayerData.class);

CompletableFuture<Integer> updated =
        database.executeAsync("UPDATE players SET balance = balance + ? WHERE uuid = ?", 50, uuid);
```

Typed overloads:

| Method | Result |
| --- | --- |
| `queryAsync(String sql, Object... parameters)` | `QueryResult` |
| `queryAsync(SqlStatement statement)` | `QueryResult` |
| `queryAsync(SqlStatement statement, Class<T> type)` | `List<T>` mapped entities |
| `queryAsync(SqlStatement statement, RowMapper<T> mapper)` | `List<T>` from a hand-written mapper |
| `queryFirstAsync(SqlStatement statement, Class<T> type)` | `Optional<T>` |
| `executeAsync(...)` | affected-row count |
| `insertAsync(InsertQuery)` | inserted-row count |
| `insertReturningKeysAsync(InsertQuery)` | `QueryResult` whose `generatedKeys()` holds new keys |
| `updateAsync(UpdateQuery)` | updated-row count |
| `deleteAsync(DeleteQuery)` | deleted-row count |

Builders implement `SqlStatement` and are passed directly — there is no `.build()` step.

Generated keys: [Generated keys](../cookbook/generated-keys.md). Query builder:
[Queries](queries.md).

## Batches

```java
database.batchInsertAsync("players", List.of("uuid", "name", "balance"), rows);
database.batchAsync("UPDATE players SET balance = ? WHERE uuid = ?", updates);
database.batchUpdateAsync("UPDATE players SET balance = ? WHERE uuid = ?", updates);
```

`batchAsync` and `batchUpdateAsync` are the same idea: one statement, many parameter sets, one
transaction. Prefer `batchInsertAsync` when the statement is a plain insert. Details:
[Transactions](transactions.md#batch-operations).

## Transactions

```java
database.runTransactionAsync(tx -> {
    tx.execute("UPDATE accounts SET balance = balance - ? WHERE uuid = ?", amount, from);
    tx.execute("UPDATE accounts SET balance = balance + ? WHERE uuid = ?", amount, to);
});

database.transactionAsync(tx ->
        tx.query("SELECT balance FROM accounts WHERE uuid = ?", uuid)
                .value(Long.class)
                .orElse(0L));
```

The two have different names on purpose: as overloads, `tx -> tx.execute(...)` matches both and
fails to compile. Overloads taking `TransactionOptions` exist for isolation, read-only and timeout.
Full page: [Transactions](transactions.md).

## Escape hatch: a raw connection

For driver-specific work the rest of the API does not cover (PostgreSQL `COPY`, JSONB operators,
vendor session settings):

```java
database.withConnectionAsync(connection -> {
    try (PreparedStatement statement = connection.jdbc().prepareStatement(
            "SELECT payload @> ?::jsonb FROM extras WHERE uuid = ?")) {
        statement.setString(1, "{\"rank\":\"VIP\"}");
        statement.setObject(2, uuid);
        // ...
        return result;
    }
});
```

`DatabaseConnection` is **synchronous**. It is only ever handed to code that already runs on a
database worker thread, so the blocking is contained. Do not close the connection or the
`jdbc()` handle, and do not change auto-commit — the library owns both.

| Method | Purpose |
| --- | --- |
| `query(sql, ...)` / `query(SqlStatement)` | run a select |
| `execute(sql, ...)` / `execute(SqlStatement)` | run an update |
| `executeReturningKeys(...)` | insert and read generated keys |
| `batch(sql, parameterSets)` | JDBC batch |
| `jdbc()` | underlying `java.sql.Connection` |

A `Transaction` *is* a `DatabaseConnection`, plus savepoints and `setRollbackOnly()`.

## Subsystems

```java
database.migrations();                                 // needs enderlib-database-migrations on the classpath
database.repository(PlayerData.class, UUID.class);     // CrudRepository; same instance if called twice
database.cache("players", UUID.class, PlayerData.class);
database.caches();                                     // CacheManager
database.converters();                                 // ConverterRegistry
database.metrics();                                    // DatabaseMetrics
database.executor();
```

`repository(...)` returns the same instance for the same entity type. `migrations()` throws
`ConfigurationException` if the migrations module is missing.

## Several databases

```java
DatabaseRegistry registry = new DatabaseRegistry();
registry.register(playerDatabase);
registry.register(analyticsDatabase);

registry.require("players").queryAsync(...);
registry.get("analytics");           // Optional<Database>
registry.all();                      // Map<String, Database>
registry.size();
registry.unregister("analytics");    // removes without closing
registry.closeAll();                 // in onDisable — closes every remaining instance
```

`DatabaseRegistry` lives in `enderlib-database-core` (`com.enderbyte.enderlib.database.core.DatabaseRegistry`). It is
an ordinary object, not a static singleton: two plugins each own theirs.

`register` throws `ConfigurationException` if the name is taken. `require` throws if the name is
missing. `closeAll()` closes every registered database even if one close fails, then clears the
map. `close()` is the same thing (`AutoCloseable`).
