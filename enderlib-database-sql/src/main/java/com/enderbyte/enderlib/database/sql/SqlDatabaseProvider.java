package com.enderbyte.enderlib.database.sql;

import com.enderbyte.enderlib.database.api.Database;
import com.enderbyte.enderlib.database.api.config.DatabaseConfig;
import com.enderbyte.enderlib.database.api.config.DatabaseType;
import com.enderbyte.enderlib.database.api.exception.ConfigurationException;
import com.enderbyte.enderlib.database.api.provider.DatabaseProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers the JDBC implementation with {@link java.util.ServiceLoader}.
 *
 * <p>Public and with a public no-argument constructor because {@code ServiceLoader} requires both.
 */
public final class SqlDatabaseProvider implements DatabaseProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqlDatabaseProvider.class);

    @Override
    public String name() {
        return "jdbc";
    }

    @Override
    public boolean supports(final DatabaseType type) {
        return true;
    }

    @Override
    public Database create(final DatabaseConfig config) {
        requireDriver(config);
        return new HikariDatabase(config);
    }

    /**
     * Loads and initialises the JDBC driver.
     *
     * <p>Drivers are not dependencies of this library: a plugin ships the one it uses, so the
     * common mistake is forgetting it. Failing here, naming the Maven coordinate, beats a
     * {@code No suitable driver} error from deep inside the pool later.
     *
     * <p>The class is loaded with {@code initialize = true}, which matters more than it looks.
     * Every JDBC driver registers itself with {@link java.sql.DriverManager} from a static
     * initialiser, and loading without initialising skips exactly that. Drivers normally also
     * self-register through {@code META-INF/services/java.sql.Driver}, but that file is routinely
     * lost when a plugin is shaded without the services transformer, and {@code DriverManager}
     * scans it with the system class loader, which cannot see a class inside a plugin jar anyway.
     */
    private static void requireDriver(final DatabaseConfig config) {
        final String driverClass = config.driverClassName();
        if (driverClass == null) {
            return;
        }
        try {
            Class.forName(driverClass, true, SqlDatabaseProvider.class.getClassLoader());
        } catch (final ClassNotFoundException e) {
            throw new ConfigurationException("The JDBC driver for " + config.type().displayName()
                    + " (" + driverClass + ") is not on the classpath. Add "
                    + mavenCoordinate(config.type()) + " to your plugin, or shade it in."
                    + " If you are using a different driver on purpose, set it with"
                    + " driverClassName(...).", e);
        } catch (final LinkageError e) {
            throw new ConfigurationException("The JDBC driver " + driverClass + " is present but"
                    + " could not be initialised. This usually means a shading problem or two"
                    + " incompatible copies of the driver on the classpath.", e);
        }
        LOGGER.debug("Loaded the {} JDBC driver ({})", config.type().displayName(), driverClass);
    }

    private static String mavenCoordinate(final DatabaseType type) {
        return switch (type) {
            case POSTGRESQL -> "org.postgresql:postgresql";
            case MYSQL -> "com.mysql:mysql-connector-j";
            case MARIADB -> "org.mariadb.jdbc:mariadb-java-client";
            case SQLITE -> "org.xerial:sqlite-jdbc";
            case H2 -> "com.h2database:h2";
        };
    }
}
