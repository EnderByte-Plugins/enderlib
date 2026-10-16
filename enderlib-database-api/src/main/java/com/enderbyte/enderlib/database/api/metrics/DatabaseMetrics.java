package com.enderbyte.enderlib.database.api.metrics;

/**
 * Collects operational counters for one database.
 *
 * <p>Recording is on the hot path of every query, so implementations must be lock-free and
 * allocation-free. The default implementation uses striped adders and a fixed-bucket histogram; the
 * no-op implementation costs a few nanoseconds of dead method call when metrics are disabled.
 *
 * <p>Plugins that already run Micrometer can bridge these counters into their registry instead of
 * the library forcing Micrometer on everyone.
 */
public interface DatabaseMetrics {

    /**
     * Records a completed query.
     *
     * @param operation coarse operation name ({@code "query"}, {@code "execute"}, {@code "batch"})
     * @param nanos     wall-clock duration
     * @param success   whether it completed without error
     */
    void recordQuery(String operation, long nanos, boolean success);

    /**
     * Records a failure.
     *
     * @param errorType exception simple name; never include SQL text or parameter values
     */
    void recordError(String errorType);

    /** Records a retried attempt. */
    void recordRetry();

    /** Records an operation that exceeded its deadline. */
    void recordTimeout();

    /**
     * Records a finished transaction.
     *
     * @param committed {@code true} on commit, {@code false} on rollback
     * @param nanos     wall-clock duration
     */
    void recordTransaction(boolean committed, long nanos);

    /** Records an operation entering the executor. */
    void operationStarted();

    /** Records an operation leaving the executor. */
    void operationFinished();

    /** @return a consistent read-out of every counter */
    MetricsSnapshot snapshot();

    /** Resets every counter. Intended for tests and for {@code /plugin reload}. */
    void reset();

    /** @return {@code false} for the no-op implementation */
    boolean isEnabled();
}
