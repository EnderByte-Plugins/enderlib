package com.enderbyte.enderlib.database.api.migration;

import java.util.List;

/**
 * Supplies the migrations to apply.
 *
 * <p>The usual source is the plugin's own jar: {@code migrations/V1__create_players.sql} and so on.
 * Loading from the plugin's data folder is supported too, which lets a server owner add a change
 * without rebuilding the jar.
 */
@FunctionalInterface
public interface MigrationSource {

    /**
     * @return every discovered migration; ordering is handled by the service
     * @throws com.enderbyte.enderlib.database.api.exception.MigrationException if a resource cannot be read
     *         or a file name does not follow the {@code V<version>__<description>.sql} convention
     */
    List<Migration> load();
}
