package com.enderbyte.enderlib.database.core;

import com.enderbyte.enderlib.database.api.Database;
import com.enderbyte.enderlib.database.api.exception.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the databases a plugin uses, so they can be looked up by name and closed together.
 *
 * <pre>{@code
 * registry.register(playerDatabase);
 * registry.register(analyticsDatabase);
 *
 * // onDisable
 * registry.closeAll();
 * }</pre>
 *
 * <p>Deliberately an ordinary object rather than a static singleton: two plugins on one server each
 * own their registry, and neither can accidentally close the other's connections. Create one per
 * plugin, keep it in a field.
 *
 * <p>Thread-safe.
 */
public final class DatabaseRegistry implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseRegistry.class);

    private final Map<String, Database> databases = new ConcurrentHashMap<>();

    /**
     * Registers a database under its own name.
     *
     * @param database the database
     * @param <T>      database type
     * @return the same instance, for chaining
     * @throws ConfigurationException if a database with that name is already registered
     */
    public <T extends Database> T register(final T database) {
        return register(database.name(), database);
    }

    /**
     * Registers a database under an explicit name.
     *
     * @param name     lookup name
     * @param database the database
     * @param <T>      database type
     * @return the same instance, for chaining
     * @throws ConfigurationException if the name is taken
     */
    public <T extends Database> T register(final String name, final T database) {
        final Database previous = databases.putIfAbsent(name, database);
        if (previous != null) {
            throw new ConfigurationException("A database named '" + name + "' is already registered");
        }
        return database;
    }

    /**
     * @param name lookup name
     * @return the database, if registered
     */
    public Optional<Database> get(final String name) {
        return Optional.ofNullable(databases.get(name));
    }

    /**
     * @param name lookup name
     * @return the database
     * @throws ConfigurationException if no database is registered under that name
     */
    public Database require(final String name) {
        final Database database = databases.get(name);
        if (database == null) {
            throw new ConfigurationException("No database named '" + name + "' is registered");
        }
        return database;
    }

    /**
     * Removes a database without closing it.
     *
     * @param name lookup name
     * @return the removed database, if present
     */
    public Optional<Database> unregister(final String name) {
        return Optional.ofNullable(databases.remove(name));
    }

    /** @return every registered database by name */
    public Map<String, Database> all() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(databases));
    }

    /** @return how many databases are registered */
    public int size() {
        return databases.size();
    }

    /**
     * Closes every registered database.
     *
     * <p>A failure closing one does not prevent the others from closing: on plugin disable the
     * priority is releasing every resource, not reporting the first problem.
     */
    public void closeAll() {
        for (final Map.Entry<String, Database> entry : databases.entrySet()) {
            try {
                entry.getValue().close();
            } catch (final RuntimeException e) {
                LOGGER.error("Failed to close database '{}'", entry.getKey(), e);
            }
        }
        databases.clear();
    }

    @Override
    public void close() {
        closeAll();
    }
}
