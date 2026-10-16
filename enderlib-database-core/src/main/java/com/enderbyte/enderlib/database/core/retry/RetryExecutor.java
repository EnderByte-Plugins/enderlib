package com.enderbyte.enderlib.database.core.retry;

import com.enderbyte.enderlib.database.api.config.RetryPolicy;
import com.enderbyte.enderlib.database.api.metrics.DatabaseMetrics;
import com.enderbyte.enderlib.database.core.executor.DatabaseExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Runs work on the database executor, retrying transient failures with backoff.
 *
 * <p>Retries are scheduled, never slept: the worker thread is released between attempts so a
 * five-second backoff does not occupy a database thread for five seconds.
 */
public final class RetryExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetryExecutor.class);

    private final DatabaseExecutor executor;
    private final RetryPolicy policy;
    private final FailureClassifier classifier;
    private final DatabaseMetrics metrics;
    private final String databaseName;

    /**
     * @param executor     executor to run attempts on
     * @param policy       retry configuration
     * @param classifier   decides which failures are worth retrying
     * @param metrics      metrics sink for retry counters
     * @param databaseName database name, for log messages
     */
    public RetryExecutor(final DatabaseExecutor executor,
                         final RetryPolicy policy,
                         final FailureClassifier classifier,
                         final DatabaseMetrics metrics,
                         final String databaseName) {
        this.executor = executor;
        this.policy = policy;
        this.classifier = classifier;
        this.metrics = metrics;
        this.databaseName = databaseName;
    }

    /** @return the policy in use */
    public RetryPolicy policy() {
        return policy;
    }

    /**
     * Runs work with retries.
     *
     * @param operation short operation name for logs and metrics
     * @param task      the blocking work
     * @param <T>       result type
     * @return the result, or a failure once attempts are exhausted
     */
    public <T> CompletableFuture<T> executeAsync(final String operation, final Callable<T> task) {
        return attempt(operation, task, 1, policy.maxAttempts());
    }

    /**
     * Runs work with retries, overriding the attempt count. Used by transactions, which only replay
     * when the policy explicitly allows it.
     *
     * @param operation   short operation name
     * @param task        the blocking work
     * @param maxAttempts attempt ceiling for this call
     * @param <T>         result type
     * @return the result, or a failure once attempts are exhausted
     */
    public <T> CompletableFuture<T> executeAsync(final String operation,
                                                 final Callable<T> task,
                                                 final int maxAttempts) {
        return attempt(operation, task, 1, Math.max(1, maxAttempts));
    }

    private <T> CompletableFuture<T> attempt(final String operation,
                                             final Callable<T> task,
                                             final int attempt,
                                             final int maxAttempts) {
        return executor.submit(task).handle((result, error) -> {
            if (error == null) {
                return CompletableFuture.completedFuture(result);
            }
            final Throwable cause = unwrap(error);
            if (attempt >= maxAttempts || !classifier.isTransient(cause)) {
                return CompletableFuture.<T>failedFuture(cause);
            }
            final Duration delay = policy.delayForAttempt(attempt);
            metrics.recordRetry();
            LOGGER.warn("Database '{}' operation '{}' failed with a transient error"
                            + " (attempt {}/{}), retrying in {}ms: {}",
                    databaseName, operation, attempt, maxAttempts, delay.toMillis(),
                    cause.getClass().getSimpleName() + ": " + cause.getMessage());
            return scheduleRetry(operation, task, attempt, maxAttempts, delay);
        }).thenCompose(future -> future);
    }

    private <T> CompletableFuture<T> scheduleRetry(final String operation,
                                                   final Callable<T> task,
                                                   final int attempt,
                                                   final int maxAttempts,
                                                   final Duration delay) {
        if (delay.isZero() || delay.isNegative()) {
            return attempt(operation, task, attempt + 1, maxAttempts);
        }
        // delayedExecutor uses the JDK's shared scheduler thread, so waiting costs no database
        // worker and no extra thread of our own.
        final Executor delayed = CompletableFuture.delayedExecutor(
                delay.toMillis(), TimeUnit.MILLISECONDS, executor);
        final CompletableFuture<T> result = new CompletableFuture<>();
        try {
            delayed.execute(() -> attempt(operation, task, attempt + 1, maxAttempts)
                    .whenComplete((value, error) -> {
                        if (error != null) {
                            result.completeExceptionally(unwrap(error));
                        } else {
                            result.complete(value);
                        }
                    }));
        } catch (final RuntimeException rejected) {
            // Shutdown or a full queue between attempts: fail visibly instead of leaving the
            // caller's future pending forever.
            result.completeExceptionally(rejected);
        }
        return result;
    }

    private static Throwable unwrap(final Throwable error) {
        return error instanceof CompletionException && error.getCause() != null
                ? error.getCause()
                : error;
    }
}
