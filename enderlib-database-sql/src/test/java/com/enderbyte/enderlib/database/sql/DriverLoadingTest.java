package com.enderbyte.enderlib.database.sql;

import com.enderbyte.enderlib.database.api.Database;
import com.enderbyte.enderlib.database.api.config.DatabaseConfig;
import com.enderbyte.enderlib.database.api.config.DatabaseType;
import com.enderbyte.enderlib.database.api.exception.ConfigurationException;
import org.junit.jupiter.api.Test;

import java.sql.Driver;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the driver lookup path, which is where a Minecraft plugin is most likely to fall over.
 *
 * <p>{@link DriverManager} resolves drivers with the system class loader, so it cannot see a driver
 * that lives inside a plugin jar; shading also tends to drop the {@code META-INF/services} entry a
 * driver relies on to register itself. Both end in {@code No suitable driver} even though the class
 * is sitting right there on the classpath.
 */
class DriverLoadingTest {

    @Test
    void connectsWithoutTheDriverBeingRegisteredWithDriverManager() {
        final List<Driver> removed = deregisterAll("org.h2.Driver");
        assertTrue(!removed.isEmpty(), "H2 should have registered itself before this test");
        try {
            // This is the plugin's situation: the class is loadable, DriverManager knows nothing.
            try (Database database = open("mem:driverless-" + UUID.randomUUID())) {
                assertEquals(1L, database.queryAsync("SELECT CAST(1 AS BIGINT)").join()
                        .value(Long.class).orElseThrow());
            }
        } finally {
            for (final Driver driver : removed) {
                register(driver);
            }
        }
    }

    @Test
    void defaultsToTheEngineDriverAndHonoursAnOverride() {
        assertEquals("org.h2.Driver", config("mem:defaults").driverClassName());
        assertEquals("org.mariadb.jdbc.Driver", DatabaseConfig.builder()
                .type(DatabaseType.MYSQL)
                .host("localhost")
                .database("test")
                .driverClassName("org.mariadb.jdbc.Driver")
                .build()
                .driverClassName());
    }

    @Test
    void reportsAMissingDriverWithItsMavenCoordinate() {
        final DatabaseConfig config = DatabaseConfig.builder()
                .type(DatabaseType.H2)
                .database("mem:missing")
                .driverClassName("com.example.NoSuchDriver")
                .build();

        final ConfigurationException error = assertThrows(ConfigurationException.class,
                () -> Database.builder().config(config).build());

        assertTrue(error.getMessage().contains("com.example.NoSuchDriver"));
        assertTrue(error.getMessage().contains("driverClassName"));
    }

    private static DatabaseConfig config(final String database) {
        return DatabaseConfig.builder()
                .type(DatabaseType.H2)
                .database(database)
                .username("sa")
                .password("")
                .build();
    }

    private static Database open(final String database) {
        return Database.builder().config(config(database + ";DB_CLOSE_DELAY=-1")).build().connect();
    }

    private static List<Driver> deregisterAll(final String className) {
        final List<Driver> removed = new ArrayList<>(1);
        DriverManager.drivers()
                .filter(driver -> driver.getClass().getName().equals(className))
                .forEach(removed::add);
        for (final Driver driver : removed) {
            try {
                DriverManager.deregisterDriver(driver);
            } catch (final Exception e) {
                throw new IllegalStateException("Could not deregister " + className, e);
            }
        }
        return removed;
    }

    private static void register(final Driver driver) {
        try {
            DriverManager.registerDriver(driver);
        } catch (final Exception e) {
            throw new IllegalStateException("Could not restore " + driver.getClass().getName(), e);
        }
    }
}
