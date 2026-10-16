package com.enderbyte.enderlib.database.api.cache;

import java.time.Duration;

/**
 * Fluent builder for a {@link Cache}.
 *
 * <p>When caching is disabled in {@code DatabaseConfig}, or Caffeine is not on the classpath, the
 * builder still works and hands back a pass-through cache that always calls the loader. Plugin code
 * does not need to branch on whether caching is enabled.
 *
 * @param <K> key type
 * @param <V> value type
 */
public interface CacheBuilder<K, V> {

    /**
     * @param maximumSize entry ceiling before eviction kicks in; size it against your player count,
     *                    not against the table
     * @return this builder
     */
    CacheBuilder<K, V> maximumSize(long maximumSize);

    /** Expires an entry a fixed time after it was written. The safest choice for shared data. */
    CacheBuilder<K, V> expireAfterWrite(Duration duration);

    /** Expires an entry a fixed time after it was last read. */
    CacheBuilder<K, V> expireAfterAccess(Duration duration);

    /**
     * Reloads an entry in the background once it reaches this age, serving the stale value
     * meanwhile. Requires {@link #loader(java.util.function.Function)}, because a background
     * refresh has no caller to supply one.
     */
    CacheBuilder<K, V> refreshAfterWrite(Duration duration);

    /**
     * Sets the default loader, used by background refresh and by
     * {@link Cache#get(Object, java.util.function.Function)} calls that pass {@code null}.
     *
     * @param loader asynchronous loader
     * @return this builder
     */
    CacheBuilder<K, V> loader(java.util.function.Function<K, java.util.concurrent.CompletableFuture<V>> loader);

    /** Enables hit/miss statistics. Cheap, and worth having in production. */
    CacheBuilder<K, V> recordStats(boolean recordStats);

    /**
     * Holds values through soft references so the cache yields memory under pressure rather than
     * contributing to an out-of-memory crash.
     */
    CacheBuilder<K, V> softValues(boolean softValues);

    /**
     * @return the cache. Building twice with the same name returns the same instance, so the cache
     *         can be created wherever it is convenient without accidentally duplicating it.
     */
    Cache<K, V> build();
}
