# Performance

Pool sizing is the setting that most often gets guessed, and guessed badly.

A connection is only held for the microseconds-to-milliseconds a statement actually runs. A handful
of connections per server therefore serves a very large player count. Oversized pools make things
*slower* because the database spends its time context switching, and they exhaust `max_connections`
as soon as a second Minecraft server joins the network.

## Start here

1. Default maximum is **10** per `Database` instance. That is right for most single servers.
2. Watch `metrics().snapshot().pool().pendingThreads()`. Raise the pool only if that number is
   consistently above zero.
3. Treat the database's `max_connections` as a budget for the *whole network*, not for one plugin.

A usable starting formula:

```
total connections across the network  ≈  database CPU cores × 2
per-server pool size                  ≈  that total ÷ number of servers
```

Never let `servers × plugins × maximumPoolSize` approach the engine's connection limit. Two plugins
on eight servers at pool size 20 is already 320 connections before the query tools, replicas and
human operators take any.

`.poolSize(20)` is shorthand for the maximum size alone. Prefer the full `PoolConfig` when you also
need idle floor, timeouts or leak detection — see [Configuration](api/configuration.md).

## What the metrics mean

| Signal | Meaning |
| --- | --- |
| `pendingThreads` consistently `> 0` | The pool is the bottleneck. Raise it a little, or shorten queries. |
| `utilization` near 1.0 with `pendingThreads` at 0 | Fine. The pool is busy but not queued. |
| `p95QueryNanos` climbing while the pool is idle | The engine or the SQL is slow, not the pool. |
| `timeoutCount` rising | Deadlines are firing. Check the database, then the query, then the timeout. |
| `retryCount` spikes after a blip, then returns | Working as designed. |

Recording is lock-free. Leave metrics on.

## Timeouts and queues

Every operation has a deadline (`TimeoutConfig`). A hung query is bounded both as a JDBC statement
timeout and as a future deadline.

The executor queue is bounded on purpose. During an outage a bounded queue produces fast, visible
failures instead of an ever-growing backlog that ends in an out-of-memory crash. The default
rejection policy is `ABORT` for the same reason — `CALLER_RUNS` would run a blocking query on
whatever thread submitted it, and if that is the server thread, the server freezes.

## Batches versus one-row statements

A thousand `executeAsync` calls are a thousand round trips. `batchInsertAsync` / `batchAsync` run
inside one transaction with a longer default deadline (60s versus 10s). Use them for flushing a
stats buffer or saving every online player on shutdown.

Multi-row `InsertQuery.columns(...).row(...)` is faster than one insert per row, but for very large
volumes prefer `batchInsertAsync`, which streams through a JDBC batch instead of building one
enormous statement.

## Caching

A local cache removes load from the database only for data this server owns. See
[Operations](api/operations.md#caching). Size it against your player count, not against the table.

## Multi-server

Short transactions, `@Version` on anything two servers might write, and `FOR UPDATE SKIP LOCKED`
for work queues. Details: [Transactions](api/transactions.md) and
[Work queue](cookbook/work-queue.md).
