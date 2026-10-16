# Queries

Placeholders are `?`, and values are always bound — there is no API in this library that
concatenates a value into SQL text. The query builder validates identifiers against a strict
pattern, so injection is not possible through it. Use raw SQL freely when it is clearer; the two
mix without friction.

## Raw SQL

```java
CompletableFuture<QueryResult> result =
        database.queryAsync("SELECT * FROM players WHERE balance > ?", 1000);

CompletableFuture<Integer> updated = database.executeAsync(
        "UPDATE players SET balance = balance + ? WHERE uuid = ?", 50, uuid);
```

```java
database.queryAsync(Query.raw(
        "SELECT * FROM players ORDER BY balance DESC FETCH FIRST ? ROWS ONLY", 10));
```

`Query.raw` is the escape hatch for window functions, CTEs and engine-specific syntax the builder
does not model. Keep using `?` placeholders.

## QueryResult

A `QueryResult` is fully read before the connection goes back to the pool, so you can hold it, pass
it to another thread, and keep it as long as you like without pinning a connection. Results are
immutable and safe to hand to the server thread.

```java
QueryResult result = ...;

result.isEmpty();
result.size();
result.first();                       // Optional<Row>
result.firstOrNull();                 // Row or null
result.all();                         // List<Row>
result.stream();
result.value(Long.class);             // single value, ideal for SELECT COUNT(*)
result.first(PlayerData.class);       // Optional<T> via entity mapping
result.map(PlayerData.class);         // List<T>
result.first(row -> ...);             // Optional<T> via RowMapper
result.map(row -> ...);
result.generatedKeys();               // after insertReturningKeysAsync
```

`QueryResult` is `Iterable<Row>`.

## Row

Column lookup is case-insensitive. Typed getters return the type's zero value for SQL `NULL`; use
`optional(...)` or `isNull(...)` when the difference matters. A missing column throws
`MappingException`.

```java
row.getString("name");
row.getInt("level");
row.getLong("balance");
row.getBoolean("banned");
row.getFloat("ratio");
row.getDouble("ratio");
row.getBigDecimal("precise_amount");
row.getUuid("uuid");                  // native UUID, CHAR(36) or BINARY(16) — all handled
row.getInstant("last_seen");
row.getLocalDate("birthday");
row.getLocalDateTime("started_at");
row.getBytes("inventory");
row.getEnum("rank", Rank.class);
row.optional("nickname", String.class);
row.get("custom_column", MyType.class);
row.get(0);                           // 0-based position, useful for unaliased aggregates
row.isNull("nickname");
row.hasColumn("balance");
row.columns();                        // Set<String> in selection order
row.asMap();                          // immutable snapshot
```

`row.get(column, type)` and `optional` go through the `ConverterRegistry`, so custom
`TypeConverter`s apply here too.

Mapping without annotations:

```java
database.queryAsync(query, row -> new PlayerData(
        row.getUuid("uuid"), row.getString("name"), row.getLong("balance")));
```

## The query builder

```java
import static com.enderbyte.enderlib.database.api.query.Conditions.*;

SelectQuery top = Query.select("uuid", "name", "balance")
        .from("players")
        .where("balance", Operator.GREATER_THAN, 0)
        .and("banned", Operator.EQUALS, false)
        .orderBy("balance", SortDirection.DESCENDING)
        .limit(10);

database.queryAsync(top, PlayerData.class).thenAccept(this::showLeaderboard);
```

Builders implement `SqlStatement`. Pass them to `queryAsync` / `insertAsync` / `updateAsync` /
`deleteAsync` directly.

| Factory | Starts |
| --- | --- |
| `Query.select("uuid", "name")` | `SELECT` of those columns |
| `Query.select()` | `SELECT *` |
| `Query.select(Expression...)` | `SELECT` of aggregates / aliases |
| `Query.selectFrom("players")` | `SELECT * FROM players` |
| `Query.selectCount("players")` | `SELECT COUNT(*) FROM players` |
| `Query.insertInto("players")` | `INSERT` |
| `Query.update("players")` | `UPDATE` |
| `Query.deleteFrom("players")` | `DELETE` |
| `Query.raw(sql, params...)` | hand-written SQL |

### SELECT

```java
Query.select("uuid", "name")
        .distinct()
        .from("players", "p")
        .leftJoin("stats", Conditions.columns("p.uuid", Operator.EQUALS, "stats.uuid"))
        .where("p.world", "survival")
        .groupBy("p.name")
        .having(raw("SUM(s.kills) > ?", 100))
        .orderBy("p.name")
        .limit(50)
        .offset(100);
```

`offset` requires a `limit` on MySQL, MariaDB and SQLite.

Join helpers: `join` (inner), `leftJoin`, `rightJoin`, `innerJoin`, `fullJoin`, and the general
`join(JoinType, table, alias, on)`. `JoinType.CROSS` is the only join that may omit `ON`.

Row locks — only meaningful **inside a transaction**; ignored by SQLite:

```java
Query.selectFrom("pending_rewards")
        .where("claimed", false)
        .limit(1)
        .forUpdateSkipLocked();     // or forUpdate() to wait for the lock instead
```

See [Work queue](../cookbook/work-queue.md).

### INSERT

```java
database.insertAsync(Query.insertInto("players")
        .value("uuid", uuid)
        .value("name", name)
        .value("balance", 100L));

database.insertAsync(Query.insertInto("players")
        .values(Map.of("uuid", uuid, "name", name, "balance", 100L)));
```

