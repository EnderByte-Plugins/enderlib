# EnderLib Database documentation

An asynchronous, production-grade database library for Minecraft Java server plugins. This is **not
a plugin**: it is a plain Java library with no Bukkit, Spigot or Paper types in it.

**Supported engines:** PostgreSQL, MySQL, MariaDB, SQLite, H2.

Start here, then pick a page by what you are doing.

## Start

- [Getting started](getting-started.md) — install, modules, drivers, lifecycle, and the one threading rule
- [Minecraft integration](minecraft-integration.md) — class loaders, shading, and `META-INF/services`
- [Performance](performance.md) — pool sizing across a network of servers

## API reference

- [Database](api/database.md) — `Database`, `DatabaseBuilder`, `DatabaseConnection`, `DatabaseRegistry`
- [Queries](api/queries.md) — raw SQL, the query builder, `Row`, `QueryResult`
- [Mapping](api/mapping.md) — entities, `TypeConverter`, `CrudRepository`, `SqlRepository`
- [Transactions](api/transactions.md) — transactions, savepoints, batches, row locks
- [Migrations](api/migrations.md) — versioned migrations and schema helpers
- [Configuration](api/configuration.md) — builder options and the full `ConfigLoader` key table
- [Operations](api/operations.md) — caching, metrics, Micrometer, health checks, errors
- [Engines](api/engines.md) — PostgreSQL, MySQL, MariaDB, SQLite, H2

## Cookbook

Copy-paste recipes for the patterns plugins actually write. See [Cookbook](cookbook/README.md).

| Recipe | What it shows |
| --- | --- |
| [Plugin lifecycle](cookbook/plugin-lifecycle.md) | `onEnable` / `onDisable`, connect, migrate, hop back to the server thread |
| [Player profiles](cookbook/player-profiles.md) | Record entity, repository, join load, cache scoped to online players |
| [Economy transfer](cookbook/economy-transfer.md) | Transaction, optimistic locking, atomic increments |
| [Leaderboards](cookbook/leaderboards.md) | `ORDER BY` / `LIMIT`, joins, aggregates |
| [Generated keys](cookbook/generated-keys.md) | `@Id(generated = true)` and `insertReturningKeysAsync` |
| [Custom types](cookbook/custom-types.md) | `TypeConverter` for a location or blob |
| [Work queue](cookbook/work-queue.md) | `FOR UPDATE SKIP LOCKED` across servers |
| [Health and metrics](cookbook/health-and-metrics.md) | `pingAsync()`, snapshots, Micrometer |

The examples in this tree are compile-checked: `DocsExamplesTest` in `enderlib-database-sql` runs the
H2-runnable snippets, so the build fails if a snippet drifts from the API. Bukkit-only fragments
(scheduler hops, `JavaPlugin`) stay in markdown only.
