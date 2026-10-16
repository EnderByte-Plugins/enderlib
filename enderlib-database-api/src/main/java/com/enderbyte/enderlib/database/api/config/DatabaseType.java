package com.enderbyte.enderlib.database.api.config;

import java.util.Locale;
import java.util.Optional;

/**
 * The database engines shipped with the library.
 *
 * <p>This enum carries only <em>metadata</em> (JDBC URL prefix, default port, driver class name).
 * No behaviour is switched on it outside of the provider module, which keeps the API free of
 * engine-specific logic and makes adding another JDBC engine a matter of adding a constant plus a
 * {@link com.enderbyte.enderlib.database.api.query.SqlDialect}.
 */
public enum DatabaseType {

    /** PostgreSQL 12+. Recommended default for large networks. */
    POSTGRESQL("PostgreSQL", 5432, "jdbc:postgresql://", "org.postgresql.Driver", true),

    /** Oracle MySQL 8+. */
    MYSQL("MySQL", 3306, "jdbc:mysql://", "com.mysql.cj.jdbc.Driver", true),

    /** MariaDB 10.4+. Wire-compatible with MySQL but uses its own driver. */
    MARIADB("MariaDB", 3306, "jdbc:mariadb://", "org.mariadb.jdbc.Driver", true),

    /** File-backed SQLite. Single-server only: never share a SQLite file between servers. */
    SQLITE("SQLite", -1, "jdbc:sqlite:", "org.sqlite.JDBC", false),

    /** H2, primarily useful for tests and single-server embedded setups. */
    H2("H2", -1, "jdbc:h2:", "org.h2.Driver", false);

    private final String displayName;
    private final int defaultPort;
    private final String uriPrefix;
    private final String driverClassName;
    private final boolean networked;

    DatabaseType(final String displayName,
                 final int defaultPort,
                 final String uriPrefix,
                 final String driverClassName,
                 final boolean networked) {
        this.displayName = displayName;
        this.defaultPort = defaultPort;
        this.uriPrefix = uriPrefix;
        this.driverClassName = driverClassName;
        this.networked = networked;
    }

    /** @return human readable engine name, safe for log messages */
    public String displayName() {
        return displayName;
    }

    /** @return the default TCP port, or {@code -1} for embedded/file based engines */
    public int defaultPort() {
        return defaultPort;
    }

    /** @return the JDBC URI prefix used when a connection string is not supplied */
    public String uriPrefix() {
        return uriPrefix;
    }

    /**
     * @return the canonical driver class, or {@code null} when the engine does not use JDBC.
     *         Drivers are <em>not</em> a dependency of this library; the consuming plugin ships the
     *         one it needs.
     */
    public String driverClassName() {
        return driverClassName;
    }

    /**
     * @return {@code true} when the engine is reached over the network and therefore needs a host,
     *         port and usually credentials; {@code false} for file/in-memory engines
     */
    public boolean isNetworked() {
        return networked;
    }

    /**
     * Case-insensitive lookup, convenient when the type comes from a plugin's own YAML config.
     *
     * @param value name such as {@code "postgresql"}, {@code "MySQL"} or {@code "h2"}
     * @return the matching type, or empty when unknown
     */
    public static Optional<DatabaseType> fromString(final String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        final String normalized = value.trim().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
        return switch (normalized) {
            case "postgresql", "postgres", "pgsql", "pg" -> Optional.of(POSTGRESQL);
            case "mysql" -> Optional.of(MYSQL);
            case "mariadb", "maria" -> Optional.of(MARIADB);
            case "sqlite", "sqlite3" -> Optional.of(SQLITE);
            case "h2" -> Optional.of(H2);
            default -> Optional.empty();
        };
    }
}
