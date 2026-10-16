# Operations

Caching, metrics, health checks and errors.

## Caching

Requires Caffeine on the classpath. Without it the builder still works and returns a pass-through
cache that always calls the loader, so your code does not branch on whether caching is available.
Disable it with `.cachingEnabled(false)` the same way.

```java
Cache<UUID, PlayerData> cache = database.cache("players", UUID.class, PlayerData.class)
        .maximumSize(100_000)
        .expireAfterWrite(Duration.ofMinutes(10))
        .expireAfterAccess(Duration.ofMinutes(30))
        .recordStats(true)
        .softValues(true)
        .build();

cache.get(uuid, id -> repository.findById(id).thenApply(found -> found.orElse(null)))
        .thenAccept(this::use);

cache.getIfPresent(uuid);          // no load
cache.put(uuid, player);           // store
cache.invalidate(uuid);            // after writing, not before
cache.invalidateAll();
cache.estimatedSize();
cache.asMap();                     // live, read-only view
cache.stats();                     // hit rate; zeros unless recordStats(true)
```

Concurrent requests for the same missing key share one load, so a thousand players joining at once
cannot become a thousand identical queries.

Building twice with the same name returns the same cache, so you can create it wherever is
convenient without duplicating it.

Background refresh — requires a default loader, because there is no caller to supply one:

```java
database.cache("players", UUID.class, PlayerData.class)
        .maximumSize(10_000)
        .refreshAfterWrite(Duration.ofMinutes(5))
        .loader(id -> repository.findById(id).thenApply(found -> found.orElse(null)))
        .build();
```

`expireAfterWrite` is the safest TTL for shared data. Size the cache against your player count, not
against the table.

> **Caching across servers.** A local cache is only safe for data this server owns. If several
> servers write the same row, a stale entry here is a lost update there. Scope entries to players
> currently online on this server, keep the TTL short, or invalidate explicitly when your network
> messaging says the record changed.

### CacheManager

```java
database.caches().cache("players");     // Optional<Cache<K, V>>
database.caches().caches();             // every cache by name
database.caches().invalidateAll();      // /myplugin reload
database.caches().isEnabled();
database.caches().newCache("homes");    // same as database.cache("homes")
```

`database.cache(name)` is the one-argument overload when you already have type witnesses.

## Metrics

```java
MetricsSnapshot metrics = database.metrics().snapshot();

metrics.queryCount();
metrics.queryErrorCount();
metrics.transactionCount();
metrics.rollbackCount();
metrics.errorRate();
metrics.averageQueryMillis();
metrics.p50QueryNanos();
metrics.p95QueryNanos();
metrics.p99QueryNanos();
metrics.maxQueryNanos();
metrics.retryCount();
metrics.timeoutCount();
metrics.pendingOperations();

PoolStatistics pool = metrics.pool();
pool.activeConnections();
pool.idleConnections();
pool.pendingThreads();     // above zero means the pool is your bottleneck
pool.utilization();
```

Latency percentiles come from a fixed-bucket histogram: precise enough to spot a regression, not a
substitute for a real APM. Recording is lock-free and allocation-free, so it is safe on the hot
path. Disable it with `.metricsEnabled(false)` and it costs a dead method call.
`MetricsSnapshot.EMPTY` is what you get when metrics are off.

`pendingThreads` consistently above zero is the signal to raise the pool — [Performance](../performance.md).

### Micrometer

Already running Micrometer? Bridge the counters into your registry with `MicrometerMetricsBridge`
rather than having the library force Micrometer on everyone. The class lives in `enderlib-database-core` and
is only loaded when you construct it, so servers that do not use Micrometer never need the jar.

```java
import com.enderbyte.enderlib.database.core.metrics.MicrometerMetricsBridge;
import io.micrometer.core.instrument.MeterRegistry;

MeterRegistry registry = ...;
DatabaseMetrics builtin = database.metrics();
DatabaseMetrics bridged = new MicrometerMetricsBridge(registry, database.name(), builtin);
```

The constructor publishes into the registry and still answers `snapshot()` from the in-memory
delegate. There is no builder hook that *replaces* `database.metrics()` with the bridge; construct
it yourself and keep the instance next to the database if you want to record through it, or scrape
`database.metrics().snapshot()` on a schedule and push those numbers.

Meters (all tagged with `database=<name>`):

| Meter | Type |
| --- | --- |
| `enderdb.query` | Timer (percentile histogram) |
| `enderdb.transaction` | Timer |
| `enderdb.errors` | Counter |
| `enderdb.retries` | Counter |
| `enderdb.timeouts` | Counter |
| `enderdb.rollbacks` | Counter |
| `enderdb.pool.active` | Gauge |
| `enderdb.pool.pending` | Gauge |
| `enderdb.operations.pending` | Gauge |

## Health

```java
database.pingAsync().thenAccept(up -> {
    if (up) {
        sender.sendMessage("Database reachable");
    } else {
        sender.sendMessage("Database down");
    }
});
```

`pingAsync()` never throws; it completes with `true` or `false`. Combine with a metrics snapshot
for a `/status` command — [Health and metrics](../cookbook/health-and-metrics.md).

## Error handling

All exceptions extend `DatabaseException` and are unchecked, so they propagate through
`CompletableFuture` naturally. Unwrap `getCause()` in `exceptionally` — the future wraps the
failure.

| Exception | Meaning |
| --- | --- |
| `ConnectionException` | The database is unreachable, or the pool is exhausted |
| `QueryException` | A statement failed; parameters are redacted from the message |
| `TransactionException` | Commit, rollback or savepoint handling failed |
| `DatabaseTimeoutException` | An operation exceeded its deadline |
| `OptimisticLockException` | A `@Version` update lost the race; re-read and retry |
| `MigrationException` | A bad checksum, a duplicate version, or a failing script |
| `ConfigurationException` | Invalid configuration, or a missing provider/driver |
| `MappingException` | An entity cannot be mapped to or from a row |

```java
database.queryAsync(...).exceptionally(error -> {
    Throwable cause = error.getCause();
    if (cause instanceof DatabaseTimeoutException) {
        getLogger().warning("Database is slow; skipping this tick");
    } else if (cause instanceof ConnectionException) {
        getLogger().severe("Database is unreachable");
    } else {
        getLogger().log(Level.SEVERE, "Query failed", cause);
    }
    return null;
});
```

Logging goes through **SLF4J**. Credentials are never logged.

## Running several servers against one database

This is the case the library is built for.

- **Migrations are safe.** The history table doubles as a lock table. See [Migrations](migrations.md).
- **Use `@Version`** on anything two servers might write. Without it, the last write silently wins.
- **Be careful with caches.** See the warning above.
- **Set `serverId`** on the migration service so the history table records who applied what.
- **Never share a SQLite file between servers.** SQLite is single-writer; the pool is capped at one
  connection for it and a shared file will corrupt or lock up. Use PostgreSQL or MySQL for networks.
- **Prefer short transactions** and `forUpdateSkipLocked()` for work-claiming patterns.
