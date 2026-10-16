# Getting started

## Why this library

Most plugin database code is written twice: once badly, then again after the first lag report. The
recurring problems are always the same, and each one is addressed here deliberately.

| Problem | How this library handles it |
| --- | --- |
| Queries on the server thread freeze the game | Every operation returns a `CompletableFuture` and runs on a dedicated pool |
| A new connection per query | HikariCP pooling, tuned per engine |
| String-concatenated SQL | Values are *only* ever bound as parameters; identifiers are validated |
| A dead database hangs the server | Bounded queues, timeouts on every operation, and a shutdown that refuses to hang |
| A blip takes the plugin down | Transient failures are retried with jittered backoff; real bugs are not |
| Schema changes by hand | Versioned, checksummed migrations with a cross-server lock |
| Two servers overwrite each other | Optimistic locking via `@Version` |
| No idea why it is slow | Query counters, latency percentiles and live pool statistics |

## Requirements

- **Java 17** or newer. This covers every current Minecraft server release; Java 21 and 25 work
  unchanged.
- **Maven** (or any tool that reads Maven repositories).
- A **JDBC driver** for your engine — see below.

## Installation

Add the JDBC provider:

```xml
<dependency>
    <groupId>com.enderbyte</groupId>
    <artifactId>enderlib-database-sql</artifactId>
    <version>1.0.0</version>
</dependency>
```

`enderlib-database-sql` pulls in `enderlib-database-api`, `enderlib-database-core` and HikariCP transitively, so that one
dependency is enough for normal use.

If you call `database.migrations()`, also add:

```xml
<dependency>
    <groupId>com.enderbyte</groupId>
    <artifactId>enderlib-database-migrations</artifactId>
    <version>1.0.0</version>
</dependency>
```

### The modules

| Module | Add it when | Pulls in |
| --- | --- | --- |
| `enderlib-database-api` | You want to compile against the interfaces only | SLF4J |
| `enderlib-database-core` | Never directly; it is the shared implementation | `enderlib-database-api` |
| `enderlib-database-sql` | **Always** — this is the JDBC provider | `enderlib-database-core`, HikariCP |
| `enderlib-database-migrations` | You use `database.migrations()` | `enderlib-database-sql` |

Optional, both detected at runtime and safely absent:

| Dependency | Enables |
| --- | --- |
| `com.github.ben-manes.caffeine:caffeine` | The real caching layer (without it, caches pass through to the loader) |
| `io.micrometer:micrometer-core` | Bridging metrics into an existing Micrometer registry |

### Your JDBC driver

Drivers are **not** dependencies of this library. Shipping five drivers in every plugin bloats the
jar and starts version fights with drivers the server already has. Add the one you use:

```xml
<!-- pick one -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>42.7.3</version>
</dependency>
<!-- com.mysql:mysql-connector-j -->
<!-- org.mariadb.jdbc:mariadb-java-client -->
<!-- org.xerial:sqlite-jdbc -->
<!-- com.h2database:h2 -->
```

Make sure the scope is `compile` (the default) so the driver ends up inside your plugin jar. A
`provided` driver compiles fine and then vanishes at runtime.

If the driver is missing, `build()` fails immediately with a message naming the exact Maven
coordinate — not a confusing `No suitable driver` error later on.

The library loads the driver class itself and hands its name to the pool, so it never depends on
`DriverManager` finding the driver. That matters because `DriverManager` searches with the system
class loader, which cannot see anything inside a plugin jar. If you need a driver other than the
engine's usual one — the MariaDB driver against MySQL, or a cloud connector — name it explicitly:

```java
Database.builder()
        .type(DatabaseType.MYSQL)
        .driverClassName("org.mariadb.jdbc.Driver")
        // ...
```

Shading, class loaders and `META-INF/services` are covered in
[Minecraft integration](minecraft-integration.md).

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

    public Database database() {
        return database;
    }
}
```

Then, anywhere in your plugin:

```java
database.queryAsync("SELECT name, balance FROM players WHERE uuid = ?", player.getUniqueId())
        .thenAccept(result -> result.first().ifPresent(row ->
                getLogger().info(row.getString("name") + " has " + row.getLong("balance"))));
```

A complete Paper plugin, including hopping back to the server thread, is in
[Plugin lifecycle](cookbook/plugin-lifecycle.md).

## Lifecycle

`build()` does no I/O. `connect()` opens the pool and verifies the database answers.

Connecting during `onEnable` blocks until the database responds or the connect timeout expires, and
that is intentional: the server is not serving players yet, and starting a plugin whose database is
unreachable is worse than a slow boot. If you would rather let the server finish starting, use
`connectAsync()`.

`DatabaseBuilder.connect()` is shorthand for `build().connect()`.

`close()` is idempotent, waits briefly for in-flight work, and then gives up rather than hanging the
server on shutdown. Always call it from `onDisable`. `closeAsync()` does the same without blocking
the caller.

`isConnected()` is true after a successful `connect()` and false after `close()`. `pingAsync()` is
the non-throwing health check for a `/status` command.

Skip the connect-time validation only when you have a reason — for example a database that is
brought up after the server:

```java
Database.builder()
        .type(DatabaseType.POSTGRESQL)
        .validateOnConnect(false)
        .build();
```

`buildConfig()` returns the `DatabaseConfig` the builder would produce, without creating a
database. Useful for tests and for dumping the resolved settings.

## The one rule: never block the server thread

Every method on `Database` returns a `CompletableFuture` and does its work on the library's own
threads. That part is handled for you. The part you own is what happens *after*.

**A future's callbacks run on a database thread.** Anything touching the Bukkit API must hop back:

```java
database.queryFirstAsync(Query.selectFrom("players").where("uuid", uuid), PlayerData.class)
        .thenAccept(found -> Bukkit.getScheduler().runTask(plugin, () ->
                found.ifPresent(data -> player.sendMessage("Balance: " + data.balance()))));
```

Never call `.join()` or `.get()` on the server thread. That converts an async call back into a
blocking one and reintroduces exactly the lag this library prevents. The only reasonable place for
`join()` is plugin startup, before players connect — which is why `migrateAndWait()` exists and is
named the way it is.

Always attach an error handler. A future that fails silently is a bug you will find much later:

```java
future.exceptionally(error -> {
    getLogger().log(Level.SEVERE, "Could not load player data", error);
    return null;
});
```

Use `database.executor()` to chain follow-up *database* work off the server thread. Do not submit
long-running unrelated tasks to it.

## Next

- [Queries](api/queries.md) — run SQL and read results
- [Mapping](api/mapping.md) — entities and repositories
- [Cookbook](cookbook/README.md) — complete recipes
