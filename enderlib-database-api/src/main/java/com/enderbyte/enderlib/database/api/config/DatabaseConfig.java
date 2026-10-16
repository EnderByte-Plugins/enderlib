package com.enderbyte.enderlib.database.api.config;

import com.enderbyte.enderlib.database.api.exception.ConfigurationException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, fully programmatic description of one database.
 *
 * <p>Deliberately not tied to any file format. This is a library, so the consuming plugin owns its
 * own configuration file and simply feeds the values in. {@link ConfigLoader} is provided for the
 * common case of a flat {@code key=value} map, but it is entirely optional.
 *
 * <p>Credentials are never included in {@link #toString()}, and no code path in the library logs
 * this object's password.
 *
 * <pre>{@code
 * DatabaseConfig config = DatabaseConfig.builder()
 *         .type(DatabaseType.POSTGRESQL)
 *         .host("localhost")
 *         .port(5432)
 *         .database("minecraft")
 *         .username("minecraft")
 *         .password(secretFromConfig)
 *         .poolSize(20)
 *         .build();
 * }</pre>
 */
public final class DatabaseConfig {

    private final String name;
    private final DatabaseType type;
    private final String host;
    private final int port;
    private final String database;
    private final String schema;
    private final String username;
    private final String password;
    private final String connectionString;
    private final String driverClassName;
    private final Map<String, String> properties;
    private final PoolConfig pool;
    private final ExecutorConfig executor;
    private final TimeoutConfig timeouts;
    private final RetryPolicy retryPolicy;
    private final boolean metricsEnabled;
    private final boolean cachingEnabled;
    private final boolean validateOnConnect;

    private DatabaseConfig(final Builder builder) {
        this.name = builder.name;
        this.type = builder.type;
        this.host = builder.host;
        this.port = builder.port;
        this.database = builder.database;
        this.schema = builder.schema;
        this.username = builder.username;
        this.password = builder.password;
        this.connectionString = builder.connectionString;
        this.driverClassName = builder.driverClassName;
        this.properties = Collections.unmodifiableMap(new LinkedHashMap<>(builder.properties));
        this.pool = builder.pool;
        this.executor = builder.executor;
        this.timeouts = builder.timeouts;
        this.retryPolicy = builder.retryPolicy;
        this.metricsEnabled = builder.metricsEnabled;
        this.cachingEnabled = builder.cachingEnabled;
        this.validateOnConnect = builder.validateOnConnect;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** @return logical name used for pools, threads, metrics and log messages */
    public String name() {
        return name;
    }

    public DatabaseType type() {
        return type;
    }

    /** @return host name, or {@code null} for embedded engines / connection-string configuration */
    public String host() {
        return host;
    }

    /** @return the port, resolved to the engine default when not set explicitly */
    public int port() {
        return port > 0 ? port : type.defaultPort();
    }

    /** @return database name, or the file path for SQLite/H2 */
    public String database() {
        return database;
    }

    /** @return schema/search-path to select after connecting, or {@code null} */
    public String schema() {
        return schema;
    }

    public String username() {
        return username;
    }

    /** @return the password; callers must never log this value */
    public String password() {
        return password;
    }

    /** @return an explicit JDBC URL, or {@code null} to build one from host/port/database */
    public String connectionString() {
        return connectionString;
    }

    /**
     * @return the JDBC driver class to use, or {@code null} to use the engine's default. Set this
     *         when the driver is not the expected one, for example the MariaDB driver against a
     *         MySQL server, or a cloud connector that wraps the stock driver.
     */
    public String driverClassName() {
        return driverClassName != null ? driverClassName : type.driverClassName();
    }

    /** @return additional driver properties, never {@code null} */
    public Map<String, String> properties() {
        return properties;
    }

    public PoolConfig pool() {
        return pool;
    }

    public ExecutorConfig executor() {
        return executor;
    }

    public TimeoutConfig timeouts() {
        return timeouts;
    }

    public RetryPolicy retryPolicy() {
        return retryPolicy;
    }

    public boolean metricsEnabled() {
        return metricsEnabled;
    }

    public boolean cachingEnabled() {
        return cachingEnabled;
    }

    /** @return whether {@code connect()} performs a round trip to prove the database is reachable */
    public boolean validateOnConnect() {
        return validateOnConnect;
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * @return a description safe to write to a log: host, port and database only, never credentials
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DatabaseConfig{name=").append(name)
                .append(", type=").append(type.displayName());
        if (connectionString != null) {
            sb.append(", connectionString=<redacted>");
        } else if (host != null) {
            sb.append(", host=").append(host).append(':').append(port());
        }
        if (database != null) {
            sb.append(", database=").append(database);
        }
        sb.append(", username=").append(username == null ? "<none>" : "<set>")
                .append(", password=").append(password == null ? "<none>" : "<redacted>")
                .append(", pool=").append(pool)
                .append('}');
        return sb.toString();
    }

    /** Mutable builder for {@link DatabaseConfig}. Not thread-safe; the built config is. */
    public static final class Builder {

        private String name;
        private DatabaseType type;
        private String host;
        private int port = -1;
        private String database;
        private String schema;
        private String username;
        private String password;
        private String connectionString;
        private String driverClassName;
        private final Map<String, String> properties = new LinkedHashMap<>();
        private PoolConfig pool = PoolConfig.defaults();
        private ExecutorConfig executor = ExecutorConfig.defaults();
        private TimeoutConfig timeouts = TimeoutConfig.defaults();
        private RetryPolicy retryPolicy = RetryPolicy.exponentialBackoff();
        private boolean metricsEnabled = true;
        private boolean cachingEnabled = true;
        private boolean validateOnConnect = true;

        private Builder() {
        }

        private Builder(final DatabaseConfig source) {
            this.name = source.name;
            this.type = source.type;
            this.host = source.host;
            this.port = source.port;
            this.database = source.database;
            this.schema = source.schema;
            this.username = source.username;
            this.password = source.password;
            this.connectionString = source.connectionString;
            this.driverClassName = source.driverClassName;
            this.properties.putAll(source.properties);
            this.pool = source.pool;
            this.executor = source.executor;
            this.timeouts = source.timeouts;
            this.retryPolicy = source.retryPolicy;
            this.metricsEnabled = source.metricsEnabled;
            this.cachingEnabled = source.cachingEnabled;
            this.validateOnConnect = source.validateOnConnect;
        }

        /**
         * Logical name for this database. Shows up in pool names, thread names and metrics tags.
         * Defaults to the database/file name. Give each database in a plugin a distinct name.
         */
        public Builder name(final String name) {
            this.name = name;
            return this;
        }

        public Builder type(final DatabaseType type) {
            this.type = Objects.requireNonNull(type, "type");
            return this;
        }

        public Builder host(final String host) {
            this.host = host;
            return this;
        }

        public Builder port(final int port) {
            if (port <= 0 || port > 65535) {
                throw new IllegalArgumentException("port must be within 1..65535");
            }
            this.port = port;
            return this;
        }

        /** Database name, or the file path for SQLite/H2 (for example {@code plugins/MyPlugin/data.db}). */
        public Builder database(final String database) {
            this.database = database;
            return this;
        }

        /** PostgreSQL schema / MySQL catalog to select after connecting. */
        public Builder schema(final String schema) {
            this.schema = schema;
            return this;
        }

        public Builder username(final String username) {
            this.username = username;
            return this;
        }

        /**
         * Sets the password. Read it from your plugin's configuration file or an environment
         * variable; never hard-code it.
         */
        public Builder password(final String password) {
            this.password = password;
            return this;
        }

        /**
         * Uses an explicit connection string instead of assembling one from host/port/database.
         * Everything after this point still applies (credentials, pool, executor).
         */
        public Builder connectionString(final String connectionString) {
            this.connectionString = connectionString;
            return this;
        }

        /**
         * Overrides the JDBC driver class. Only needed when the driver is not the engine's usual
         * one — the MariaDB driver against MySQL, or a cloud connector wrapping the stock driver.
         */
        public Builder driverClassName(final String driverClassName) {
            this.driverClassName = driverClassName;
            return this;
        }

        /** Adds a driver-specific property, for example {@code sslmode=require}. */
        public Builder property(final String key, final String value) {
            this.properties.put(Objects.requireNonNull(key, "key"), value);
            return this;
        }

        public Builder properties(final Map<String, String> properties) {
            this.properties.putAll(Objects.requireNonNull(properties, "properties"));
            return this;
        }

        public Builder pool(final PoolConfig pool) {
            this.pool = Objects.requireNonNull(pool, "pool");
            return this;
        }

        /** Shorthand for {@code pool(PoolConfig.builder().maximumPoolSize(size).build())}. */
        public Builder poolSize(final int size) {
            this.pool = this.pool.toBuilder().maximumPoolSize(size).build();
            return this;
        }

        public Builder executor(final ExecutorConfig executor) {
            this.executor = Objects.requireNonNull(executor, "executor");
            return this;
        }

        public Builder timeouts(final TimeoutConfig timeouts) {
            this.timeouts = Objects.requireNonNull(timeouts, "timeouts");
            return this;
        }

        public Builder retryPolicy(final RetryPolicy retryPolicy) {
            this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
            return this;
        }

        /** Metrics collection is cheap (lock-free counters) but can be switched off entirely. */
        public Builder metricsEnabled(final boolean metricsEnabled) {
            this.metricsEnabled = metricsEnabled;
            return this;
        }

        /**
         * When {@code false}, {@code database.cache()} hands out no-op caches, so a plugin can
         * disable caching globally without touching its own code paths.
         */
        public Builder cachingEnabled(final boolean cachingEnabled) {
            this.cachingEnabled = cachingEnabled;
            return this;
        }

        /** When {@code true} (the default), {@code connect()} fails fast if the database is unreachable. */
        public Builder validateOnConnect(final boolean validateOnConnect) {
            this.validateOnConnect = validateOnConnect;
            return this;
        }

        /**
         * @return the validated, immutable configuration
         * @throws ConfigurationException if required values for the chosen type are missing
         */
        public DatabaseConfig build() {
            if (type == null) {
                throw new ConfigurationException("A database type must be set");
            }
            if (connectionString == null) {
                if (type.isNetworked() && (host == null || host.isBlank())) {
                    throw new ConfigurationException(type.displayName()
                            + " requires either host() or connectionString()");
                }
                if (database == null || database.isBlank()) {
                    throw new ConfigurationException(type.displayName()
                            + " requires database() (a database name, or a file path for SQLite/H2)");
                }
            }
            if (name == null || name.isBlank()) {
                name = database != null && !database.isBlank()
                        ? sanitizeName(database)
                        : type.name().toLowerCase(java.util.Locale.ROOT);
            }
            return new DatabaseConfig(this);
        }

        /** Strips path separators so a SQLite file path still yields a usable pool/thread name. */
        private static String sanitizeName(final String raw) {
            final int slash = Math.max(raw.lastIndexOf('/'), raw.lastIndexOf('\\'));
            final String tail = slash >= 0 ? raw.substring(slash + 1) : raw;
            final String cleaned = tail.replaceAll("[^A-Za-z0-9._-]", "");
            return cleaned.isBlank() ? "database" : cleaned;
        }
    }
}
