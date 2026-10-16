package com.enderbyte.enderlib.database.core;

import com.enderbyte.enderlib.database.api.Database;
import com.enderbyte.enderlib.database.api.cache.CacheBuilder;
import com.enderbyte.enderlib.database.api.cache.CacheManager;
import com.enderbyte.enderlib.database.api.config.DatabaseConfig;
import com.enderbyte.enderlib.database.api.config.DatabaseType;
import com.enderbyte.enderlib.database.api.exception.ConnectionException;
import com.enderbyte.enderlib.database.api.exception.DatabaseException;
import com.enderbyte.enderlib.database.api.exception.DatabaseTimeoutException;
import com.enderbyte.enderlib.database.api.mapping.ConverterRegistry;
import com.enderbyte.enderlib.database.api.metrics.DatabaseMetrics;
import com.enderbyte.enderlib.database.api.metrics.PoolStatistics;
import com.enderbyte.enderlib.database.core.cache.DefaultCacheManager;
import com.enderbyte.enderlib.database.core.executor.DatabaseExecutor;
import com.enderbyte.enderlib.database.core.metrics.NoopDatabaseMetrics;
import com.enderbyte.enderlib.database.core.metrics.StandardDatabaseMetrics;
import com.enderbyte.enderlib.database.core.retry.FailureClassifier;
import com.enderbyte.enderlib.database.core.retry.RetryExecutor;
import com.enderbyte.enderlib.database.core.retry.SqlFailureClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lifecycle, executor, metrics and cache plumbing shared by every provider.
 *
 * <p>Subclasses implement only what is engine-specific: opening and closing the underlying client,
 * proving connectivity, and reporting pool state.
 *
 * <p>State transitions are guarded by an atomic reference, so concurrent {@code connect()} and
 * {@code close()} calls from different plugin threads cannot interleave into a half-open database.
 */
