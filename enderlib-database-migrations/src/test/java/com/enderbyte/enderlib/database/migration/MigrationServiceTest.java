package com.enderbyte.enderlib.database.migration;

import com.enderbyte.enderlib.database.api.Database;
import com.enderbyte.enderlib.database.api.config.DatabaseType;
import com.enderbyte.enderlib.database.api.exception.MigrationException;
import com.enderbyte.enderlib.database.api.migration.MigrationResult;
import com.enderbyte.enderlib.database.api.migration.MigrationSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationServiceTest {

    private static final MigrationSource TWO_MIGRATIONS = MigrationSources.of(
            new SqlMigration(1, "create players",
                    "CREATE TABLE players (uuid UUID PRIMARY KEY, name VARCHAR(16) NOT NULL);"
                            + " CREATE INDEX idx_players_name ON players (name);"),
            new SqlMigration(2, "add balance",
                    "ALTER TABLE players ADD COLUMN balance BIGINT NOT NULL DEFAULT 0"));

    private String jdbcName;
    private Database database;

    @BeforeEach
    void setUp() {
        jdbcName = "mig-" + UUID.randomUUID();
        database = open();
    }

    @AfterEach
    void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    private Database open() {
        return Database.builder()
                .type(DatabaseType.H2)
                .name("migrations")
                .database("mem:" + jdbcName + ";DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .build()
                .connect();
    }

    @Test
    void appliesPendingMigrationsInOrder() {
        final MigrationResult result = database.migrations()
                .source(TWO_MIGRATIONS)
                .migrateAndWait();

        assertEquals(2, result.appliedCount());
        assertEquals(0, result.initialVersion());
        assertEquals(2, result.currentVersion());
        assertEquals(List.of(1, 2), result.applied().stream().map(a -> a.version()).toList());

        database.executeAsync("INSERT INTO players (uuid, name) VALUES (?, ?)",
                UUID.randomUUID(), "Notch").join();
    }

    @Test
    void isIdempotent() {
        database.migrations().source(TWO_MIGRATIONS).migrateAndWait();

        final MigrationResult second = database.migrations()
                .source(TWO_MIGRATIONS)
                .migrateAndWait();

        assertTrue(second.isUpToDate());
        assertEquals(2, second.currentVersion());
    }

    @Test
    void reportsPendingAndCurrentVersion() {
        assertEquals(2, database.migrations().source(TWO_MIGRATIONS).pending().join().size());
        database.migrations().source(TWO_MIGRATIONS).migrateAndWait();
        assertEquals(0, database.migrations().source(TWO_MIGRATIONS).pending().join().size());
        assertEquals(2, database.migrations().source(TWO_MIGRATIONS).currentVersion().join());
    }

    @Test
    void rejectsEditedMigrations() {
        database.migrations().source(TWO_MIGRATIONS).migrateAndWait();

        final MigrationSource edited = MigrationSources.of(
                new SqlMigration(1, "create players",
                        "CREATE TABLE players (uuid UUID PRIMARY KEY, name VARCHAR(32) NOT NULL)"));

        final CompletionException error = assertThrows(CompletionException.class,
                () -> database.migrations().source(edited).migrateAndWait());
        final MigrationException cause = assertInstanceOf(MigrationException.class, error.getCause());
        assertTrue(cause.getMessage().contains("has been modified since it was applied"));
    }

    @Test
    void rejectsDuplicateVersions() {
        final MigrationSource duplicates = MigrationSources.of(
                new SqlMigration(1, "first", "CREATE TABLE a (id INT PRIMARY KEY)"),
                new SqlMigration(1, "second", "CREATE TABLE b (id INT PRIMARY KEY)"));

        final CompletionException error = assertThrows(CompletionException.class,
                () -> database.migrations().source(duplicates).migrateAndWait());
        assertTrue(error.getCause().getMessage().contains("share version 1"));
    }

    @Test
    void failedMigrationBlocksStartupUntilRepaired() {
        final MigrationSource broken = MigrationSources.of(
                new SqlMigration(1, "create players",
                        "CREATE TABLE players (uuid UUID PRIMARY KEY)"),
                new SqlMigration(2, "broken", "THIS IS NOT SQL"));

        assertThrows(CompletionException.class,
                () -> database.migrations().source(broken).migrateAndWait());

        // H2 has transactional DDL, so the failure left no history row and the run simply retries.
        assertEquals(1, database.migrations().source(broken).currentVersion().join());
    }

    @Test
    void honoursBaselineVersion() {
        // Pretend the schema was created by hand before the plugin adopted migrations.
        database.executeAsync("CREATE TABLE players (uuid UUID PRIMARY KEY, name VARCHAR(16))").join();

        final MigrationResult result = database.migrations()
                .source(TWO_MIGRATIONS)
                .baselineVersion(1)
                .migrateAndWait();

        assertEquals(1, result.appliedCount(), "only V2 should run");
        assertEquals(2, result.currentVersion());
    }

    @Test
    void onlyOneOfManyServersAppliesTheMigrations() throws Exception {
        // Eight servers booting against the same database at the same time.
        final List<Database> servers = new ArrayList<>();
        try {
            final List<CompletableFuture<MigrationResult>> runs = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                final Database server = open();
                servers.add(server);
                runs.add(server.migrations().source(TWO_MIGRATIONS).serverId("server-" + i).migrate());
            }
            CompletableFuture.allOf(runs.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);

            final long migrated = runs.stream()
                    .map(CompletableFuture::join)
                    .filter(result -> result.appliedCount() > 0)
                    .count();
            assertEquals(1L, migrated, "exactly one server must apply the migrations");
            for (final CompletableFuture<MigrationResult> run : runs) {
                assertEquals(2, run.join().currentVersion());
            }
        } finally {
            servers.forEach(Database::close);
        }
    }

    @Test
    void splitsScriptsWithoutBreakingLiterals() {
        final List<String> statements = SqlMigration.split(
                "INSERT INTO t VALUES ('a;b'); -- a comment; still a comment\n"
                        + "/* block; comment */ UPDATE t SET x = 1;");

        assertEquals(2, statements.size());
        assertEquals("INSERT INTO t VALUES ('a;b')", statements.get(0));
        assertTrue(statements.get(1).endsWith("UPDATE t SET x = 1"));
    }
}
