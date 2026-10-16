package com.enderbyte.enderlib.database.sql;

import com.enderbyte.enderlib.database.api.config.DatabaseConfig;
import com.enderbyte.enderlib.database.api.config.DatabaseType;

/**
 * Builds JDBC URLs from configuration.
 *
 * <p>An explicit {@code connectionString} always wins; this is only used when a plugin configured
 * host, port and database separately.
 */
final class JdbcUrlBuilder {

    private JdbcUrlBuilder() {
    }

    /**
     * @param config validated configuration
     * @return the JDBC URL to hand to the pool
     */
    static String build(final DatabaseConfig config) {
        if (config.connectionString() != null && !config.connectionString().isBlank()) {
            return normalizeExplicit(config);
        }
        return switch (config.type()) {
            case POSTGRESQL, MYSQL, MARIADB -> networkUrl(config);
            case SQLITE -> "jdbc:sqlite:" + config.database();
            case H2 -> h2Url(config);
        };
    }

    private static String normalizeExplicit(final DatabaseConfig config) {
        final String provided = config.connectionString().trim();
        // Accept both "jdbc:postgresql://..." and a bare "postgresql://..." host string.
        return provided.startsWith("jdbc:") ? provided : "jdbc:" + provided;
    }

    private static String networkUrl(final DatabaseConfig config) {
        return config.type().uriPrefix()
                + config.host() + ':' + config.port() + '/' + config.database();
    }

    private static String h2Url(final DatabaseConfig config) {
        final String database = config.database();
        if (database.startsWith("mem:") || database.startsWith("tcp:") || database.startsWith("file:")) {
            return "jdbc:h2:" + database;
        }
        return "jdbc:h2:file:" + database;
    }

    /**
     * @param type engine
     * @return {@code true} when the engine is a single-file embedded database
     */
    static boolean isEmbeddedFile(final DatabaseType type) {
        return type == DatabaseType.SQLITE || type == DatabaseType.H2;
    }
}
