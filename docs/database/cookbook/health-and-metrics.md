# Health and metrics

A `/status` command and a Micrometer bridge.

## Ping

`pingAsync()` never throws. It completes with `true` when the database answered and `false` when
it did not.

```java
public CompletableFuture<String> statusLine() {
    return database.pingAsync().thenApply(up -> {
        MetricsSnapshot snap = database.metrics().snapshot();
        PoolStatistics pool = snap.pool();
        return (up ? "UP" : "DOWN")
                + " queries=" + snap.queryCount()
                + " errors=" + snap.queryErrorCount()
                + " p95ms=" + (snap.p95QueryNanos() / 1_000_000L)
                + " pool.active=" + pool.activeConnections()
                + " pool.pending=" + pool.pendingThreads();
    });
}
```

Hop back to the server thread before sending the message to a `CommandSender`.

## Snapshot fields worth watching

| Field | Watch for |
| --- | --- |
| `pendingThreads()` | Consistently `> 0` → raise the pool or shorten queries |
| `errorRate()` | Climbing → something is failing, not just slow |
| `timeoutCount()` | Deadlines firing |
| `retryCount()` | Spikes after a blip are normal; a plateau is not |
| `p95QueryNanos()` | Slow SQL, not a small pool, if the pool is idle |
| `pendingOperations()` | Executor queue backing up |

Full list: [Operations](../api/operations.md#metrics). Sizing: [Performance](../performance.md).

## Periodic log

```java
database.executor().execute(() -> {
    // this is already a database thread — do not touch Bukkit
});

Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
    MetricsSnapshot snap = database.metrics().snapshot();
    if (snap.pool().pendingThreads() > 0) {
        plugin.getLogger().warning("Database pool queued: " + snap.pool().pendingThreads());
    }
}, 20L * 60, 20L * 60);
```

A scheduled Bukkit async task is fine for *reading* a snapshot. Do not call `.join()` on a query
from one.

## Micrometer

Micrometer is optional. Construct the bridge yourself; there is no builder flag that replaces
`database.metrics()`.

```java
import com.enderbyte.enderlib.database.core.metrics.MicrometerMetricsBridge;
import io.micrometer.core.instrument.MeterRegistry;

MeterRegistry registry = ...; // your existing registry
DatabaseMetrics builtin = database.metrics();
MicrometerMetricsBridge bridged =
        new MicrometerMetricsBridge(registry, database.name(), builtin);
```

Meters, all tagged `database=<name>`:

- Timers: `enderdb.query` (percentile histogram), `enderdb.transaction`
- Counters: `enderdb.errors`, `enderdb.retries`, `enderdb.timeouts`, `enderdb.rollbacks`
- Gauges: `enderdb.pool.active`, `enderdb.pool.pending`, `enderdb.operations.pending`

The bridge still answers `snapshot()` from the in-memory delegate, so `/status` and Micrometer can
coexist.

If you are not running Micrometer, scrape `database.metrics().snapshot()` on a timer and push
those numbers to whatever you already have. Do not add the Micrometer jar "just in case" — the
bridge class is only loaded when you construct it.
