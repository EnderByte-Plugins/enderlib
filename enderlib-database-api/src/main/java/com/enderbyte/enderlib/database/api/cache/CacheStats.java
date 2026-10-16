package com.enderbyte.enderlib.database.api.cache;

/**
 * A snapshot of cache counters.
 *
 * @param hitCount        lookups served from the cache
 * @param missCount       lookups that had to load
 * @param loadSuccessCount successful loads
 * @param loadFailureCount loads that failed
 * @param evictionCount   entries dropped by size or expiry
 */
public record CacheStats(long hitCount,
                         long missCount,
                         long loadSuccessCount,
                         long loadFailureCount,
                         long evictionCount) {

    /** An all-zero snapshot, returned when statistics are not recorded. */
    public static final CacheStats EMPTY = new CacheStats(0L, 0L, 0L, 0L, 0L);

    /** @return total lookups */
    public long requestCount() {
        return hitCount + missCount;
    }

    /** @return hit ratio between 0.0 and 1.0; {@code 1.0} when there were no lookups */
    public double hitRate() {
        final long requests = requestCount();
        return requests == 0L ? 1.0d : (double) hitCount / requests;
    }

    /** @return miss ratio between 0.0 and 1.0 */
    public double missRate() {
        return 1.0d - hitRate();
    }
}
