package com.enderbyte.enderlib.database.core.cache;

import com.enderbyte.enderlib.database.api.cache.Cache;
import com.enderbyte.enderlib.database.api.cache.CacheStats;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * A cache that stores nothing and always calls the loader.
 *
 * <p>Returned when caching is disabled, or when Caffeine is not on the classpath. Plugin code keeps
 * calling {@code cache.get(key, loader)} and simply always hits the database, so disabling caching
 * never requires a second code path.
 *
 * @param <K> key type
 * @param <V> value type
 */
public final class PassthroughCache<K, V> implements Cache<K, V> {

    private final String name;
    private final Function<K, CompletableFuture<V>> defaultLoader;

    /**
     * @param name          cache name
     * @param defaultLoader loader used when a caller passes {@code null}, may itself be {@code null}
     */
    public PassthroughCache(final String name, final Function<K, CompletableFuture<V>> defaultLoader) {
        this.name = name;
        this.defaultLoader = defaultLoader;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Optional<V> getIfPresent(final K key) {
        return Optional.empty();
    }

    @Override
    public CompletableFuture<V> get(final K key, final Function<K, CompletableFuture<V>> loader) {
        final Function<K, CompletableFuture<V>> effective = loader != null ? loader : defaultLoader;
        if (effective == null) {
            return CompletableFuture.completedFuture(null);
        }
        return effective.apply(key);
    }

    @Override
    public void put(final K key, final V value) {
        // intentionally empty
    }

    @Override
    public void invalidate(final K key) {
        // intentionally empty
    }

    @Override
    public void invalidateAll() {
        // intentionally empty
    }

    @Override
    public long estimatedSize() {
        return 0L;
    }

    @Override
    public CacheStats stats() {
        return CacheStats.EMPTY;
    }

    @Override
    public Map<K, V> asMap() {
        return Map.of();
    }
}
