package com.enderbyte.enderlib.database.api.migration;

import com.enderbyte.enderlib.database.api.DatabaseConnection;

/**
 * One versioned schema change.
 *
 * <p>Implementations come from {@code .sql} files on the classpath or on disk, but a plugin can
 * also supply a Java migration when a change needs logic — backfilling a column from an old format,
 * for instance.
 *
 * <p>A migration must be written so that it can run exactly once and never be edited afterwards:
 * the checksum is verified against the history table on every startup, and editing an applied
 * migration is a deployment error the library refuses to ignore.
 */
public interface Migration extends Comparable<Migration> {

    /** @return the version, unique and strictly increasing; taken from {@code V<version>__...} */
    int version();

    /** @return a short human description, taken from the file name after the double underscore */
    String description();

    /**
     * @return a stable checksum of the migration content, used to detect edits to already applied
     *         migrations
     */
    String checksum();

    /**
     * Applies the change.
     *
     * <p>Called on a database worker thread, inside a transaction where the engine supports
     * transactional DDL.
     *
     * @param connection the connection to run against
     * @throws Exception any failure; the migration is recorded as failed and startup aborts
     */
    void apply(DatabaseConnection connection) throws Exception;

    @Override
    default int compareTo(final Migration other) {
        return Integer.compare(version(), other.version());
    }
}
