package com.enderbyte.enderlib.database.api.provider;

import com.enderbyte.enderlib.database.api.Database;
import com.enderbyte.enderlib.database.api.config.DatabaseConfig;
import com.enderbyte.enderlib.database.api.config.DatabaseType;

/**
 * Service-provider interface that turns configuration into a {@link Database}.
 *
 * <p>Providers are discovered with {@link java.util.ServiceLoader}, which is what keeps the API
 * module free of HikariCP and of any JDBC driver: a plugin compiles against {@code enderlib-database-api}
 * and adds {@code enderlib-database-sql} plus the one driver it actually uses. Supporting another engine
 * means adding a provider, not changing the API.
 *
 * <p>Implementations register themselves in
 * {@code META-INF/services/com.enderbyte.enderlib.database.api.provider.DatabaseProvider}.
 *
 * <p><strong>Shading note.</strong> Service files must be merged when shading; the Maven Shade
 * {@code ServicesResourceTransformer} does this. Without it, provider lookup fails at runtime with
 * "no provider available". See {@code docs/minecraft-integration.md}.
 */
public interface DatabaseProvider {

    /** @return short provider name, used in log messages */
    String name();

    /**
     * @param type requested engine
     * @return {@code true} if this provider can serve it
     */
    boolean supports(DatabaseType type);

    /**
     * Creates a database instance. Must not open connections: connecting happens in
     * {@link Database#connect()}, so construction never blocks.
     *
     * @param config validated configuration
     * @return the database, not yet connected
     */
    Database create(DatabaseConfig config);
}
