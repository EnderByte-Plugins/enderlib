package com.enderbyte.enderlib.database.api.migration;

import com.enderbyte.enderlib.database.api.Database;

/**
 * Service-provider interface that supplies a {@link MigrationService} for a database.
 *
 * <p>This indirection is what lets {@code database.migrations()} exist on the main API while the
 * implementation lives in the optional {@code enderlib-database-migrations} module: a plugin that does not
 * ship that module simply never calls the method, and never carries the code.
 *
 * <p>Discovered with {@link java.util.ServiceLoader}.
 */
public interface MigrationServiceFactory {

    /**
     * @param database the database to migrate
     * @return a migration service bound to it
     */
    MigrationService create(Database database);
}
