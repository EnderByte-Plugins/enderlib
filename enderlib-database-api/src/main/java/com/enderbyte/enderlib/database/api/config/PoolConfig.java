package com.enderbyte.enderlib.database.api.config;

import java.time.Duration;
import java.util.Objects;

/**
 * Connection pool tuning.
 *
 * <p>The defaults are deliberately conservative. A frequent and expensive mistake is to assume
 * "1,000 players means 1,000 connections": a connection is only held for the microseconds-to-
 * milliseconds a statement actually runs, so a handful of connections per server serves a very
 * large player count. Oversized pools make things <em>slower</em> because the database spends its
 * time context switching, and they exhaust {@code max_connections} as soon as a second Minecraft
 * server joins the network.
 *
 * <p>Sizing guidance (see {@code docs/performance.md}): start from
 * {@code (database CPU cores * 2)} connections <em>in total across the whole network</em>, divide
 * by the number of servers sharing the database, and never let
 * {@code servers * plugins * maximumPoolSize} approach the engine's connection limit.
 *
 * <p>Instances are immutable and safe to share.
 */
public final class PoolConfig {

    /** Sentinel meaning "keep {@code minimumIdle} equal to {@code maximumPoolSize}". */
    public static final int MINIMUM_IDLE_MATCH_MAXIMUM = -1;

    private final int maximumPoolSize;
    private final int minimumIdle;
    private final Duration connectionTimeout;
    private final Duration idleTimeout;
    private final Duration maxLifetime;
    private final Duration keepaliveTime;
    private final Duration validationTimeout;
    private final Duration leakDetectionThreshold;
    private final String poolName;
    private final String connectionTestQuery;
    private final boolean readOnly;
    private final boolean autoCommit;

