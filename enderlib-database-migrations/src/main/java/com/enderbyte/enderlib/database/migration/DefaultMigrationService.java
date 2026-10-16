package com.enderbyte.enderlib.database.migration;

import com.enderbyte.enderlib.database.api.Database;
import com.enderbyte.enderlib.database.api.DatabaseConnection;
import com.enderbyte.enderlib.database.api.exception.MigrationException;
import com.enderbyte.enderlib.database.api.migration.AppliedMigration;
import com.enderbyte.enderlib.database.api.migration.Migration;
import com.enderbyte.enderlib.database.api.migration.MigrationResult;
import com.enderbyte.enderlib.database.api.migration.MigrationService;
import com.enderbyte.enderlib.database.api.migration.MigrationSource;
import com.enderbyte.enderlib.database.api.query.Query;
import com.enderbyte.enderlib.database.api.query.SqlDialect;
import com.enderbyte.enderlib.database.api.result.Row;
import com.enderbyte.enderlib.database.api.schema.ColumnDefinition;
import com.enderbyte.enderlib.database.api.schema.Schema;
import com.enderbyte.enderlib.database.api.schema.SqlType;
import com.enderbyte.enderlib.database.api.util.Identifiers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * The default {@link MigrationService}: versioned SQL migrations with checksums, a history table and
 * a lock that makes concurrent server startups safe.
 *
 * <p><strong>How the lock works.</strong> The history table doubles as the lock table: acquiring the
 * lock means inserting a reserved row, and the primary key makes exactly one server win. The losers
 * poll until the row disappears and then re-read the history, by which point the schema is current.
 * This needs no engine-specific advisory lock, so it behaves identically on all five engines.
 *
 * <p>A lock older than the configured timeout is treated as abandoned — a server that crashed
 * mid-migration must not block the whole network forever.
 */
