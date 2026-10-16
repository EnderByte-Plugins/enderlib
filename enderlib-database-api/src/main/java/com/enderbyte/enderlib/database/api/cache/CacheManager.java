package com.enderbyte.enderlib.database.api.cache;

import java.util.Map;
import java.util.Optional;

/**
 * Owns the caches belonging to one database, so they can be listed, inspected and invalidated
 * together — for example from a {@code /myplugin reload} command.
 *
 * <p>Implementations must be thread-safe.
 */
public interface CacheManager {

    /**
     * Starts building a named cache, or returns a builder that yields the existing cache when one
     * with that name was already created.
     *
     * @param name unique cache name within this database
     * @param <K>  key type
     * @param <V>  value type
     * @return the builder
     */
    <K, V> CacheBuilder<K, V> newCache(String name);

    /**
     * @param name cache name
     * @param <K>  key type
     * @param <V>  value type
     * @return the cache, if it was created
     */
    <K, V> Optional<Cache<K, V>> cache(String name);

    /** @return every cache by name */
    Map<String, Cache<?, ?>> caches();

    /** Invalidates every cache. */
    void invalidateAll();

    /** @return whether caching is enabled at all for this database */
    boolean isEnabled();
}
