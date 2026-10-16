package com.enderbyte.enderlib.database.core.cache;

import com.enderbyte.enderlib.database.api.cache.Cache;
import com.enderbyte.enderlib.database.api.cache.CacheBuilder;
import com.enderbyte.enderlib.database.api.cache.CacheManager;
import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Owns the caches of one database and decides which implementation to hand out.
 *
 * <p>Caffeine is an optional dependency. When it is absent, or when caching is switched off in the
 * configuration, every cache is a {@link PassthroughCache} and the plugin's code path is unchanged.
 * The Caffeine classes are only touched from {@link CaffeineCache} and from the private builder
 * below, so the JVM never tries to load them when the jar is missing.
 */
public final class DefaultCacheManager implements CacheManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultCacheManager.class);

    private static final boolean CAFFEINE_AVAILABLE = isCaffeineAvailable();

    private final Map<String, Cache<?, ?>> caches = new ConcurrentHashMap<>();
    private final boolean enabled;
    private final Executor executor;
    private final String databaseName;

    /**
     * @param databaseName database name, for log messages
     * @param enabled      whether caching is enabled in the configuration
     * @param executor     executor used for asynchronous loads and maintenance
     */
    public DefaultCacheManager(final String databaseName, final boolean enabled, final Executor executor) {
        this.databaseName = databaseName;
        this.executor = executor;
        this.enabled = enabled && CAFFEINE_AVAILABLE;
        if (enabled && !CAFFEINE_AVAILABLE) {
            LOGGER.info("Caching was requested for database '{}' but Caffeine is not on the classpath;"
                    + " caches will pass through to the database. Add"
                    + " com.github.ben-manes.caffeine:caffeine to enable it.", databaseName);
        }
    }

    @Override
    public <K, V> CacheBuilder<K, V> newCache(final String name) {
        return new Builder<>(name);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <K, V> Optional<Cache<K, V>> cache(final String name) {
        return Optional.ofNullable((Cache<K, V>) caches.get(name));
    }

    @Override
    public Map<String, Cache<?, ?>> caches() {
        return Collections.unmodifiableMap(new HashMap<>(caches));
    }

    @Override
    public void invalidateAll() {
        caches.values().forEach(Cache::invalidateAll);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /** Drops every cache; called during database shutdown so nothing keeps entries alive. */
    public void clear() {
        invalidateAll();
        caches.clear();
    }

    private static boolean isCaffeineAvailable() {
        try {
            Class.forName("com.github.benmanes.caffeine.cache.Caffeine", false,
                    DefaultCacheManager.class.getClassLoader());
            return true;
        } catch (final ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** Builder that yields either a Caffeine-backed or a pass-through cache. */
    private final class Builder<K, V> implements CacheBuilder<K, V> {

        private final String name;
        private long maximumSize = 10_000L;
        private Duration expireAfterWrite;
        private Duration expireAfterAccess;
        private Duration refreshAfterWrite;
        private boolean recordStats = true;
        private boolean softValues;
        private Function<K, CompletableFuture<V>> loader;

        private Builder(final String name) {
            this.name = name;
        }

        @Override
        public CacheBuilder<K, V> maximumSize(final long maximumSize) {
            this.maximumSize = maximumSize;
            return this;
        }

        @Override
        public CacheBuilder<K, V> expireAfterWrite(final Duration duration) {
            this.expireAfterWrite = duration;
            return this;
        }

        @Override
        public CacheBuilder<K, V> expireAfterAccess(final Duration duration) {
            this.expireAfterAccess = duration;
            return this;
        }

        @Override
        public CacheBuilder<K, V> refreshAfterWrite(final Duration duration) {
            this.refreshAfterWrite = duration;
            return this;
        }

        @Override
        public CacheBuilder<K, V> recordStats(final boolean recordStats) {
            this.recordStats = recordStats;
            return this;
        }

        @Override
        public CacheBuilder<K, V> softValues(final boolean softValues) {
            this.softValues = softValues;
            return this;
        }

        @Override
        public CacheBuilder<K, V> loader(final Function<K, CompletableFuture<V>> loader) {
            this.loader = loader;
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Cache<K, V> build() {
            final Cache<?, ?> existing = caches.get(name);
            if (existing != null) {
                return (Cache<K, V>) existing;
            }
            if (refreshAfterWrite != null && loader == null) {
                throw new IllegalStateException("refreshAfterWrite requires loader(...) on cache '"
                        + name + "': a background refresh has no caller to load from");
            }
            final Cache<K, V> created = enabled ? buildCaffeine() : new PassthroughCache<>(name, loader);
            final Cache<?, ?> raced = caches.putIfAbsent(name, created);
            return raced != null ? (Cache<K, V>) raced : created;
        }

        private Cache<K, V> buildCaffeine() {
            final Caffeine<Object, Object> caffeine = Caffeine.newBuilder()
                    .maximumSize(maximumSize)
                    .executor(executor);
            if (expireAfterWrite != null) {
                caffeine.expireAfterWrite(expireAfterWrite);
            }
            if (expireAfterAccess != null) {
                caffeine.expireAfterAccess(expireAfterAccess);
            }
            if (refreshAfterWrite != null) {
                caffeine.refreshAfterWrite(refreshAfterWrite);
            }
            if (recordStats) {
                caffeine.recordStats();
            }
            if (softValues) {
                caffeine.softValues();
            }
            final AsyncCache<K, V> delegate = loader == null
                    ? caffeine.buildAsync()
                    : caffeine.buildAsync((K key, Executor cacheExecutor) -> loader.apply(key));
            LOGGER.debug("Created cache '{}' for database '{}' (maximumSize={})",
                    name, databaseName, maximumSize);
            return new CaffeineCache<>(name, delegate, loader);
        }
    }
}