final class DefaultMigrationService implements MigrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultMigrationService.class);

    /** Reserved version of the lock row. Real migrations start at 1. */
    private static final int LOCK_VERSION = -1;
    private static final String LOCK_DESCRIPTION = "migration lock";
    private static final Duration LOCK_POLL_INTERVAL = Duration.ofMillis(500);

    private final Database database;
    private final SqlDialect dialect;

    private volatile MigrationSource source;
    private volatile String historyTable = "schema_migrations";
    private volatile String serverId = defaultServerId();
    private volatile int baselineVersion;
    private volatile Duration lockTimeout = Duration.ofMinutes(5);

    DefaultMigrationService(final Database database) {
        this.database = database;
        this.dialect = database.dialect();
    }

    @Override
    public MigrationService source(final MigrationSource source) {
        this.source = Objects.requireNonNull(source, "source");
        return this;
    }

    @Override
    public MigrationService historyTable(final String table) {
        this.historyTable = Identifiers.requireValid(table, "historyTable");
        return this;
    }

    @Override
    public MigrationService serverId(final String serverId) {
        this.serverId = Objects.requireNonNull(serverId, "serverId");
        return this;
    }

    @Override
    public MigrationService baselineVersion(final int version) {
        this.baselineVersion = version;
        return this;
    }

    /**
     * Overrides how long a lock may be held before it is considered abandoned. Default five minutes.
     *
     * @param timeout the timeout
     * @return this service
     */
    MigrationService lockTimeout(final Duration timeout) {
        this.lockTimeout = timeout;
        return this;
    }

    @Override
    public CompletableFuture<MigrationResult> migrate() {
        return database.withConnectionAsync(this::runMigration);
    }

    @Override
    public MigrationResult migrateAndWait() {
        return migrate().join();
    }

    @Override
    public CompletableFuture<List<Migration>> pending() {
        return database.withConnectionAsync(connection -> {
            ensureHistoryTable(connection);
            final Map<Integer, AppliedMigration> history = readHistory(connection);
            final List<Migration> pending = new ArrayList<>();
            for (final Migration migration : sortedMigrations()) {
                if (migration.version() > baselineVersion && !isApplied(history, migration)) {
                    pending.add(migration);
                }
            }
            return pending;
        });
    }

    @Override
    public CompletableFuture<List<AppliedMigration>> applied() {
        return database.withConnectionAsync(connection -> {
            ensureHistoryTable(connection);
            return List.copyOf(readHistory(connection).values());
        });
    }

    @Override
    public CompletableFuture<Void> validate() {
        return database.withConnectionAsync(connection -> {
            ensureHistoryTable(connection);
            verifyChecksums(readHistory(connection), sortedMigrations());
            return null;
        });
    }

    @Override
    public CompletableFuture<Integer> repair() {
        return database.withConnectionAsync(connection -> {
            ensureHistoryTable(connection);
            final int removed = connection.execute(Query.deleteFrom(historyTable)
                    .where("success", false)
                    .and("version", com.enderbyte.enderlib.database.api.query.Operator.NOT_EQUALS, LOCK_VERSION));
            if (removed > 0) {
                LOGGER.info("Removed {} failed migration entries from '{}'", removed, historyTable);
            }
            return removed;
        });
    }

    @Override
    public CompletableFuture<Integer> currentVersion() {
        return database.withConnectionAsync(connection -> {
            ensureHistoryTable(connection);
            return highestVersion(readHistory(connection));
        });
    }

    // -------------------------------------------------------------------------- the actual run

    private MigrationResult runMigration(final DatabaseConnection connection) {
        final long startedAt = System.nanoTime();
        ensureHistoryTable(connection);

        final List<Migration> all = sortedMigrations();
        final int initialVersion = highestVersion(readHistory(connection));

        if (!acquireLock(connection)) {
            // Another server is migrating. Wait it out, then report the schema it produced.
            awaitLockRelease(connection);
            final int version = highestVersion(readHistory(connection));
            LOGGER.info("Another server applied the migrations for '{}'; schema is at version {}",
                    database.name(), version);
            return new MigrationResult(List.of(), initialVersion, version,
                    Duration.ofNanos(System.nanoTime() - startedAt), true);
        }

        final List<AppliedMigration> applied = new ArrayList<>();
        try {
            // Re-read inside the lock: the history may have changed while we were acquiring it.
            final Map<Integer, AppliedMigration> history = readHistory(connection);
            verifyChecksums(history, all);
            failIfPreviousRunFailed(history);

            for (final Migration migration : all) {
                if (migration.version() <= baselineVersion || isApplied(history, migration)) {
                    continue;
                }
                applied.add(apply(connection, migration));
            }
        } finally {
            releaseLock(connection);
        }

        final int currentVersion = highestVersion(readHistory(connection));
        if (applied.isEmpty()) {
            LOGGER.info("Database '{}' schema is up to date at version {}", database.name(), currentVersion);
        } else {
            LOGGER.info("Applied {} migration(s) to '{}': version {} -> {}",
                    applied.size(), database.name(), initialVersion, currentVersion);
        }
        return new MigrationResult(applied, initialVersion, currentVersion,
                Duration.ofNanos(System.nanoTime() - startedAt), false);
    }

    /**
     * Applies one migration and records the outcome.
     *
     * <p>Where the engine supports transactional DDL the change and its history row commit together,
     * so a migration is never half-applied. MySQL and MariaDB commit DDL implicitly; there the
     * failure is recorded as a failed history row instead, which blocks startup until an operator
     * repairs it. That is the honest behaviour: the alternative is pretending a partial migration
     * succeeded.
     */
    private AppliedMigration apply(final DatabaseConnection connection, final Migration migration) {
        LOGGER.info("Applying migration V{} ({}) to '{}'",
                migration.version(), migration.description(), database.name());
        final long startedAt = System.currentTimeMillis();
        final boolean transactional = dialect.supportsTransactionalDdl();
        boolean autoCommitChanged = false;

        try {
            if (transactional && connection.jdbc().getAutoCommit()) {
                connection.jdbc().setAutoCommit(false);
                autoCommitChanged = true;
            }
            migration.apply(connection);
            final AppliedMigration record = new AppliedMigration(
                    migration.version(), migration.description(), migration.checksum(),
                    Instant.now(), System.currentTimeMillis() - startedAt, true, serverId);
            insertHistory(connection, record);
            if (autoCommitChanged) {
                connection.jdbc().commit();
            }
            return record;
        } catch (final Exception e) {
            if (autoCommitChanged) {
                rollbackQuietly(connection);
            } else {
                recordFailure(connection, migration, System.currentTimeMillis() - startedAt);
            }
            throw new MigrationException("Migration V" + migration.version() + " ("
                    + migration.description() + ") failed on database '" + database.name()
                    + "'. The schema was not changed further; fix the migration and restart.", e);
        } finally {
            if (autoCommitChanged) {
                try {
                    connection.jdbc().setAutoCommit(true);
                } catch (final java.sql.SQLException e) {
                    LOGGER.warn("Could not restore auto-commit after migration V{}", migration.version(), e);
                }
            }
        }
    }

    private void recordFailure(final DatabaseConnection connection,
                               final Migration migration,
                               final long millis) {
        try {
            insertHistory(connection, new AppliedMigration(
                    migration.version(), migration.description(), migration.checksum(),
                    Instant.now(), millis, false, serverId));
        } catch (final RuntimeException e) {
            LOGGER.warn("Could not record the failure of migration V{}", migration.version(), e);
        }
    }

    private void rollbackQuietly(final DatabaseConnection connection) {
        try {
            connection.jdbc().rollback();
        } catch (final java.sql.SQLException e) {
            LOGGER.warn("Rollback of a failed migration also failed", e);
        }
    }

    // -------------------------------------------------------------------------------- the lock

    /**
     * Takes the migration lock by inserting the reserved row.
     *
     * @return {@code true} if this server holds the lock, {@code false} if another server does
     */
    private boolean acquireLock(final DatabaseConnection connection) {
        final long deadline = System.nanoTime() + lockTimeout.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                insertHistory(connection, new AppliedMigration(
                        LOCK_VERSION, LOCK_DESCRIPTION, "", Instant.now(), 0L, false, serverId));
                return true;
            } catch (final RuntimeException duplicateKey) {
                // The primary key rejected the insert, so someone else holds the lock. Take it over
                // only when it is provably abandoned.
                if (!breakStaleLock(connection)) {
                    return false;
                }
            }
        }
        return false;
    }

    private boolean breakStaleLock(final DatabaseConnection connection) {
        final Instant heldSince = lockHeldSince(connection);
        if (heldSince == null) {
            // Released between our insert failing and this read: retry immediately.
            return true;
        }
        if (heldSince.isBefore(Instant.now().minus(lockTimeout))) {
            LOGGER.warn("Migration lock on '{}' has been held since {}, longer than {}."
                            + " Assuming the holder crashed and taking over.",
                    historyTable, heldSince, lockTimeout);
            releaseLock(connection);
            return true;
        }
        return false;
    }

    private Instant lockHeldSince(final DatabaseConnection connection) {
        final Row row = connection.query(Query.selectFrom(historyTable)
                        .where("version", LOCK_VERSION))
                .firstOrNull();
        return row == null ? null : row.getInstant("applied_at");
    }

    private void awaitLockRelease(final DatabaseConnection connection) {
        final long deadline = System.nanoTime() + lockTimeout.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                Thread.sleep(LOCK_POLL_INTERVAL.toMillis());
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new MigrationException("Interrupted while waiting for another server"
                        + " to finish migrating '" + database.name() + '\'');
            }
            if (lockHeldSince(connection) == null) {
                return;
            }
        }
        throw new MigrationException("Timed out after " + lockTimeout + " waiting for another server"
                + " to finish migrating '" + database.name() + "'. If no server is migrating, delete"
                + " the row with version " + LOCK_VERSION + " from '" + historyTable + "'.");
    }

    private void releaseLock(final DatabaseConnection connection) {
        try {
            connection.execute(Query.deleteFrom(historyTable).where("version", LOCK_VERSION));
        } catch (final RuntimeException e) {
            LOGGER.error("Could not release the migration lock on '{}'. Other servers will wait"
                    + " until it goes stale ({}).", historyTable, lockTimeout, e);
        }
    }

    // ----------------------------------------------------------------------------- the history

    private void ensureHistoryTable(final DatabaseConnection connection) {
        connection.execute(Schema.createTable(historyTable)
                .ifNotExists()
                .column(ColumnDefinition.of("version", SqlType.INTEGER).notNull().primaryKey())
                .column(ColumnDefinition.of("description", SqlType.VARCHAR).length(200).notNull())
                .column(ColumnDefinition.of("checksum", SqlType.VARCHAR).length(64).notNull())
                .column(ColumnDefinition.of("applied_at", SqlType.TIMESTAMP_WITH_TIMEZONE).notNull())
                .column(ColumnDefinition.of("execution_millis", SqlType.BIGINT).notNull())
                .column(ColumnDefinition.of("success", SqlType.BOOLEAN).notNull())
                .column(ColumnDefinition.of("applied_by", SqlType.VARCHAR).length(100).notNull()));
    }

    private Map<Integer, AppliedMigration> readHistory(final DatabaseConnection connection) {
        final Map<Integer, AppliedMigration> history = new HashMap<>();
        for (final Row row : connection.query(Query.selectFrom(historyTable)
                .where("version", com.enderbyte.enderlib.database.api.query.Operator.GREATER_THAN, LOCK_VERSION)
                .orderBy("version"))) {
            final AppliedMigration record = new AppliedMigration(
                    row.getInt("version"),
                    row.getString("description"),
                    row.getString("checksum"),
                    row.getInstant("applied_at"),
                    row.getLong("execution_millis"),
                    row.getBoolean("success"),
                    row.getString("applied_by"));
            history.put(record.version(), record);
        }
        return history;
    }

    private void insertHistory(final DatabaseConnection connection, final AppliedMigration record) {
        connection.execute(Query.insertInto(historyTable)
                .value("version", record.version())
                .value("description", record.description())
                .value("checksum", record.checksum())
                .value("applied_at", record.appliedAt())
                .value("execution_millis", record.executionMillis())
                .value("success", record.success())
                .value("applied_by", record.appliedBy()));
    }

    // ------------------------------------------------------------------------------ validation

    private List<Migration> sortedMigrations() {
        if (source == null) {
            throw new MigrationException("No migration source configured."
                    + " Call source(MigrationSources.classpath(\"migrations\")) first.");
        }
        final List<Migration> migrations = new ArrayList<>(source.load());
        migrations.sort(null);
        for (int i = 1; i < migrations.size(); i++) {
            final Migration previous = migrations.get(i - 1);
            final Migration current = migrations.get(i);
            if (previous.version() == current.version()) {
                throw new MigrationException("Two migrations share version " + current.version()
                        + " ('" + previous.description() + "' and '" + current.description()
                        + "'). Versions must be unique.");
            }
        }
        return migrations;
    }

    /**
     * Fails when an applied migration's content no longer matches what was recorded.
     *
     * <p>Editing an applied migration means some servers ran the old text and some would run the
     * new one, which is a schema drift that is very hard to diagnose later. Add a new migration
     * instead.
     */
    private void verifyChecksums(final Map<Integer, AppliedMigration> history,
                                 final List<Migration> migrations) {
        for (final Migration migration : migrations) {
            final AppliedMigration record = history.get(migration.version());
            if (record == null || !record.success()) {
                continue;
            }
            if (!record.checksum().equals(migration.checksum())) {
                throw new MigrationException("Migration V" + migration.version() + " ("
                        + migration.description() + ") has been modified since it was applied on "
                        + record.appliedAt() + " by '" + record.appliedBy() + "'."
                        + " Applied migrations must never be edited; add a new migration instead."
                        + " If the change was intentional and every server has the new schema,"
                        + " update the checksum in the '" + historyTable + "' table.");
            }
        }
    }

    private void failIfPreviousRunFailed(final Map<Integer, AppliedMigration> history) {
        for (final AppliedMigration record : history.values()) {
            if (!record.success()) {
                throw new MigrationException("Migration V" + record.version() + " ("
                        + record.description() + ") failed on " + record.appliedAt() + " and was"
                        + " never repaired. Fix the schema by hand, then call repair() or delete the"
                        + " failed row from '" + historyTable + "'.");
            }
        }
    }

    private boolean isApplied(final Map<Integer, AppliedMigration> history, final Migration migration) {
        final AppliedMigration record = history.get(migration.version());
        return record != null && record.success();
    }

    private int highestVersion(final Map<Integer, AppliedMigration> history) {
        int highest = baselineVersion;
        for (final AppliedMigration record : history.values()) {
            if (record.success() && record.version() > highest) {
                highest = record.version();
            }
        }
        return highest;
    }

    private static String defaultServerId() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (final java.net.UnknownHostException e) {
            return "unknown";
        }
    }
}
