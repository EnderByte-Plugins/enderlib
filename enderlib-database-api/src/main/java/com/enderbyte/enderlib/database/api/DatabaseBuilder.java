package com.enderbyte.enderlib.database.api;

import com.enderbyte.enderlib.database.api.config.DatabaseConfig;
import com.enderbyte.enderlib.database.api.config.DatabaseType;
import com.enderbyte.enderlib.database.api.config.ExecutorConfig;
import com.enderbyte.enderlib.database.api.config.PoolConfig;
import com.enderbyte.enderlib.database.api.config.RetryPolicy;
import com.enderbyte.enderlib.database.api.config.TimeoutConfig;
import com.enderbyte.enderlib.database.api.exception.ConfigurationException;
import com.enderbyte.enderlib.database.api.provider.DatabaseProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Fluent entry point for creating a {@link Database}.
 *
 * <pre>{@code
 * Database database = Database.builder()
 *         .type(DatabaseType.POSTGRESQL)
 *         .host("localhost")
 *         .port(5432)
 *         .database("minecraft")
 *         .username("minecraft")
 *         .password(password)
 *         .poolSize(10)
 *         .build();
 * }</pre>
 *
 * <p>Connection-string configuration works the same way:
 *
 * <pre>{@code
 * Database database = Database.builder()
 *         .type(DatabaseType.POSTGRESQL)
 *         .connectionString("jdbc:postgresql://localhost:5432/minecraft")
 *         .username("minecraft")
 *         .password(password)
 *         .build();
 * }</pre>
 *
 * <p>{@link #build()} does no I/O: it validates, finds the provider module for the requested engine
 * and constructs the instance. Connecting is a separate, explicit step.
 */
public final class DatabaseBuilder {

    private DatabaseConfig.Builder config = DatabaseConfig.builder();

    DatabaseBuilder() {
    }

    /** @see DatabaseConfig.Builder#type(DatabaseType) */
    public DatabaseBuilder type(final DatabaseType type) {
        config.type(type);
        return this;
    }

    /** @see DatabaseConfig.Builder#name(String) */
    public DatabaseBuilder name(final String name) {
        config.name(name);
        return this;
    }

    /** @see DatabaseConfig.Builder#host(String) */
    public DatabaseBuilder host(final String host) {
        config.host(host);
        return this;
    }

    /** @see DatabaseConfig.Builder#port(int) */
    public DatabaseBuilder port(final int port) {
        config.port(port);
        return this;
    }

    /** @see DatabaseConfig.Builder#database(String) */
    public DatabaseBuilder database(final String database) {
        config.database(database);
        return this;
    }

    /** @see DatabaseConfig.Builder#schema(String) */
    public DatabaseBuilder schema(final String schema) {
        config.schema(schema);
        return this;
    }

    /** @see DatabaseConfig.Builder#username(String) */
    public DatabaseBuilder username(final String username) {
        config.username(username);
        return this;
    }

    /** @see DatabaseConfig.Builder#password(String) */
    public DatabaseBuilder password(final String password) {
        config.password(password);
        return this;
    }

    /** @see DatabaseConfig.Builder#connectionString(String) */
    public DatabaseBuilder connectionString(final String connectionString) {
        config.connectionString(connectionString);
        return this;
    }

    /** @see DatabaseConfig.Builder#driverClassName(String) */
    public DatabaseBuilder driverClassName(final String driverClassName) {
        config.driverClassName(driverClassName);
        return this;
    }

    /** @see DatabaseConfig.Builder#property(String, String) */
    public DatabaseBuilder property(final String key, final String value) {
        config.property(key, value);
        return this;
    }

    /** @see DatabaseConfig.Builder#properties(Map) */
    public DatabaseBuilder properties(final Map<String, String> properties) {
        config.properties(properties);
        return this;
    }

    /** @see DatabaseConfig.Builder#pool(PoolConfig) */
    public DatabaseBuilder pool(final PoolConfig pool) {
        config.pool(pool);
        return this;
    }

    /** @see DatabaseConfig.Builder#poolSize(int) */
    public DatabaseBuilder poolSize(final int poolSize) {
        config.poolSize(poolSize);
        return this;
    }

    /** @see DatabaseConfig.Builder#executor(ExecutorConfig) */
    public DatabaseBuilder executor(final ExecutorConfig executor) {
        config.executor(executor);
        return this;
    }

    /** @see DatabaseConfig.Builder#timeouts(TimeoutConfig) */
    public DatabaseBuilder timeouts(final TimeoutConfig timeouts) {
        config.timeouts(timeouts);
        return this;
    }

    /** @see DatabaseConfig.Builder#retryPolicy(RetryPolicy) */
    public DatabaseBuilder retryPolicy(final RetryPolicy retryPolicy) {
        config.retryPolicy(retryPolicy);
        return this;
    }

    /** @see DatabaseConfig.Builder#metricsEnabled(boolean) */
    public DatabaseBuilder metricsEnabled(final boolean metricsEnabled) {
        config.metricsEnabled(metricsEnabled);
        return this;
    }

    /** @see DatabaseConfig.Builder#cachingEnabled(boolean) */
    public DatabaseBuilder cachingEnabled(final boolean cachingEnabled) {
        config.cachingEnabled(cachingEnabled);
        return this;
    }

    /** @see DatabaseConfig.Builder#validateOnConnect(boolean) */
    public DatabaseBuilder validateOnConnect(final boolean validateOnConnect) {
        config.validateOnConnect(validateOnConnect);
        return this;
    }

    /** Replaces everything configured so far with an existing configuration. */
    public DatabaseBuilder config(final DatabaseConfig existing) {
        return fromConfig(existing);
    }

    /** @return the configuration this builder would produce, without creating a database */
    public DatabaseConfig buildConfig() {
        return config.build();
    }

    /**
     * Creates the database. Does not connect.
     *
     * @return the database instance
     * @throws ConfigurationException if the configuration is incomplete, or if no provider module
     *         for the requested engine is on the classpath
     */
    public Database build() {
        final DatabaseConfig built = config.build();
        return provider(built.type()).create(built);
    }

    /**
     * Creates the database and connects immediately.
     *
     * @return the connected database
     */
    public Database connect() {
        return build().connect();
    }

    /**
     * Replaces the pending configuration wholesale.
     *
     * <p>Delegates to {@link DatabaseConfig#toBuilder()} rather than copying field by field, so a
     * value added to the configuration later cannot be silently dropped here.
     */
    private DatabaseBuilder fromConfig(final DatabaseConfig existing) {
        this.config = existing.toBuilder();
        return this;
    }

    /**
     * Finds the provider for an engine.
     *
     * <p>Looks in this class's own class loader first, which is the plugin's loader on a Minecraft
     * server, then falls back to the thread context loader. Both are needed: server platforms
     * isolate plugin class loaders, and a shaded library must find the services file that was
     * shaded alongside it.
     */
    private static DatabaseProvider provider(final DatabaseType type) {
        final List<DatabaseProvider> providers = new ArrayList<>(4);
        collect(providers, DatabaseProvider.class.getClassLoader());
        collect(providers, Thread.currentThread().getContextClassLoader());

        for (final DatabaseProvider provider : providers) {
            if (provider.supports(type)) {
                return provider;
            }
        }
        throw new ConfigurationException("No database provider found for " + type.displayName()
                + ". Add the enderlib-database-sql module to your plugin. If you shade this library, make "
                + "sure the Shade plugin's ServicesResourceTransformer is enabled so "
                + "META-INF/services entries survive the merge.");
    }

    private static void collect(final List<DatabaseProvider> target, final ClassLoader loader) {
        if (loader == null) {
            return;
        }
        for (final DatabaseProvider provider : ServiceLoader.load(DatabaseProvider.class, loader)) {
            boolean duplicate = false;
            for (final DatabaseProvider existing : target) {
                if (existing.getClass() == provider.getClass()) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                target.add(provider);
            }
        }
    }
}
