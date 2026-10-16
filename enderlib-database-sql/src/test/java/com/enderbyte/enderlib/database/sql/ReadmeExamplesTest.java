package com.enderbyte.enderlib.database.sql;

import com.enderbyte.enderlib.database.api.Database;
import com.enderbyte.enderlib.database.api.config.DatabaseType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Executes the snippets that remain in README.md.
 *
 * <p>Documentation that no longer compiles is worse than no documentation. Cookbook and API pages
 * are covered by {@link DocsExamplesTest}.
 */
class ReadmeExamplesTest {

    private Database database;

    @BeforeEach
    void setUp() {
        database = Database.builder()
                .type(DatabaseType.H2)
                .name("players")
                .database("mem:readme-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .poolSize(10)
                .build()
                .connect();

        database.executeAsync("CREATE TABLE players ("
                + "uuid UUID PRIMARY KEY, name VARCHAR(16) NOT NULL, balance BIGINT NOT NULL)").join();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void queryFromTheQuickStart() {
        final UUID uuid = UUID.randomUUID();
        database.executeAsync(
                "INSERT INTO players (uuid, name, balance) VALUES (?, ?, ?)",
                uuid, "Steve", 50L).join();

        final AtomicReference<String> logged = new AtomicReference<>();
        database.queryAsync("SELECT name, balance FROM players WHERE uuid = ?", uuid)
                .thenAccept(result -> result.first().ifPresent(row ->
                        logged.set(row.getString("name") + " has " + row.getLong("balance"))))
                .join();

        assertEquals("Steve has 50", logged.get());
        assertTrue(database.isConnected());
    }
}
