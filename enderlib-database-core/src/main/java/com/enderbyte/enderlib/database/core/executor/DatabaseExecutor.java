package com.enderbyte.enderlib.database.core.executor;

import com.enderbyte.enderlib.database.api.config.ExecutorConfig;
import com.enderbyte.enderlib.database.api.exception.DatabaseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The thread pool that runs blocking driver calls for one database.
 *
 * <p>Three properties matter here, and all three are deliberate:
 *
 * <ul>
 *   <li><strong>Dedicated.</strong> Never {@code ForkJoinPool.commonPool()}: blocking JDBC work on
 *       the common pool starves every other library in the JVM.</li>
 *   <li><strong>Bounded.</strong> A bounded queue turns a database outage into fast, visible
 *       failures instead of an ever-growing backlog that ends in an out-of-memory crash.</li>
 *   <li><strong>Non-throwing.</strong> Submission failures come back as a failed future, never as an
 *       exception on the caller's thread, so a rejected task can never surprise the server thread.</li>
 * </ul>
 *
 * <p>An externally supplied executor can be used instead, in which case this class will not shut it
 * down: ownership stays with whoever created it.
 */
public final class DatabaseExecutor implements Executor, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseExecutor.class);

    private final Executor delegate;
    private final ThreadPoolExecutor pool;
    private final ExecutorConfig config;
    private final String name;
    private volatile boolean shutdown;

    private DatabaseExecutor(final Executor delegate,
                             final ThreadPoolExecutor pool,
                             final ExecutorConfig config,
                             final String name) {
        this.delegate = delegate;
        this.pool = pool;
        this.config = config;
        this.name = name;
    }

    /**
     * Creates an executor for a database.
     *
     * @param databaseName logical database name, used in thread names
     * @param facet        subsystem name such as {@code SQL}
     * @param config       executor configuration
     * @return the executor
     */
    public static DatabaseExecutor create(final String databaseName,
                                          final String facet,
                                          final ExecutorConfig config) {
        Objects.requireNonNull(databaseName, "databaseName");
        Objects.requireNonNull(config, "config");

        final String threadPrefix = config.threadNamePrefix() != null
                ? config.threadNamePrefix()
                : "EnderDB-" + databaseName + '-' + facet;

        if (config.sharedExecutor() != null) {
            return new DatabaseExecutor(config.sharedExecutor(), null, config, threadPrefix);
        }

        final BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(config.queueCapacity());
        final ThreadPoolExecutor pool = new ThreadPoolExecutor(
                config.coreSize(),
                config.maximumSize(),
                config.keepAlive().toMillis(),
                TimeUnit.MILLISECONDS,
                queue,
                new NamedThreadFactory(threadPrefix, config.daemonThreads()),
                rejectionHandler(config));
        return new DatabaseExecutor(pool, pool, config, threadPrefix);
    }

    private static ThreadPoolExecutor.AbortPolicy abortPolicy() {
        return new ThreadPoolExecutor.AbortPolicy();
    }

    private static java.util.concurrent.RejectedExecutionHandler rejectionHandler(final ExecutorConfig config) {
        return switch (config.rejectionPolicy()) {
            case ABORT -> abortPolicy();
            case CALLER_RUNS -> new ThreadPoolExecutor.CallerRunsPolicy();
            case BLOCK -> (task, executor) -> {
                if (executor.isShutdown()) {
                    throw new RejectedExecutionException("Database executor has been shut down");
                }
                try {
                    executor.getQueue().put(task);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RejectedExecutionException("Interrupted while queueing database work", e);
                }
            };
        };
    }

    /** @return the thread name prefix used by this executor */
    public String name() {
        return name;
    }

    /**
     * Submits work.
     *
     * @param task blocking work to run on a database thread
     * @param <T>  result type
     * @return a future completed with the result, or failed with the task's exception
     */
    public <T> CompletableFuture<T> submit(final Callable<T> task) {
        Objects.requireNonNull(task, "task");
        final CompletableFuture<T> future = new CompletableFuture<>();
        try {
            delegate.execute(() -> {
                // The caller may already have cancelled or timed out; skip the round trip.
                if (future.isDone()) {
                    return;
                }
                try {
                    future.complete(task.call());
                } catch (final Throwable error) {
                    future.completeExceptionally(error);
                }
            });
        } catch (final RejectedExecutionException rejected) {
            future.completeExceptionally(new DatabaseException(
                    "Database executor '" + name + "' rejected work: the queue is full ("
                            + config.queueCapacity() + " entries). The database is likely unreachable "
                            + "or too slow to keep up.", rejected));
        }
        return future;
    }

    /**
     * Submits work that produces no value.
     *
     * @param task blocking work to run on a database thread
     * @return a future completed when the task finishes
     */
    public CompletableFuture<Void> submitVoid(final ThrowingRunnable task) {
        return submit(() -> {
            task.run();
            return null;
        });
    }

    @Override
    public void execute(final Runnable command) {
        delegate.execute(command);
    }

    /** @return tasks waiting in the queue, or {@code -1} for an externally supplied executor */
    public int queueSize() {
        return pool == null ? -1 : pool.getQueue().size();
    }

    /** @return tasks currently running, or {@code -1} for an externally supplied executor */
    public int activeCount() {
        return pool == null ? -1 : pool.getActiveCount();
    }

    /** @return current worker count, or {@code -1} for an externally supplied executor */
    public int poolSize() {
        return pool == null ? -1 : pool.getPoolSize();
    }

    /** @return {@code true} once {@link #close()} has run */
    public boolean isShutdown() {
        return shutdown;
    }

    /**
     * Stops accepting work and waits for in-flight tasks, up to the configured shutdown timeout.
     *
     * <p>Anything still running after that is interrupted: a plugin disable must not hang the
     * server because one query is stuck behind a dead database. Externally supplied executors are
     * left alone.
     */
    @Override
    public void close() {
        if (shutdown) {
            return;
        }
        shutdown = true;
        if (pool == null) {
            return;
        }
        pool.shutdown();
        try {
            if (!pool.awaitTermination(config.shutdownTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                final List<Runnable> abandoned = pool.shutdownNow();
                LOGGER.warn("Database executor '{}' did not finish within {}; {} task(s) were abandoned",
                        name, config.shutdownTimeout(), abandoned.size());
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            pool.shutdownNow();
            LOGGER.warn("Interrupted while shutting down database executor '{}'", name);
        }
    }

    /** A {@link Runnable} that may throw, so blocking driver calls need no try/catch boilerplate. */
    @FunctionalInterface
    public interface ThrowingRunnable {

        /**
         * @throws Exception any failure, which fails the returned future
         */
        void run() throws Exception;
    }
}
