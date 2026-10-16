package com.enderbyte.enderlib.database.sql;

import com.enderbyte.enderlib.database.api.Database;
import com.enderbyte.enderlib.database.api.config.DatabaseType;
import com.enderbyte.enderlib.database.api.mapping.Column;
import com.enderbyte.enderlib.database.api.mapping.Id;
import com.enderbyte.enderlib.database.api.mapping.Table;
import com.enderbyte.enderlib.database.api.query.Conditions;
import com.enderbyte.enderlib.database.api.query.Query;
import com.enderbyte.enderlib.database.api.repository.CrudRepository;
import com.enderbyte.enderlib.database.api.result.QueryResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage of the provider wiring against an in-memory H2 database: builder discovery,
 * async queries, entity mapping, transactions, batches and the repository.
 */
class SqlDatabaseSmokeTest {

    @Table("players")
    record PlayerData(@Id UUID uuid, String name, @Column("balance") long balance) {
    }

    private Database database;

    @BeforeEach
    void setUp() {
        database = Database.builder()
                .type(DatabaseType.H2)
                .name("smoke-" + ThreadLocalRandom.current().nextInt(100_000))
                .database("mem:smoke-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .build()
                .connect();

        database.executeAsync("CREATE TABLE players ("
                + "uuid UUID PRIMARY KEY, name VARCHAR(16) NOT NULL, balance BIGINT NOT NULL)").join();
    }

    @AfterEach
    void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    void connectsAndReportsHealth() {
        assertTrue(database.isConnected());
        assertTrue(database.pingAsync().join());
    }

    @Test
    void insertsAndMapsEntities() {
        final UUID uuid = UUID.randomUUID();
        database.insertAsync(Query.insertInto("players")
                .value("uuid", uuid)
                .value("name", "Notch")
                .value("balance", 100L)).join();

        final PlayerData player = database.queryFirstAsync(
                        Query.selectFrom("players").where("uuid", uuid), PlayerData.class)
                .join()
                .orElseThrow();

        assertEquals(uuid, player.uuid());
        assertEquals("Notch", player.name());
        assertEquals(100L, player.balance());
    }

    @Test
    void commitsAndRollsBackTransactions() {
        final UUID first = UUID.randomUUID();
        final UUID second = UUID.randomUUID();

        database.runTransactionAsync(transaction -> {
            transaction.execute("INSERT INTO players VALUES (?, ?, ?)", first, "Alex", 10L);
            transaction.execute("INSERT INTO players VALUES (?, ?, ?)", second, "Steve", 20L);
        }).join();
        assertEquals(2L, count());

        final CompletableFuture<Void> failed = database.runTransactionAsync(transaction -> {
            transaction.execute("DELETE FROM players");
            throw new IllegalStateException("boom");
        });
        assertTrue(failed.isCompletedExceptionally() || failed.handle((v, e) -> e).join() != null);
        assertEquals(2L, count(), "a failed transaction must leave no trace");
    }

    @Test
    void batchesInserts() {
        final List<List<Object>> rows = List.of(
                List.of(UUID.randomUUID(), "One", 1L),
                List.of(UUID.randomUUID(), "Two", 2L),
                List.of(UUID.randomUUID(), "Three", 3L));

        final int[] counts = database.batchInsertAsync(
                "players", List.of("uuid", "name", "balance"), rows).join();

        assertEquals(3, counts.length);
        assertEquals(3L, count());
    }

    @Test
    void supportsRepositories() {
        final CrudRepository<PlayerData, UUID> repository =
                database.repository(PlayerData.class, UUID.class);
        final UUID uuid = UUID.randomUUID();

        repository.save(new PlayerData(uuid, "Herobrine", 42L)).join();
        assertTrue(repository.existsById(uuid).join());
        assertEquals(1L, repository.count().join());

        // Same key again: an upsert, not a duplicate-key failure.
        repository.save(new PlayerData(uuid, "Herobrine", 99L)).join();
        assertEquals(99L, repository.findById(uuid).join().orElseThrow().balance());

        assertEquals(1, repository.find(Conditions.equal("name", "Herobrine")).join().size());
        assertTrue(repository.deleteById(uuid).join());
        assertFalse(repository.findById(uuid).join().isPresent());
    }

    @Test
    void runsConcurrentWorkWithoutLeakingConnections() throws Exception {
        final List<CompletableFuture<Integer>> writes = java.util.stream.IntStream.range(0, 200)
                .mapToObj(i -> database.executeAsync(
                        "INSERT INTO players VALUES (?, ?, ?)", UUID.randomUUID(), "P" + i, (long) i))
                .toList();
        CompletableFuture.allOf(writes.toArray(new CompletableFuture[0]))
                .get(30, TimeUnit.SECONDS);

        assertEquals(200L, count());
        assertEquals(0, database.metrics().snapshot().pool().activeConnections(),
                "every connection must be back in the pool");
    }

    private long count() {
        final QueryResult result = database.queryAsync("SELECT COUNT(*) FROM players").join();
        return result.value(Long.class).orElseThrow();
    }
}