    private PoolConfig(final Builder builder) {
        this.maximumPoolSize = builder.maximumPoolSize;
        this.minimumIdle = builder.minimumIdle;
        this.connectionTimeout = builder.connectionTimeout;
        this.idleTimeout = builder.idleTimeout;
        this.maxLifetime = builder.maxLifetime;
        this.keepaliveTime = builder.keepaliveTime;
        this.validationTimeout = builder.validationTimeout;
        this.leakDetectionThreshold = builder.leakDetectionThreshold;
        this.poolName = builder.poolName;
        this.connectionTestQuery = builder.connectionTestQuery;
        this.readOnly = builder.readOnly;
        this.autoCommit = builder.autoCommit;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** @return a pool configuration with the library defaults */
    public static PoolConfig defaults() {
        return builder().build();
    }

    public int maximumPoolSize() {
        return maximumPoolSize;
    }

    /** @return the idle floor, resolved against {@link #maximumPoolSize()} */
    public int minimumIdle() {
        return minimumIdle == MINIMUM_IDLE_MATCH_MAXIMUM ? maximumPoolSize : minimumIdle;
    }

    public Duration connectionTimeout() {
        return connectionTimeout;
    }

    public Duration idleTimeout() {
        return idleTimeout;
    }

    public Duration maxLifetime() {
        return maxLifetime;
    }

    public Duration keepaliveTime() {
        return keepaliveTime;
    }

    public Duration validationTimeout() {
        return validationTimeout;
    }

    /** @return leak detection threshold; {@link Duration#ZERO} disables detection */
    public Duration leakDetectionThreshold() {
        return leakDetectionThreshold;
    }

    /** @return explicit pool name, or {@code null} to derive one from the database name */
    public String poolName() {
        return poolName;
    }

    /** @return an explicit validation query, or {@code null} to use JDBC4 {@code isValid()} */
    public String connectionTestQuery() {
        return connectionTestQuery;
    }

    public boolean readOnly() {
        return readOnly;
    }

    public boolean autoCommit() {
        return autoCommit;
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    @Override
    public String toString() {
        return "PoolConfig{maximumPoolSize=" + maximumPoolSize
                + ", minimumIdle=" + minimumIdle()
                + ", connectionTimeout=" + connectionTimeout
                + ", idleTimeout=" + idleTimeout
                + ", maxLifetime=" + maxLifetime
                + ", keepaliveTime=" + keepaliveTime
                + ", leakDetectionThreshold=" + leakDetectionThreshold
                + ", poolName=" + poolName + '}';
    }

    /** Mutable builder for {@link PoolConfig}. Not thread-safe. */
    public static final class Builder {

        private int maximumPoolSize = 10;
        private int minimumIdle = MINIMUM_IDLE_MATCH_MAXIMUM;
        private Duration connectionTimeout = Duration.ofSeconds(10);
        private Duration idleTimeout = Duration.ofMinutes(10);
        private Duration maxLifetime = Duration.ofMinutes(30);
        private Duration keepaliveTime = Duration.ofMinutes(5);
        private Duration validationTimeout = Duration.ofSeconds(5);
        private Duration leakDetectionThreshold = Duration.ZERO;
        private String poolName;
        private String connectionTestQuery;
        private boolean readOnly;
        private boolean autoCommit = true;

        private Builder() {
        }

        private Builder(final PoolConfig source) {
            this.maximumPoolSize = source.maximumPoolSize;
            this.minimumIdle = source.minimumIdle;
            this.connectionTimeout = source.connectionTimeout;
            this.idleTimeout = source.idleTimeout;
            this.maxLifetime = source.maxLifetime;
            this.keepaliveTime = source.keepaliveTime;
            this.validationTimeout = source.validationTimeout;
            this.leakDetectionThreshold = source.leakDetectionThreshold;
            this.poolName = source.poolName;
            this.connectionTestQuery = source.connectionTestQuery;
            this.readOnly = source.readOnly;
            this.autoCommit = source.autoCommit;
        }

        /**
         * Hard ceiling on concurrent connections held by this pool. Default {@code 10}.
         */
        public Builder maximumPoolSize(final int maximumPoolSize) {
            if (maximumPoolSize < 1) {
                throw new IllegalArgumentException("maximumPoolSize must be >= 1");
            }
            this.maximumPoolSize = maximumPoolSize;
            return this;
        }

        /**
         * Minimum idle connections. Defaults to {@link #maximumPoolSize(int)} (a fixed-size pool),
         * which is what HikariCP recommends for server workloads: it removes connection-creation
         * latency spikes during player join storms.
         */
        public Builder minimumIdle(final int minimumIdle) {
            if (minimumIdle < 0 && minimumIdle != MINIMUM_IDLE_MATCH_MAXIMUM) {
                throw new IllegalArgumentException("minimumIdle must be >= 0");
            }
            this.minimumIdle = minimumIdle;
            return this;
        }

        /** How long a caller waits for a connection before failing. Default 10s. */
        public Builder connectionTimeout(final Duration connectionTimeout) {
            this.connectionTimeout = requirePositive(connectionTimeout, "connectionTimeout");
            return this;
        }

        /** How long an idle connection above {@code minimumIdle} survives. Default 10m. */
        public Builder idleTimeout(final Duration idleTimeout) {
            this.idleTimeout = Objects.requireNonNull(idleTimeout, "idleTimeout");
            return this;
        }

        /**
         * Maximum lifetime of a connection. Must stay comfortably below any server-side idle
         * timeout ({@code wait_timeout} on MySQL/MariaDB, {@code idle_session_timeout} on
         * PostgreSQL) and below proxy/firewall idle cut-offs. Default 30m.
         */
        public Builder maxLifetime(final Duration maxLifetime) {
            this.maxLifetime = requirePositive(maxLifetime, "maxLifetime");
            return this;
        }

        /** Keepalive probe interval; must be shorter than {@link #maxLifetime(Duration)}. Default 5m. */
        public Builder keepaliveTime(final Duration keepaliveTime) {
            this.keepaliveTime = Objects.requireNonNull(keepaliveTime, "keepaliveTime");
            return this;
        }

        /** Timeout for connection validation. Default 5s. */
        public Builder validationTimeout(final Duration validationTimeout) {
            this.validationTimeout = requirePositive(validationTimeout, "validationTimeout");
            return this;
        }

        /**
         * Logs a stack trace when a connection is held longer than this. {@link Duration#ZERO}
         * (the default) disables it. Enable it while hunting a leak; it is cheap but noisy for
         * legitimately long batch jobs.
         */
        public Builder leakDetectionThreshold(final Duration leakDetectionThreshold) {
            this.leakDetectionThreshold = Objects.requireNonNull(leakDetectionThreshold, "leakDetectionThreshold");
            return this;
        }

        /**
         * Pool name used in thread names, metrics and log output. Defaults to
         * {@code EnderDB-<database name>}, which makes stack traces immediately attributable to a
         * specific plugin when several plugins use this library on one server.
         */
        public Builder poolName(final String poolName) {
            this.poolName = poolName;
            return this;
        }

        /** Overrides JDBC4 {@code Connection.isValid()} validation. Rarely needed. */
        public Builder connectionTestQuery(final String connectionTestQuery) {
            this.connectionTestQuery = connectionTestQuery;
            return this;
        }

        /** Marks every connection read-only, letting the engine optimise and reject writes. */
        public Builder readOnly(final boolean readOnly) {
            this.readOnly = readOnly;
            return this;
        }

        /**
         * Auto-commit state of pooled connections. Default {@code true}; transactions switch it off
         * for their duration and restore it afterwards.
         */
        public Builder autoCommit(final boolean autoCommit) {
            this.autoCommit = autoCommit;
            return this;
        }

        public PoolConfig build() {
            if (minimumIdle != MINIMUM_IDLE_MATCH_MAXIMUM && minimumIdle > maximumPoolSize) {
                throw new IllegalArgumentException("minimumIdle (" + minimumIdle
                        + ") must not exceed maximumPoolSize (" + maximumPoolSize + ')');
            }
            if (!keepaliveTime.isZero() && keepaliveTime.compareTo(maxLifetime) >= 0) {
                throw new IllegalArgumentException("keepaliveTime (" + keepaliveTime
                        + ") must be shorter than maxLifetime (" + maxLifetime + ')');
            }
            return new PoolConfig(this);
        }

        private static Duration requirePositive(final Duration duration, final String name) {
            Objects.requireNonNull(duration, name);
            if (duration.isNegative() || duration.isZero()) {
                throw new IllegalArgumentException(name + " must be positive");
            }
            return duration;
        }
    }
}