public abstract class AbstractDatabase implements Database {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractDatabase.class);

    /** Lifecycle states. */
    protected enum State {
        /** Built, not yet connected. */
        NEW,
        /** {@code connect()} is in progress. */
        CONNECTING,
        /** Usable. */
        CONNECTED,
        /** Shut down; every operation now fails fast. */
        CLOSED
    }

    private final DatabaseConfig config;
    private final DatabaseExecutor executor;
    private final DatabaseMetrics metrics;
    private final DefaultCacheManager cacheManager;
    private final ConverterRegistry converters;
    private final RetryExecutor retries;
    private final AtomicReference<State> state = new AtomicReference<>(State.NEW);

    /**
     * @param config     validated configuration
     * @param facet      subsystem name used in thread names, such as {@code SQL}
     * @param converters converter registry for this engine
     */
    protected AbstractDatabase(final DatabaseConfig config,
                               final String facet,
                               final ConverterRegistry converters) {
        this(config, facet, converters, SqlFailureClassifier.INSTANCE);
    }

    /**
     * @param config     validated configuration
     * @param facet      subsystem name used in thread names
     * @param converters converter registry for this engine
     * @param classifier decides which failures are retried
     */
    protected AbstractDatabase(final DatabaseConfig config,
                               final String facet,
                               final ConverterRegistry converters,
                               final FailureClassifier classifier) {
        this.config = config;
        this.converters = converters;
        this.executor = DatabaseExecutor.create(config.name(), facet, config.executor());
        this.metrics = config.metricsEnabled()
                ? new StandardDatabaseMetrics(this::poolStatistics)
                : NoopDatabaseMetrics.INSTANCE;
        this.cacheManager = new DefaultCacheManager(config.name(), config.cachingEnabled(), executor);
        this.retries = new RetryExecutor(executor, config.retryPolicy(), classifier, metrics, config.name());
    }

    @Override
    public final String name() {
        return config.name();
    }

    @Override
    public final DatabaseType type() {
        return config.type();
    }

    @Override
    public final DatabaseConfig config() {
        return config;
    }

    @Override
    public final Database connect() {
        if (state.get() == State.CONNECTED) {
            return this;
        }
        if (!state.compareAndSet(State.NEW, State.CONNECTING)) {
            if (state.get() == State.CLOSED) {
                throw new ConnectionException("Database '" + name() + "' has already been closed");
            }
            // Another thread is connecting: wait for it rather than opening a second pool.
            awaitConnected();
            return this;
        }
        try {
            doConnect();
            if (config.validateOnConnect()) {
                validateConnectivity();
            }
            state.set(State.CONNECTED);
            LOGGER.info("Connected database '{}' ({}), pool size {}",
                    name(), type().displayName(), config.pool().maximumPoolSize());
            return this;
        } catch (final RuntimeException e) {
            state.set(State.NEW);
            safeShutdownAfterFailedConnect();
            throw e;
        }
    }

    @Override
    public final CompletableFuture<Database> connectAsync() {
        return executor.submit(this::connect);
    }

    @Override
    public final boolean isConnected() {
        return state.get() == State.CONNECTED;
    }

    @Override
    public final CompletableFuture<Boolean> pingAsync() {
        if (state.get() != State.CONNECTED) {
            return CompletableFuture.completedFuture(false);
        }
        return executor.submit(() -> {
            try {
                doPing();
                return true;
            } catch (final Exception e) {
                LOGGER.debug("Ping failed for database '{}'", name(), e);
                return false;
            }
        });
    }

    @Override
    public final Executor executor() {
        return executor;
    }

    @Override
    public final DatabaseMetrics metrics() {
        return metrics;
    }

    @Override
    public final CacheManager caches() {
        return cacheManager;
    }

    @Override
    public final <K, V> CacheBuilder<K, V> cache(final String name) {
        return cacheManager.newCache(name);
    }

    @Override
    public final <K, V> CacheBuilder<K, V> cache(final String name,
                                                 final Class<K> keyType,
                                                 final Class<V> valueType) {
        return cacheManager.newCache(name);
    }

    @Override
    public final ConverterRegistry converters() {
        return converters;
    }

    @Override
    public final void close() {
        final State previous = state.getAndSet(State.CLOSED);
        if (previous == State.CLOSED) {
            return;
        }
        LOGGER.info("Closing database '{}'", name());
        // Order matters: stop accepting work, let in-flight work finish, then release the client.
        try {
            executor.close();
        } finally {
            try {
                cacheManager.clear();
            } finally {
                doClose();
            }
        }
        LOGGER.info("Closed database '{}'", name());
    }

    @Override
    public final CompletableFuture<Void> closeAsync() {
        // close() blocks while draining, so run it on a throwaway thread rather than on the pool
        // that is about to be shut down.
        final CompletableFuture<Void> future = new CompletableFuture<>();
        final Thread closer = new Thread(() -> {
            try {
                close();
                future.complete(null);
            } catch (final Throwable error) {
                future.completeExceptionally(error);
            }
        }, "EnderDB-" + name() + "-shutdown");
        closer.setDaemon(true);
        closer.start();
        return future;
    }

    /** @return the executor, for subclasses submitting engine work */
    protected final DatabaseExecutor databaseExecutor() {
        return executor;
    }

    /** @return the retry executor, for subclasses running retryable operations */
    protected final RetryExecutor retries() {
        return retries;
    }

    /** @return the cache manager */
    protected final DefaultCacheManager cacheManager() {
        return cacheManager;
    }

    /** Fails fast when the database is not usable, with a message that says which state it is in. */
    protected final void requireConnected() {
        final State current = state.get();
        if (current != State.CONNECTED) {
            throw new ConnectionException("Database '" + name() + "' is not connected (state: "
                    + current + "). Call connect() during plugin startup.");
        }
    }

    /**
     * Applies an operation timeout to a future.
     *
     * <p>The timeout fails the caller's future; it does not abort work already running on the
     * database. Statement-level timeouts handle that side, so a hung query is bounded twice.
     *
     * @param future    the operation
     * @param timeout   the deadline, {@link Duration#ZERO} to disable
     * @param operation operation name for the error message
     * @param <T>       result type
     * @return the future, with the timeout applied
     */
    protected final <T> CompletableFuture<T> withTimeout(final CompletableFuture<T> future,
                                                         final Duration timeout,
                                                         final String operation) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return future;
        }
        return future.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(error -> {
                    final Throwable cause = error instanceof CompletionException && error.getCause() != null
                            ? error.getCause()
                            : error;
                    if (cause instanceof java.util.concurrent.TimeoutException) {
                        metrics.recordTimeout();
                        throw new CompletionException(new DatabaseTimeoutException(
                                "Database '" + name() + "' operation '" + operation
                                        + "' exceeded " + timeout.toMillis() + "ms", cause));
                    }
                    throw cause instanceof RuntimeException runtime
                            ? new CompletionException(runtime)
                            : new CompletionException(cause);
                });
    }

    private void validateConnectivity() {
        try {
            doPing();
        } catch (final Exception e) {
            throw new ConnectionException("Database '" + name() + "' (" + type().displayName()
                    + ") did not answer during startup validation", e);
        }
    }

    private void awaitConnected() {
        final long deadline = System.nanoTime() + config.timeouts().connect().toNanos();
        while (state.get() == State.CONNECTING) {
            if (System.nanoTime() > deadline) {
                throw new ConnectionException("Timed out waiting for database '" + name()
                        + "' to finish connecting");
            }
            Thread.onSpinWait();
            try {
                Thread.sleep(5L);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ConnectionException("Interrupted while waiting for database '" + name() + '\'');
            }
        }
        if (state.get() != State.CONNECTED) {
            throw new ConnectionException("Database '" + name() + "' failed to connect");
        }
    }

    private void safeShutdownAfterFailedConnect() {
        try {
            doClose();
        } catch (final RuntimeException suppressed) {
            LOGGER.debug("Cleanup after a failed connect also failed for '{}'", name(), suppressed);
        }
    }

    /**
     * Opens the engine client and its pool.
     *
     * @throws DatabaseException if the client cannot be created
     */
    protected abstract void doConnect();

    /** Releases the engine client. Must be idempotent and must not throw for an unopened client. */
    protected abstract void doClose();

    /**
     * Performs a cheap round trip proving the database answers.
     *
     * @throws Exception if the database is unreachable
     */
    protected abstract void doPing() throws Exception;

    /** @return live pool state, or {@link PoolStatistics#UNAVAILABLE} when there is no pool */
    protected abstract PoolStatistics poolStatistics();
}
