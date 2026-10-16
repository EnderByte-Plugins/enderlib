# EnderLib Database

An asynchronous, production-grade database library for Minecraft Java server plugins.

This is **not a plugin**. It is a plain Java library you add as a Maven dependency, with no Bukkit,
Spigot or Paper types anywhere in it. It runs equally well inside a plugin, a proxy, or a standalone
service that shares the same database.

It exists to solve one problem properly: talking to a database from a Minecraft server without ever
blocking the server thread, at a scale where a thousand players and a dozen servers share one
database.

**Supported engines:** PostgreSQL, MySQL, MariaDB, SQLite, H2.

**Documentation:** [docs/](README.md) — getting started, API reference, and a cookbook of
copy-paste examples.

---

## Installation

```xml
<dependency>
    <groupId>com.enderbyte</groupId>
    <artifactId>enderlib-database-sql</artifactId>
    <version>1.0.0</version>
</dependency>
```

Add `enderlib-database-migrations` if you call `database.migrations()`, and add **one** JDBC driver (scope
`compile`). Drivers are not dependencies of this library.

Full install notes, modules, shading and class loaders:
[Getting started](getting-started.md),
[Minecraft integration](minecraft-integration.md).

---

## Quick start

```java
public final class MyPlugin extends JavaPlugin {

    private Database database;

    @Override
    public void onEnable() {
        database = Database.builder()
                .type(DatabaseType.POSTGRESQL)
                .name("players")
                .host("localhost")
                .port(5432)
                .database("minecraft")
                .username("minecraft")
                .password(getConfig().getString("database.password"))
                .poolSize(10)
                .build()
                .connect();

        database.migrations()
                .source(MigrationSources.classpath("migrations", getClassLoader()))
                .migrateAndWait();
    }

    @Override
    public void onDisable() {
        database.close();
    }
}
```

Then, anywhere in your plugin:

```java
database.queryAsync("SELECT name, balance FROM players WHERE uuid = ?", player.getUniqueId())
        .thenAccept(result -> result.first().ifPresent(row ->
                getLogger().info(row.getString("name") + " has " + row.getLong("balance"))));
```

A future's callbacks run on a database thread. Hop back to the server thread before touching the
Bukkit API. Never call `.join()` or `.get()` on the server thread.

---

## Documentation

| Section | Contents |
| --- | --- |
| [Getting started](getting-started.md) | Install, lifecycle, the one threading rule |
| [API reference](README.md#api-reference) | `Database`, queries, mapping, transactions, migrations, config |
| [Cookbook](cookbook/README.md) | Player profiles, economy, leaderboards, generated keys, custom types, work queues |
| [Performance](performance.md) | Pool sizing across a network |
| [Minecraft integration](minecraft-integration.md) | Shading, class loaders, `META-INF/services` |
| [Engines](api/engines.md) | PostgreSQL, MySQL, MariaDB, SQLite, H2 |

The examples are not decorative: `ReadmeExamplesTest` and `DocsExamplesTest` in `enderlib-database-sql` run
the H2-runnable snippets, so the build fails if a snippet drifts from the API.

---

## Building from source

```bash
mvn clean install          # build and run unit tests
mvn test                   # tests only
mvn verify -Pintegration-tests   # adds Testcontainers tests (needs Docker)
```

Unit tests run against in-memory H2 and file-backed SQLite and need nothing installed.
PostgreSQL, MySQL and MariaDB are covered by Testcontainers and are gated behind the
`integration-tests` profile so a normal build never requires Docker.

```
enderlib/
├── enderlib-database-api/           interfaces, config, query builder, annotations — no implementation
├── enderlib-database-core/          executor, retries, metrics, caching, entity mapping
├── enderlib-database-sql/           JDBC provider: HikariCP, dialects, transactions, repositories
└── enderlib-database-migrations/    versioned migrations with checksums and a cross-server lock
```
