package com.enderbyte.enderlib.database.api.config;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Configuration of the thread pool that runs blocking driver calls.
 *
 * <p>The library never uses {@link java.util.concurrent.ForkJoinPool#commonPool()}: the common pool
 * is shared with every other library in the JVM, is sized for CPU-bound work, and blocking JDBC
 * calls on it starve unrelated code. Instead each {@code Database} owns a named, bounded pool that
 * is shut down deterministically when the consuming plugin disables.
 *
 * <p>Threads are named {@code EnderDB-<database name>-<facet>-<n>}, for example
 * {@code EnderDB-players-SQL-1}, so a thread dump immediately shows which plugin and which database
 * a stuck thread belongs to.
 */
public final class ExecutorConfig {

    /**
     * What to do when the queue is full.
     *
     * <p>The default is {@link #ABORT}. {@link #CALLER_RUNS} looks attractive but is dangerous in a
     * Minecraft plugin: if the caller happens to be the server thread, the rejection policy would
     * run a blocking database call on the main thread and freeze the server, which is precisely
     * what this library exists to prevent.
     */
    public enum RejectionPolicy {
        /** Fail the returned future with a {@code DatabaseException}. Safe and visible. */
        ABORT,
        /** Run the task on the calling thread. Never use this from the server thread. */
        CALLER_RUNS,
        /** Block the caller until queue space frees up. Also unsafe on the server thread. */
        BLOCK
    }

    private final int coreSize;
    private final int maximumSize;
    private final int queueCapacity;
    private final Duration keepAlive;
    private final String threadNamePrefix;
    private final RejectionPolicy rejectionPolicy;
    private final boolean daemonThreads;
    private final Duration shutdownTimeout;
    private final Executor sharedExecutor;

    private ExecutorConfig(final Builder builder) {
        this.coreSize = builder.coreSize;
        this.maximumSize = builder.maximumSize;
        this.queueCapacity = builder.queueCapacity;
        this.keepAlive = builder.keepAlive;
        this.threadNamePrefix = builder.threadNamePrefix;
        this.rejectionPolicy = builder.rejectionPolicy;
        this.daemonThreads = builder.daemonThreads;
        this.shutdownTimeout = builder.shutdownTimeout;
        this.sharedExecutor = builder.sharedExecutor;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ExecutorConfig defaults() {
        return builder().build();
    }

    public int coreSize() {
        return coreSize;
    }

    public int maximumSize() {
        return maximumSize;
    }

    public int queueCapacity() {
        return queueCapacity;
    }

    public Duration keepAlive() {
        return keepAlive;
    }

    /** @return explicit thread name prefix, or {@code null} to derive it from the database name */
    public String threadNamePrefix() {
        return threadNamePrefix;
    }

    public RejectionPolicy rejectionPolicy() {
        return rejectionPolicy;
    }

    public boolean daemonThreads() {
        return daemonThreads;
    }

    public Duration shutdownTimeout() {
        return shutdownTimeout;
    }

    /**
     * @return an externally owned executor to use instead of creating one, or {@code null}.
     *         When set, the library will <em>not</em> shut it down on {@code close()}.
     */
    public Executor sharedExecutor() {
        return sharedExecutor;
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    @Override
    public String toString() {
        return "ExecutorConfig{coreSize=" + coreSize
                + ", maximumSize=" + maximumSize
                + ", queueCapacity=" + queueCapacity
                + ", rejectionPolicy=" + rejectionPolicy
                + ", shared=" + (sharedExecutor != null) + '}';
    }

    /** Mutable builder for {@link ExecutorConfig}. Not thread-safe. */
    public static final class Builder {

        private static final int CPUS = Runtime.getRuntime().availableProcessors();

        private int coreSize = Math.max(2, Math.min(CPUS, 8));
        private int maximumSize = Math.max(4, Math.min(CPUS * 2, 32));
        private int queueCapacity = 10_000;
        private Duration keepAlive = Duration.ofSeconds(60);
        private String threadNamePrefix;
        private RejectionPolicy rejectionPolicy = RejectionPolicy.ABORT;
        private boolean daemonThreads = true;
        private Duration shutdownTimeout = Duration.ofSeconds(15);
        private Executor sharedExecutor;

        private Builder() {
        }

        private Builder(final ExecutorConfig source) {
            this.coreSize = source.coreSize;
            this.maximumSize = source.maximumSize;
            this.queueCapacity = source.queueCapacity;
            this.keepAlive = source.keepAlive;
            this.threadNamePrefix = source.threadNamePrefix;
            this.rejectionPolicy = source.rejectionPolicy;
            this.daemonThreads = source.daemonThreads;
            this.shutdownTimeout = source.shutdownTimeout;
            this.sharedExecutor = source.sharedExecutor;
        }

        /**
         * Steady-state worker count. There is no benefit in exceeding the connection pool size:
         * extra workers would only queue on the pool instead of on the executor.
         */
        public Builder coreSize(final int coreSize) {
            if (coreSize < 1) {
                throw new IllegalArgumentException("coreSize must be >= 1");
            }
            this.coreSize = coreSize;
            return this;
        }

        /**
         * Ceiling on workers. Additional threads above {@link #coreSize(int)} are only created once
         * the queue is full, matching {@link java.util.concurrent.ThreadPoolExecutor} semantics.
         */
        public Builder maximumSize(final int maximumSize) {
            if (maximumSize < 1) {
                throw new IllegalArgumentException("maximumSize must be >= 1");
            }
            this.maximumSize = maximumSize;
            return this;
        }

        /**
         * Bounded work queue. A bound is mandatory: an unbounded queue turns a database outage into
         * an out-of-memory error instead of a visible, fast failure.
         */
        public Builder queueCapacity(final int queueCapacity) {
            if (queueCapacity < 1) {
                throw new IllegalArgumentException("queueCapacity must be >= 1");
            }
            this.queueCapacity = queueCapacity;
            return this;
        }

        /** Idle timeout for non-core workers. Default 60s. */
        public Builder keepAlive(final Duration keepAlive) {
            this.keepAlive = Objects.requireNonNull(keepAlive, "keepAlive");
            return this;
        }

        /** Overrides the derived thread name prefix. */
        public Builder threadNamePrefix(final String threadNamePrefix) {
            this.threadNamePrefix = threadNamePrefix;
            return this;
        }

        public Builder rejectionPolicy(final RejectionPolicy rejectionPolicy) {
            this.rejectionPolicy = Objects.requireNonNull(rejectionPolicy, "rejectionPolicy");
            return this;
        }

        /**
         * Daemon threads (the default) never keep a stuck JVM alive after the server stops. The
         * library still shuts the pool down explicitly on {@code close()}.
         */
        public Builder daemonThreads(final boolean daemonThreads) {
            this.daemonThreads = daemonThreads;
            return this;
        }

        /** How long {@code close()} waits for in-flight work before interrupting it. Default 15s. */
        public Builder shutdownTimeout(final Duration shutdownTimeout) {
            this.shutdownTimeout = Objects.requireNonNull(shutdownTimeout, "shutdownTimeout");
            return this;
        }

        /**
         * Supplies an executor owned by the caller, for example one shared between several
         * databases. The library will not shut it down; that stays the owner's responsibility.
         */
        public Builder sharedExecutor(final Executor sharedExecutor) {
            this.sharedExecutor = sharedExecutor;
            return this;
        }

        public ExecutorConfig build() {
            if (maximumSize < coreSize) {
                throw new IllegalArgumentException("maximumSize (" + maximumSize
                        + ") must be >= coreSize (" + coreSize + ')');
            }
            return new ExecutorConfig(this);
        }
    }
}
