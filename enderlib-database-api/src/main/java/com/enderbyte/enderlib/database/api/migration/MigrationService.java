package com.enderbyte.enderlib.database.api.migration;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Applies versioned schema changes.
 *
 * <pre>{@code
 * // during onEnable, before anything else touches the schema
 * database.migrations()
 *         .source(MigrationSources.classpath("migrations"))
 *         .migrate()
 *         .join();
 * }</pre>
 *
 * <p><strong>Multi-server safety.</strong> Fifty servers may start at the same time and all see the
 * same pending migration. The service takes a lock row in the history table before doing anything,
 * so exactly one server migrates and the rest wait and then continue with an up-to-date schema.
 */
public interface MigrationService {

    /**
     * Sets where migrations come from.
     *
     * @param source the source, typically a classpath folder
     * @return this service
     */
    MigrationService source(MigrationSource source);

    /**
     * Overrides the history table name. Default {@code schema_migrations}.
     *
     * <p>Give each plugin its own table when several plugins share one database, otherwise their
     * version numbers collide.
     *
     * @param table table name, validated as an identifier
     * @return this service
     */
    MigrationService historyTable(String table);

    /**
     * Identifies this server in the history table, which makes "who applied what" answerable on a
     * network.
     *
     * @param serverId short server identifier
     * @return this service
     */
    MigrationService serverId(String serverId);

    /**
     * Records an existing, unmanaged schema as already being at this version, so migrations up to
     * and including it are never applied.
     *
     * @param version baseline version
     * @return this service
     */
    MigrationService baselineVersion(int version);

    /**
     * Applies every pending migration.
     *
     * @return the outcome; the future fails with
     *         {@link com.enderbyte.enderlib.database.api.exception.MigrationException} on a bad checksum,
     *         a duplicate version or a failing script
     */
    CompletableFuture<MigrationResult> migrate();

    /**
     * Applies every pending migration and waits.
     *
     * <p>Safe and usual during plugin startup: the server is not yet accepting players, and a
     * half-migrated schema is worse than a slightly slower boot. Never call it once players are
     * online.
     *
     * @return the outcome
     */
    MigrationResult migrateAndWait();

    /** @return migrations that have not been applied yet, in version order */
    CompletableFuture<List<Migration>> pending();

    /** @return the migration history, in version order */
    CompletableFuture<List<AppliedMigration>> applied();

    /**
     * Verifies that every applied migration still matches its checksum, without changing anything.
     *
     * @return the future fails if the schema and the migration files have diverged
     */
    CompletableFuture<Void> validate();

    /**
     * Clears failed entries from the history table so a fixed migration can be retried.
     *
     * @return the number of entries removed
     */
    CompletableFuture<Integer> repair();

    /** @return the highest successfully applied version, or {@code 0} */
    CompletableFuture<Integer> currentVersion();
}
