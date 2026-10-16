package com.enderbyte.enderlib.database.api.cache;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * An optional, asynchronous cache in front of the database.
 *
 * <pre>{@code
 * Cache<UUID, PlayerData> cache = database.cache("players", UUID.class, PlayerData.class)
 *         .maximumSize(100_000)
 *         .expireAfterWrite(Duration.ofMinutes(10))
 *         .build();
 *
 * cache.get(uuid, id -> repository.findById(id).thenApply(o -> o.orElse(null)))
 *      .thenAccept(player -> { ... });
 * }</pre>
 *
 * <p><strong>Caching across servers.</strong> A local cache is only safe for data this server owns.
 * If several servers write the same row, a stale entry here is a lost update there. Either scope
 * cache entries to players currently online on this server, keep the TTL short, or invalidate
 * explicitly when your network messaging tells you the record changed.
 *
 * @param <K> key type
 * @param <V> value type
 */
public interface Cache<K, V> {

    /** @return this cache's name, as used in metrics */
    String name();

    /** @return the cached value without triggering a load */
    Optional<V> getIfPresent(K key);

    /**
     * Returns the cached value, loading it asynchronously on a miss.
     *
     * <p>Concurrent requests for the same missing key share one load, so a thousand players joining
     * at once cannot turn into a thousand identical queries.
     *
     * @param key    cache key
     * @param loader asynchronous loader, may complete with {@code null} for "not found"
     * @return the value, possibly {@code null} when the loader found nothing
     */
    CompletableFuture<V> get(K key, Function<K, CompletableFuture<V>> loader);

    /** Stores a value. */
    void put(K key, V value);

    /** Removes one entry. Call this after writing the record, not before. */
    void invalidate(K key);

    /** Removes every entry. */
    void invalidateAll();

    /** @return an approximate entry count */
    long estimatedSize();

    /** @return hit/miss counters, or zeros when statistics were not enabled */
    CacheStats stats();

    /** @return a live, read-only view of the entries */
    Map<K, V> asMap();
}
