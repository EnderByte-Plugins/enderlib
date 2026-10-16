package com.enderbyte.enderlib.database.api.metrics;

/**
 * An immutable read-out of a database's counters, safe to render in a command or push to a
 * monitoring system.
 *
 * <p>Latency percentiles come from a fixed-bucket histogram, so they are approximations — precise
 * enough to spot a regression, not a substitute for a real APM.
 *
 * @param queryCount        completed queries
 * @param queryErrorCount   queries that failed
 * @param transactionCount  committed transactions
 * @param rollbackCount     rolled back transactions
 * @param retryCount        retried operations
 * @param timeoutCount      operations that hit their deadline
 * @param pendingOperations operations submitted but not finished
 * @param totalQueryNanos   summed query latency
 * @param p50QueryNanos     approximate median query latency
 * @param p95QueryNanos     approximate 95th percentile query latency
 * @param p99QueryNanos     approximate 99th percentile query latency
 * @param maxQueryNanos     slowest query observed
 * @param pool              pool state at snapshot time
 */
public record MetricsSnapshot(long queryCount,
                              long queryErrorCount,
                              long transactionCount,
                              long rollbackCount,
                              long retryCount,
                              long timeoutCount,
                              long pendingOperations,
                              long totalQueryNanos,
                              long p50QueryNanos,
                              long p95QueryNanos,
                              long p99QueryNanos,
                              long maxQueryNanos,
                              PoolStatistics pool) {

    /** An all-zero snapshot, returned when metrics are disabled. */
    public static final MetricsSnapshot EMPTY = new MetricsSnapshot(
            0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, PoolStatistics.UNAVAILABLE);

    /** @return mean query latency in milliseconds */
    public double averageQueryMillis() {
        return queryCount == 0L ? 0.0d : totalQueryNanos / (double) queryCount / 1_000_000.0d;
    }

    /** @return fraction of queries that failed, 0.0 to 1.0 */
    public double errorRate() {
        final long total = queryCount + queryErrorCount;
        return total == 0L ? 0.0d : (double) queryErrorCount / total;
    }
}
