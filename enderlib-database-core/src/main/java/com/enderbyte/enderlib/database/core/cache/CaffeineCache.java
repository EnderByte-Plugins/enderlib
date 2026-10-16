package com.enderbyte.enderlib.database.core.cache;

import com.enderbyte.enderlib.database.api.cache.Cache;
import com.enderbyte.enderlib.database.api.cache.CacheStats;
import com.github.benmanes.caffeine.cache.AsyncCache;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * {@link Cache} backed by Caffeine's asynchronous cache.
 *
 * <p>The asynchronous variant matters: concurrent lookups of the same missing key share a single
 * in-flight load, so a join storm produces one query per player rather than one per request. A load
 * that completes with {@code null} leaves no entry behind, which is the behaviour you want for
 * "this player has no row yet".
 *
 * @param <K> key type
 * @param <V> value type
 */
public final class CaffeineCache<K, V> implements Cache<K, V> {

    private final String name;
    private final AsyncCache<K, V> delegate;
    private final Function<K, CompletableFuture<V>> defaultLoader;

    /**
     * @param name          cache name
     * @param delegate      the Caffeine cache
     * @param defaultLoader loader used when a caller passes {@code null}, may be {@code null}
     */
    public CaffeineCache(final String name,
                         final AsyncCache<K, V> delegate,
                         final Function<K, CompletableFuture<V>> defaultLoader) {
        this.name = name;
        this.delegate = delegate;
        this.defaultLoader = defaultLoader;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Optional<V> getIfPresent(final K key) {
        final CompletableFuture<V> future = delegate.getIfPresent(key);
        if (future == null || !future.isDone() || future.isCompletedExceptionally()) {
            return Optional.empty();
        }
        return Optional.ofNullable(future.join());
    }

    @Override
    public CompletableFuture<V> get(final K key, final Function<K, CompletableFuture<V>> loader) {
        final Function<K, CompletableFuture<V>> effective = loader != null ? loader : defaultLoader;
        if (effective == null) {
            final CompletableFuture<V> present = delegate.getIfPresent(key);
            return present != null ? present : CompletableFuture.completedFuture(null);
        }
        return delegate.get(key, (k, executor) -> effective.apply(k));
    }

    @Override
    public void put(final K key, final V value) {
        if (value == null) {
            delegate.synchronous().invalidate(key);
            return;
        }
        delegate.put(key, CompletableFuture.completedFuture(value));
    }

    @Override
    public void invalidate(final K key) {
        delegate.synchronous().invalidate(key);
    }

    @Override
    public void invalidateAll() {
        delegate.synchronous().invalidateAll();
    }

    @Override
    public long estimatedSize() {
        return delegate.synchronous().estimatedSize();
    }

    @Override
    public CacheStats stats() {
        final com.github.benmanes.caffeine.cache.stats.CacheStats stats = delegate.synchronous().stats();
        return new CacheStats(
                stats.hitCount(),
                stats.missCount(),
                stats.loadSuccessCount(),
                stats.loadFailureCount(),
                stats.evictionCount());
    }

    @Override
    public Map<K, V> asMap() {
        return Collections.unmodifiableMap(delegate.synchronous().asMap());
    }
}
