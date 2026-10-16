# Configuration

Sensible defaults mean the [quick start](../getting-started.md) is a complete configuration.
Everything below is optional.

## Connection

```java
Database.builder()
        .type(DatabaseType.MARIADB)
        .name("players")                  // used in pool names, thread names and metrics
        .host("db.example.com")
        .port(3306)
        .database("minecraft")
        .schema("public")
        .username("minecraft")
        .password(password)
        .property("sslmode", "require")   // passed to the driver verbatim
        .build();
```

Or with a full JDBC URL:

```java
Database.builder()
        .type(DatabaseType.POSTGRESQL)
        .connectionString("jdbc:postgresql://localhost:5432/minecraft?ApplicationName=MyPlugin")
        .username("minecraft")
        .password(password)
        .build();
```

Other builder methods: `driverClassName`, `properties(Map)`, `validateOnConnect(boolean)`,
`metricsEnabled(boolean)`, `cachingEnabled(boolean)`, `config(existingDatabaseConfig)`.

`build()` does no I/O. `buildConfig()` returns the `DatabaseConfig` without creating a database.
`connect()` on the builder is `build().connect()`.

## Pool

```java
.pool(PoolConfig.builder()
        .maximumPoolSize(20)
        .minimumIdle(5)
        .connectionTimeout(Duration.ofSeconds(5))
        .idleTimeout(Duration.ofMinutes(10))
        .maxLifetime(Duration.ofMinutes(30))
        .leakDetectionThreshold(Duration.ofSeconds(30))
        .build())
```

Default maximum is **10**, which is right for most single servers. Bigger is not better — see
[Performance](../performance.md). `.poolSize(20)` is shorthand for the maximum size alone.

Builder-only pool fields (not loaded from YAML): `validationTimeout`, `connectionTestQuery`,
`readOnly`, `autoCommit`, `keepaliveTime` is loadable as `pool.keepalive-ms`.

## Executor

```java
.executor(ExecutorConfig.builder()
        .coreSize(4)
        .maximumSize(16)
        .queueCapacity(10_000)
        .shutdownTimeout(Duration.ofSeconds(10))
        .build())
```

Defaults scale with the CPU count. Threads are named `EnderDB-<database>-SQL-<n>`, so a thread dump
tells you immediately which plugin and which database a stuck thread belongs to.

The queue is bounded on purpose: during an outage, a bounded queue produces fast, visible failures
instead of an ever-growing backlog that ends in an out-of-memory crash. The default rejection
policy is `ABORT` for the same reason — `CALLER_RUNS` would run a blocking query on whatever thread
submitted it, and if that is the server thread, the server freezes.

Builder-only executor fields: `keepAlive`, `threadNamePrefix`, `rejectionPolicy`, `daemonThreads`,
`shutdownTimeout`, `sharedExecutor`. YAML can set core size, maximum size and queue capacity.

## Timeouts

```java
.timeouts(TimeoutConfig.builder()
        .query(Duration.ofSeconds(10))
        .batch(Duration.ofSeconds(60))
        .transaction(Duration.ofSeconds(30))
        .connect(Duration.ofSeconds(30))
        .build())
```

`Duration.ZERO` disables one. The query timeout is applied both as a JDBC statement timeout and as
a future deadline, so a hung query is bounded from both sides.

`timeout.batch` is **builder-only** — `ConfigLoader` does not read it. YAML can set
`timeout.query-ms`, `timeout.transaction-ms` and `timeout.connect-ms`.

## Retries

```java
.retryPolicy(RetryPolicy.exponentialBackoff()
        .maxAttempts(5)
        .initialDelay(Duration.ofMillis(100))
        .maxDelay(Duration.ofSeconds(5))
        .retryTransactions(true))
```

`RetryPolicy.fixedDelay(Duration)` and `RetryPolicy.none()` are also available.

Only genuinely transient failures are retried: connection loss, failover, deadlocks, serialization
conflicts and pool checkout timeouts. Constraint violations, syntax errors and permission problems
are permanent and surface immediately — retrying every `SQLException` turns a bug into an outage.

Retries are *scheduled*, never slept, so a five-second backoff does not occupy a database thread
for five seconds.

Jitter is on by default and matters at scale: without it, fifty servers reconnecting after the same
database restart retry in lockstep and hammer the engine the moment it returns.

Transactions are **not** retried unless you opt in with `retryTransactions(true)`, because
replaying one re-runs your callback.

YAML can set `retry.max-attempts`, `retry.initial-delay-ms` and `retry.max-delay-ms`. Multiplier,
jitter and `retryTransactions` are builder-only.

## From your `config.yml`

The library never reads a file itself. Feed it your plugin's map:

```java
DatabaseConfig config = ConfigLoader.fromMap(
        getConfig().getConfigurationSection("database").getValues(true));

Database database = Database.builder().config(config).build().connect();
```

`ConfigLoader.fromProperties(Properties)` does the same from a `.properties` file. Values may be
strings or already-typed numbers/booleans, which is what YAML parsers usually hand back. `type` is
required.

```yaml
database:
  type: postgresql
  name: players
  host: localhost
  port: 5432
  database: minecraft
  schema: public
  username: minecraft
  password: secret
  # connection-string: jdbc:postgresql://localhost:5432/minecraft
  # driver-class-name: org.postgresql.Driver
  properties.sslmode: require
  pool.maximum-size: 20
  pool.minimum-idle: 5
  pool.connection-timeout-ms: 5000
  pool.idle-timeout-ms: 600000
  pool.max-lifetime-ms: 1800000
  pool.keepalive-ms: 0
  pool.leak-detection-ms: 30000
  pool.name: ender-players
  executor.core-size: 4
  executor.maximum-size: 16
  executor.queue-capacity: 10000
  timeout.query-ms: 10000
  timeout.transaction-ms: 30000
  timeout.connect-ms: 30000
  retry.max-attempts: 5
  retry.initial-delay-ms: 100
  retry.max-delay-ms: 5000
  metrics.enabled: true
  cache.enabled: true
```

Boolean keys accept `true` / `false`, `yes` / `no`, `on` / `off`, `1` / `0`.

### Keys ConfigLoader does not read

Set these on the builders instead:

| Area | Builder-only |
| --- | --- |
| Connection | `validateOnConnect` |
| Pool | `validationTimeout`, `connectionTestQuery`, `readOnly`, `autoCommit` |
| Executor | `keepAlive`, `threadNamePrefix`, `rejectionPolicy`, `daemonThreads`, `shutdownTimeout`, `sharedExecutor` |
| Timeouts | `timeout.batch` |
| Retry | `multiplier`, `jitterFactor`, `retryTransactions`, `fixedDelay()`, `none()` |

## Several databases

There is no global singleton. Create one instance per logical database and keep each for the
plugin's lifetime. `DatabaseRegistry` is documented in [Database](database.md#several-databases).