Multi-row insert — do not mix with `value()`:

```java
database.insertAsync(Query.insertInto("players")
        .columns("uuid", "name", "balance")
        .row(uuid1, "Alex", 10L)
        .row(uuid2, "Steve", 20L));
```

For very large volumes prefer `batchInsertAsync`, which streams through a JDBC batch instead of
building one enormous statement.

Upsert — "save this player, create the row if it is new":

```java
database.insertAsync(Query.insertInto("players")
        .value("uuid", uuid)
        .value("name", name)
        .value("balance", balance)
        .onConflictUpdateAll("uuid"));

database.insertAsync(Query.insertInto("players")
        .value("uuid", uuid)
        .value("name", name)
        .value("balance", balance)
        .onConflictUpdate(List.of("uuid"), List.of("name", "balance")));
```

Renders as `ON CONFLICT` on PostgreSQL and SQLite, `ON DUPLICATE KEY UPDATE` on MySQL/MariaDB, and
`MERGE ... KEY` on H2. MySQL/MariaDB infer the unique key and ignore `conflictColumns`. H2's
`MERGE` overwrites every listed column, so a narrower `updateColumns` list cannot be honoured
exactly.

### UPDATE

```java
database.updateAsync(Query.update("players")
        .set("balance", 250L)
        .set("last_seen", Instant.now())
        .where("uuid", uuid));

database.updateAsync(Query.update("players")
        .set(Map.of("name", name, "world", world))
        .where("uuid", uuid));
```

Atomic increment — prefer this over read-modify-write when several servers may touch the same row:

```java
database.updateAsync(Query.update("players")
        .set("balance", Expressions.raw("balance + ?", amount))
        .where("uuid", uuid));
```

### DELETE

```java
database.deleteAsync(Query.deleteFrom("players").where("uuid", uuid));
```

### Guard rails

`UPDATE` and `DELETE` without a `WHERE` clause are refused. If you really mean every row:

```java
Query.update("sessions").set("active", false).allowFullTableUpdate();
Query.deleteFrom("sessions").allowFullTableDelete();
```

## Conditions

```java
import static com.enderbyte.enderlib.database.api.query.Conditions.*;

Query.selectFrom("players")
        .where(and(
                equal("world", "survival"),
                or(greaterThan("balance", 1000), equal("rank", "VIP"))))
        .whereIn("uuid", onlinePlayerIds)
        .whereBetween("level", 10, 50)
        .whereLike("name", "Steve%")
        .whereNotNull("last_seen");
```

Consecutive `where(...)` / `and(...)` calls combine with `AND`; use `or(...)` for the alternative.
Builders are mutable and intended for one thread; the compiled `SqlQuery` is immutable.

| Factory | SQL |
| --- | --- |
| `equal` / `notEqual` | `=` / `<>` |
| `greaterThan` / `lessThan` | `>` / `<` |
| `like` / `notLike` | `LIKE` / `NOT LIKE` (pattern bound as a parameter) |
| `in` / `notIn` (collection) | `IN (?, ?)` — empty collection renders as `1 = 0` / `1 = 1` |
| `in` / `notIn` (SelectQuery) | `IN (SELECT ...)` |
| `between` / `notBetween` | inclusive |
| `isNull` / `isNotNull` | `IS NULL` |
| `exists` / `notExists` | `EXISTS (SELECT ...)` |
| `not(condition)` | `NOT (...)` |
| `and` / `or` / `composite` | grouping |
| `columns(left, op, right)` | compare two columns (joins) |
| `raw(sql, params...)` | escape hatch |

Shorthands on the builder: `whereIn`, `whereNotIn`, `whereIn(column, subquery)`, `whereBetween`,
`whereLike`, `whereNull`, `whereNotNull`, `whereExists`.

Subquery `IN` and `EXISTS`:

```java
Query.selectFrom("players")
        .whereIn("uuid", Query.select("uuid").from("bans").where("active", true))
        .whereExists(Query.selectFrom("stats").where("stats.uuid", Operator.EQUALS, "players.uuid"));
```

The condition factories validate their first argument as a column identifier and will reject an
expression. That is what stops SQL from being smuggled in through a column name. Use
`Conditions.raw` for `SUM(s.kills) > ?` and similar.

## Expressions

```java
Query.select(
                Expressions.column("p.name"),
                Expressions.sum("s.kills").as("kills"),
                Expressions.countDistinct("s.uuid").as("unique_killers"))
        .from("players", "p")
        .join(JoinType.LEFT, "stats", "s", raw("s.uuid = p.uuid"))
        .groupBy("p.name")
        .orderBy(Expressions.column("kills"), SortDirection.DESCENDING);
```

| Factory | SQL |
| --- | --- |
| `column("p.uuid")` | qualified column |
| `all()` | `*` |
| `count()` / `count("col")` / `countDistinct("col")` | `COUNT` |
| `sum` / `avg` / `min` / `max` | aggregates |
| `subquery(select)` | scalar subquery |
| `value(x)` | bound literal |
| `raw(sql, params...)` | escape hatch |
| `aliased(expr, "alias")` or `expr.as("alias")` | `AS` |

`Conditions.raw` vs `Expressions.raw` vs `Query.raw`: a predicate, a select-list fragment, and a
whole statement. All three require `?` placeholders. Never build the string from user input.
