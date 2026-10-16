package com.enderbyte.enderlib.database.migration;

import com.enderbyte.enderlib.database.api.Database;
import com.enderbyte.enderlib.database.api.migration.MigrationService;
import com.enderbyte.enderlib.database.api.migration.MigrationServiceFactory;

/**
 * Makes {@link DefaultMigrationService} discoverable, so {@code database.migrations()} works as soon
 * as this module is on the classpath and fails with a clear message when it is not.
 */
public final class DefaultMigrationServiceFactory implements MigrationServiceFactory {

    @Override
    public MigrationService create(final Database database) {
        return new DefaultMigrationService(database);
    }
}
