package com.enderbyte.enderlib.database.api.config;

import com.enderbyte.enderlib.database.api.exception.ConfigurationException;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Optional helper that turns a flat {@code key -> value} map into a {@link DatabaseConfig}.
 *
 * <p>The library never reads a file itself: a plugin already has its own configuration mechanism
 * (usually Bukkit's {@code config.yml}) and this simply saves it from writing the same twenty
 * builder calls. Feed it {@code config.getConfigurationSection("database").getValues(true)} or a
 * {@link Properties} instance.
 *
 * <p>Recognised keys (all optional except {@code type} and the connection target):
 * <pre>
 * type                          postgresql | mysql | mariadb | sqlite | h2
 * name                          logical name
 * host / port / database        connection target
 * schema                        schema to select after connecting
 * username / password           credentials
 * connection-string             full JDBC URL, replaces host/port/database
 * driver-class-name             JDBC driver class, when not the engine's usual one
 * properties.&lt;key&gt;              driver property passed through verbatim
 * pool.maximum-size             int
 * pool.minimum-idle             int
 * pool.connection-timeout-ms    long
 * pool.idle-timeout-ms          long
 * pool.max-lifetime-ms          long
 * pool.keepalive-ms             long
 * pool.leak-detection-ms        long
 * pool.name                     string
 * executor.core-size            int
 * executor.maximum-size         int
 * executor.queue-capacity       int
 * timeout.query-ms              long
 * timeout.transaction-ms        long
 * retry.max-attempts            int
 * retry.initial-delay-ms        long
 * retry.max-delay-ms            long
 * metrics.enabled               boolean
 * cache.enabled                 boolean
 * </pre>
 */
public final class ConfigLoader {

    private ConfigLoader() {
    }

    /** Loads from a {@link Properties} instance, for example one read from a plugin data folder. */
    public static DatabaseConfig fromProperties(final Properties properties) {
        Objects.requireNonNull(properties, "properties");
        final Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (final String key : properties.stringPropertyNames()) {
            map.put(key, properties.getProperty(key));
        }
        return fromMap(map);
    }

    /**
     * Loads from an arbitrary map. Values may be strings or already-typed numbers/booleans, which
     * is what YAML parsers usually hand back.
     *
     * @param values configuration values, keys as documented on this class
     * @return the resulting configuration
     * @throws ConfigurationException if a value is missing or cannot be parsed
     */
    public static DatabaseConfig fromMap(final Map<String, ?> values) {
        Objects.requireNonNull(values, "values");

        final String rawType = string(values, "type", null);
        if (rawType == null) {
            throw new ConfigurationException("Missing required configuration key 'type'");
        }
        final DatabaseType type = DatabaseType.fromString(rawType)
                .orElseThrow(() -> new ConfigurationException("Unknown database type '" + rawType + "'"));

        final DatabaseConfig.Builder builder = DatabaseConfig.builder().type(type);

        applyIfPresent(string(values, "name", null), builder::name);
        applyIfPresent(string(values, "host", null), builder::host);
        applyIfPresent(string(values, "database", null), builder::database);
        applyIfPresent(string(values, "schema", null), builder::schema);
        applyIfPresent(string(values, "username", null), builder::username);
        applyIfPresent(string(values, "password", null), builder::password);
        applyIfPresent(string(values, "connection-string", null), builder::connectionString);
        applyIfPresent(string(values, "driver-class-name", null), builder::driverClassName);
        final Integer port = integer(values, "port", null);
        if (port != null) {
            builder.port(port);
        }

        for (final Map.Entry<String, ?> entry : values.entrySet()) {
            if (entry.getKey().startsWith("properties.") && entry.getValue() != null) {
                builder.property(entry.getKey().substring("properties.".length()), entry.getValue().toString());
            }
        }

        builder.pool(pool(values));
        builder.executor(executor(values));
        builder.timeouts(timeouts(values));
        builder.retryPolicy(retry(values));
        builder.metricsEnabled(bool(values, "metrics.enabled", true));
        builder.cachingEnabled(bool(values, "cache.enabled", true));

        return builder.build();
    }

    private static PoolConfig pool(final Map<String, ?> values) {
        final PoolConfig.Builder pool = PoolConfig.builder();
        final Integer maximumSize = integer(values, "pool.maximum-size", null);
        if (maximumSize != null) {
            pool.maximumPoolSize(maximumSize);
        }
        final Integer minimumIdle = integer(values, "pool.minimum-idle", null);
        if (minimumIdle != null) {
            pool.minimumIdle(minimumIdle);
        }
        duration(values, "pool.connection-timeout-ms").ifPresent(pool::connectionTimeout);
        duration(values, "pool.idle-timeout-ms").ifPresent(pool::idleTimeout);
        duration(values, "pool.max-lifetime-ms").ifPresent(pool::maxLifetime);
        duration(values, "pool.keepalive-ms").ifPresent(pool::keepaliveTime);
        duration(values, "pool.leak-detection-ms").ifPresent(pool::leakDetectionThreshold);
        applyIfPresent(string(values, "pool.name", null), pool::poolName);
        return pool.build();
    }

    private static ExecutorConfig executor(final Map<String, ?> values) {
        final ExecutorConfig.Builder executor = ExecutorConfig.builder();
        final Integer coreSize = integer(values, "executor.core-size", null);
        if (coreSize != null) {
            executor.coreSize(coreSize);
        }
        final Integer maximumSize = integer(values, "executor.maximum-size", null);
        if (maximumSize != null) {
            executor.maximumSize(maximumSize);
        }
        final Integer queueCapacity = integer(values, "executor.queue-capacity", null);
        if (queueCapacity != null) {
            executor.queueCapacity(queueCapacity);
        }
        return executor.build();
    }

    private static TimeoutConfig timeouts(final Map<String, ?> values) {
        final TimeoutConfig.Builder timeouts = TimeoutConfig.builder();
        duration(values, "timeout.query-ms").ifPresent(timeouts::query);
        duration(values, "timeout.transaction-ms").ifPresent(timeouts::transaction);
        duration(values, "timeout.connect-ms").ifPresent(timeouts::connect);
        return timeouts.build();
    }

    private static RetryPolicy retry(final Map<String, ?> values) {
        RetryPolicy policy = RetryPolicy.exponentialBackoff();
        final Integer maxAttempts = integer(values, "retry.max-attempts", null);
        if (maxAttempts != null) {
            policy = policy.maxAttempts(maxAttempts);
        }
        final java.util.Optional<Duration> initial = duration(values, "retry.initial-delay-ms");
        if (initial.isPresent()) {
            policy = policy.initialDelay(initial.get());
        }
        final java.util.Optional<Duration> max = duration(values, "retry.max-delay-ms");
        if (max.isPresent()) {
            policy = policy.maxDelay(max.get());
        }
        return policy;
    }

    private static void applyIfPresent(final String value, final java.util.function.Consumer<String> setter) {
        if (value != null && !value.isBlank()) {
            setter.accept(value);
        }
    }

    private static String string(final Map<String, ?> values, final String key, final String fallback) {
        final Object value = values.get(key);
        return value == null ? fallback : value.toString();
    }

    private static Integer integer(final Map<String, ?> values, final String key, final Integer fallback) {
        final Object value = values.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (final NumberFormatException e) {
            throw new ConfigurationException("Configuration key '" + key + "' must be an integer", e);
        }
    }

    private static java.util.Optional<Duration> duration(final Map<String, ?> values, final String key) {
        final Object value = values.get(key);
        if (value == null) {
            return java.util.Optional.empty();
        }
        if (value instanceof Number number) {
            return java.util.Optional.of(Duration.ofMillis(number.longValue()));
        }
        try {
            return java.util.Optional.of(Duration.ofMillis(Long.parseLong(value.toString().trim())));
        } catch (final NumberFormatException e) {
            throw new ConfigurationException("Configuration key '" + key + "' must be a millisecond value", e);
        }
    }

    private static boolean bool(final Map<String, ?> values, final String key, final boolean fallback) {
        final Object value = values.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        final String text = value.toString().trim().toLowerCase(Locale.ROOT);
        return switch (text) {
            case "true", "yes", "on", "1" -> true;
            case "false", "no", "off", "0" -> false;
            default -> throw new ConfigurationException("Configuration key '" + key + "' must be a boolean");
        };
    }
}
