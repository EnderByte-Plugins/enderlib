package com.enderbyte.enderlib.database.core.metrics;

import com.enderbyte.enderlib.database.api.metrics.DatabaseMetrics;
import com.enderbyte.enderlib.database.api.metrics.MetricsSnapshot;

/**
 * Metrics implementation that records nothing.
 *
 * <p>Used when metrics are disabled. The methods are empty and trivially inlined by the JIT, so a
 * plugin that does not want metrics pays essentially nothing for the calls left in the code paths.
 */
public final class NoopDatabaseMetrics implements DatabaseMetrics {

    /** Shared stateless instance. */
    public static final NoopDatabaseMetrics INSTANCE = new NoopDatabaseMetrics();

    private NoopDatabaseMetrics() {
    }

    @Override
    public void recordQuery(final String operation, final long nanos, final boolean success) {
        // intentionally empty
    }

    @Override
    public void recordError(final String errorType) {
        // intentionally empty
    }

    @Override
    public void recordRetry() {
        // intentionally empty
    }

    @Override
    public void recordTimeout() {
        // intentionally empty
    }

    @Override
    public void recordTransaction(final boolean committed, final long nanos) {
        // intentionally empty
    }

    @Override
    public void operationStarted() {
        // intentionally empty
    }

    @Override
    public void operationFinished() {
        // intentionally empty
    }

    @Override
    public MetricsSnapshot snapshot() {
        return MetricsSnapshot.EMPTY;
    }

    @Override
    public void reset() {
        // intentionally empty
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}
