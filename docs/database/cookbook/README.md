# Cookbook

Copy-paste recipes for the patterns plugins actually write. Each one shows setup, the call, and
the result shape. H2-runnable snippets are compile-checked by `DocsExamplesTest`.

| Recipe | What it shows |
| --- | --- |
| [Plugin lifecycle](plugin-lifecycle.md) | Paper `onEnable` / `onDisable`, `connect()` vs `connectAsync()`, `migrateAndWait()`, hop back with the scheduler |
| [Player profiles](player-profiles.md) | Record entity, `CrudRepository`, join load, cache scoped to online players |
| [Economy transfer](economy-transfer.md) | Transaction that moves coins; `OptimisticLockException` retry; atomic `Expressions.raw` |
| [Leaderboards](leaderboards.md) | Query builder `ORDER BY` / `LIMIT`, join + aggregate, `queryAsync(..., Class)` |
| [Generated keys](generated-keys.md) | `@Id(generated = true)` + `insertReturningKeysAsync` |
| [Custom types](custom-types.md) | `TypeConverter` for a location stored as text |
| [Work queue](work-queue.md) | `forUpdateSkipLocked()` inside a transaction across servers |
| [Health and metrics](health-and-metrics.md) | `pingAsync()` for `/status`, `metrics().snapshot()`, Micrometer bridge |

Read [Getting started](../getting-started.md) first if you have not added the dependency yet.
