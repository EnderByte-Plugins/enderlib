package com.enderbyte.enderlib.database.sql;

import com.enderbyte.enderlib.database.api.config.DatabaseConfig;
import com.enderbyte.enderlib.database.api.config.DatabaseType;
import com.enderbyte.enderlib.database.api.config.PoolConfig;
import com.enderbyte.enderlib.database.api.exception.ConnectionException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Creates the HikariCP pool for a database.
 *
 * <p>HikariCP is used rather than a hand-written pool for the obvious reason: connection pooling is
 * full of subtle failure modes (validation, leak detection, clock changes, failover) that a mature
 * implementation has already solved.
 *
 * <p>Engine-specific driver properties are applied here, because they are worth real throughput:
 * server-side prepared statement caching on MySQL/MariaDB, and batch rewriting on PostgreSQL and
 * MySQL, which turns a thousand-row batch into a handful of round trips. Anything the plugin sets
 * explicitly wins over these defaults.
 */
final class HikariPoolFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(HikariPoolFactory.class);

    private HikariPoolFactory() {
    }

    /**
     * @param config validated configuration
     * @return a started pool
     * @throws ConnectionException if the pool cannot be created
     */
    static HikariDataSource create(final DatabaseConfig config) {
        final PoolConfig pool = config.pool();
        final HikariConfig hikari = new HikariConfig();

        hikari.setJdbcUrl(JdbcUrlBuilder.build(config));
        // Naming the driver makes HikariCP instantiate it directly instead of asking
        // DriverManager for one. That distinction is the difference between working and not
        // working inside a Minecraft plugin: DriverManager resolves drivers through the system
        // class loader, which cannot see a driver that lives in a plugin jar, so the lookup fails
        // with "No suitable driver" even though the class is right there.
        if (config.driverClassName() != null) {
            hikari.setDriverClassName(config.driverClassName());
        }
        if (config.username() != null) {
            hikari.setUsername(config.username());
        }
        if (config.password() != null) {
            hikari.setPassword(config.password());
        }
        if (config.schema() != null && !config.schema().isBlank()) {
            hikari.setSchema(config.schema());
        }

        hikari.setPoolName(pool.poolName() != null ? pool.poolName() : "EnderDB-" + config.name());
        hikari.setMaximumPoolSize(effectiveMaximumPoolSize(config));
        hikari.setMinimumIdle(Math.min(pool.minimumIdle(), effectiveMaximumPoolSize(config)));
        hikari.setConnectionTimeout(pool.connectionTimeout().toMillis());
        hikari.setIdleTimeout(pool.idleTimeout().toMillis());
        hikari.setMaxLifetime(pool.maxLifetime().toMillis());
        hikari.setKeepaliveTime(pool.keepaliveTime().toMillis());
        hikari.setValidationTimeout(pool.validationTimeout().toMillis());
        hikari.setLeakDetectionThreshold(pool.leakDetectionThreshold().toMillis());
        hikari.setAutoCommit(pool.autoCommit());
        hikari.setReadOnly(pool.readOnly());
        if (pool.connectionTestQuery() != null) {
            hikari.setConnectionTestQuery(pool.connectionTestQuery());
        }
        // Fail fast on startup: an unreachable database should surface during onEnable, not on the
        // first player join. The initialization failure is turned into our own exception below.
        hikari.setInitializationFailTimeout(config.validateOnConnect() ? 1L : -1L);

        applyEngineDefaults(config.type(), hikari);
        // Plugin-supplied properties last so they can override anything above.
        for (final Map.Entry<String, String> property : config.properties().entrySet()) {
            hikari.addDataSourceProperty(property.getKey(), property.getValue());
        }

        try {
            return new HikariDataSource(hikari);
        } catch (final RuntimeException e) {
            // The message may embed the JDBC URL, which can contain credentials when a plugin puts
            // them in a connection string; report our own redacted description instead.
            throw new ConnectionException("Could not create the connection pool for "
                    + config.toString(), e);
        }
    }

    /**
     * SQLite serialises writers at file level. More than one connection produces
     * {@code SQLITE_BUSY} storms rather than throughput, so the pool is capped at one.
     */
    private static int effectiveMaximumPoolSize(final DatabaseConfig config) {
        final int configured = config.pool().maximumPoolSize();
        if (config.type() == DatabaseType.SQLITE && configured > 1) {
            LOGGER.info("SQLite allows a single writer; capping the pool for '{}' at 1 connection"
                    + " (configured {}). Use PostgreSQL or MySQL for multi-server setups.",
                    config.name(), configured);
            return 1;
        }
        return configured;
    }

    private static void applyEngineDefaults(final DatabaseType type, final HikariConfig hikari) {
        switch (type) {
            case MYSQL, MARIADB -> {
                // Server-side prepared statements plus client-side caching: the single biggest win
                // for a plugin that runs the same handful of statements millions of times.
                hikari.addDataSourceProperty("cachePrepStmts", "true");
                hikari.addDataSourceProperty("prepStmtCacheSize", "250");
                hikari.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                hikari.addDataSourceProperty("useServerPrepStmts", "true");
                hikari.addDataSourceProperty("useLocalSessionState", "true");
                hikari.addDataSourceProperty("rewriteBatchedStatements", "true");
                hikari.addDataSourceProperty("cacheResultSetMetadata", "true");
                hikari.addDataSourceProperty("cacheServerConfiguration", "true");
                hikari.addDataSourceProperty("elideSetAutoCommits", "true");
                hikari.addDataSourceProperty("maintainTimeStats", "false");
            }
            case POSTGRESQL -> {
                // Collapses a batch of INSERTs into multi-row statements.
                hikari.addDataSourceProperty("reWriteBatchedInserts", "true");
            }
            case SQLITE -> {
                // WAL keeps readers from blocking the writer, which is what makes SQLite tolerable
                // for a single server; the busy timeout absorbs short write contention.
                hikari.addDataSourceProperty("journal_mode", "WAL");
                hikari.addDataSourceProperty("busy_timeout", "5000");
            }
            default -> {
                // H2 and unknown engines need no tuning beyond the pool defaults.
            }
        }
    }
}
