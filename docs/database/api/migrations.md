# Migrations and schema helpers

## Migrations

Put `.sql` files in your plugin's resources:

```
src/main/resources/migrations/
├── V1__create_players.sql
├── V2__add_balance_column.sql
└── V3__index_last_seen.sql
```

The name is `V<version>__<description>.sql`. A file may hold several statements separated by
semicolons; semicolons inside quoted strings and `$$ ... $$` blocks are left alone.

```java
database.migrations()
        .source(MigrationSources.classpath("migrations", getClassLoader()))
        .historyTable("myplugin_migrations")
        .serverId(getServer().getName())
        .migrateAndWait();
```

Pass **your own class loader**: on a Minecraft server every plugin has its own, and the library's
loader cannot see your resources.

Give each plugin its own `historyTable` when several plugins share a database, otherwise their
version numbers collide. Default table name is `schema_migrations`.

`migrateAndWait()` is the startup path: it applies every pending migration and blocks. The server
is not yet accepting players, and a half-migrated schema is worse than a slightly slower boot.
Never call it once players are online. `migrate()` is the same work as a
`CompletableFuture<MigrationResult>`.

### MigrationResult

| Field / method | Meaning |
| --- | --- |
| `applied()` | migrations this run applied, in order |
| `appliedCount()` | size of that list |
| `initialVersion()` / `currentVersion()` | before and after |
| `duration()` | wall-clock time |
| `skipped()` | another server held the lock and had already brought the schema up to date |
| `isUpToDate()` | this run applied nothing |

### Other sources

```java
MigrationSources.directory(getDataFolder().toPath().resolve("migrations"));
MigrationSources.of(new SqlMigration(1, "create players", "CREATE TABLE ..."));
MigrationSources.composite(fromJar, fromDataFolder);
```

`classpath(folder)` without a class loader uses the library's loader — fine in tests, wrong on a
Minecraft server.

For a change that needs logic, implement `Migration` directly and do the work in `apply`.

### The rules

- **Never edit an applied migration.** The checksum is verified on every startup, and a mismatch
  aborts with an explanation. Add a new migration instead.
- **A failed migration blocks startup** until an operator fixes the schema and calls `repair()`.
  Continuing on a half-migrated schema is worse than refusing to start.
- **Version numbers must be unique** across a history table.

### Adopting an existing schema

```java
database.migrations().source(source).baselineVersion(3).migrateAndWait();
```

Everything up to and including V3 is treated as already applied.

### Inspecting

```java
database.migrations().pending();          // CompletableFuture<List<Migration>>
database.migrations().applied();          // CompletableFuture<List<AppliedMigration>>
database.migrations().currentVersion();
database.migrations().validate();         // checksums only, changes nothing
database.migrations().repair();           // clears failed entries; returns how many were removed
```

### Multi-server safety

Fifty servers may start at the same time and all see the same pending migration. The service takes
a lock row in the history table before doing anything, so exactly one server migrates and the rest
wait and then continue with an up-to-date schema. A lock held longer than five minutes is treated
as abandoned, so a server that crashes mid-migration cannot block the network forever.

Set `serverId` so the history table records who applied what.

MySQL and MariaDB **commit DDL implicitly**, so a failed migration cannot be rolled back on those
engines. PostgreSQL can.

`database.migrations()` throws `ConfigurationException` if `enderlib-database-migrations` is not on the
classpath.

## Schema helpers

For tests, first-run bootstrapping and admin commands. **Migrations remain the production
mechanism** — they are versioned, checksummed and safe when many servers start at once, none of
which is true of ad-hoc DDL.

Nothing here runs automatically. The library never creates a table you did not ask for.

```java
database.executeAsync(Schema.createTable("players")
        .ifNotExists()
        .column(ColumnDefinition.of("uuid", SqlType.UUID).primaryKey())
        .column(ColumnDefinition.of("name", SqlType.VARCHAR).length(16).notNull())
        .column(ColumnDefinition.of("balance", SqlType.BIGINT).notNull().defaultLiteral(0))
        .column(ColumnDefinition.of("last_seen", SqlType.TIMESTAMP_WITH_TIMEZONE)));

database.executeAsync(Schema.createIndex("idx_players_name").on("players").columns("name"));
database.executeAsync(Schema.createIndex("idx_players_name_unique").on("players")
        .columns("name").unique());
database.executeAsync(Schema.addColumn("players",
        ColumnDefinition.of("world", SqlType.VARCHAR).length(64)));
database.executeAsync(Schema.dropColumn("players", "world"));
database.executeAsync(Schema.dropIndex("idx_players_name", "players"));
database.executeAsync(Schema.dropTable("players"));
```

Indexes default to `IF NOT EXISTS`. `alwaysCreate()` emits the index unconditionally and fails if
it already exists. MySQL/MariaDB need the table name on `dropIndex`; other engines use
`DROP INDEX IF EXISTS`.

Composite primary keys: `Schema.createTable("link").primaryKey("a", "b")`. Single-column keys are
simpler as `ColumnDefinition.primaryKey()`.

### ColumnDefinition

```java
ColumnDefinition.of("uuid", SqlType.UUID).notNull().primaryKey();
ColumnDefinition.of("name", SqlType.VARCHAR).length(16).notNull().unique();
ColumnDefinition.of("balance", SqlType.DECIMAL).precision(19).scale(4).defaultLiteral(0);
ColumnDefinition.of("created_at", SqlType.TIMESTAMP_WITH_TIMEZONE)
        .defaultExpression("CURRENT_TIMESTAMP");
ColumnDefinition.of("id", SqlType.BIGINT).autoIncrement().primaryKey();
```

`defaultLiteral` is for numbers, booleans and strings from your own code, not user input.
`autoIncrement` maps to `SERIAL` / `BIGSERIAL` on PostgreSQL, `AUTO_INCREMENT` on MySQL/MariaDB/H2,
and `INTEGER` on SQLite.

### SqlType

Engine-neutral. `SqlType.UUID` becomes a native `UUID` on PostgreSQL and H2, `CHAR(36)` on MySQL,
and `TEXT` on SQLite. `TIMESTAMP_WITH_TIMEZONE` is the right choice for `last_seen`. `JSON` is
available where the engine has it.
