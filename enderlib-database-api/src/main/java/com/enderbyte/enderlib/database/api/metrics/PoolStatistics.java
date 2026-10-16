package com.enderbyte.enderlib.database.api.metrics;

/**
 * A snapshot of connection pool state.
 *
 * <p>{@code pendingThreads} is the number worth alerting on: threads waiting for a connection means
 * the pool is the bottleneck, either because it is too small or because something holds connections
 * too long.
 *
 * @param totalConnections  connections currently managed
 * @param activeConnections connections currently checked out
 * @param idleConnections   connections available for checkout
 * @param pendingThreads    threads waiting for a connection
 * @param maximumPoolSize   configured ceiling
 */
public record PoolStatistics(int totalConnections,
                             int activeConnections,
                             int idleConnections,
                             int pendingThreads,
                             int maximumPoolSize) {

    /** Returned before the pool has been opened, or when a provider exposes no pool. */
    public static final PoolStatistics UNAVAILABLE = new PoolStatistics(0, 0, 0, 0, 0);

    /** @return fraction of the pool currently checked out, 0.0 to 1.0 */
    public double utilization() {
        return maximumPoolSize == 0 ? 0.0d : (double) activeConnections / maximumPoolSize;
    }
}
