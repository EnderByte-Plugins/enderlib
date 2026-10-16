package com.enderbyte.enderlib.database.sql;

import com.enderbyte.enderlib.database.api.Database;
import com.enderbyte.enderlib.database.api.cache.Cache;
import com.enderbyte.enderlib.database.api.config.ConfigLoader;
import com.enderbyte.enderlib.database.api.config.DatabaseConfig;
import com.enderbyte.enderlib.database.api.config.DatabaseType;
import com.enderbyte.enderlib.database.api.config.ExecutorConfig;
import com.enderbyte.enderlib.database.api.config.PoolConfig;
import com.enderbyte.enderlib.database.api.config.RetryPolicy;
import com.enderbyte.enderlib.database.api.config.TimeoutConfig;
import com.enderbyte.enderlib.database.api.exception.OptimisticLockException;
import com.enderbyte.enderlib.database.api.mapping.Column;
import com.enderbyte.enderlib.database.api.mapping.Id;
import com.enderbyte.enderlib.database.api.mapping.Table;
import com.enderbyte.enderlib.database.api.mapping.TypeConverter;
import com.enderbyte.enderlib.database.api.mapping.Version;
import com.enderbyte.enderlib.database.api.metrics.MetricsSnapshot;
import com.enderbyte.enderlib.database.api.query.Conditions;
import com.enderbyte.enderlib.database.api.query.Expressions;
import com.enderbyte.enderlib.database.api.query.JoinType;
import com.enderbyte.enderlib.database.api.query.Operator;
import com.enderbyte.enderlib.database.api.query.Query;
import com.enderbyte.enderlib.database.api.query.SortDirection;
import com.enderbyte.enderlib.database.api.repository.CrudRepository;
import com.enderbyte.enderlib.database.api.result.QueryResult;
import com.enderbyte.enderlib.database.api.schema.ColumnDefinition;
import com.enderbyte.enderlib.database.api.schema.Schema;
import com.enderbyte.enderlib.database.api.schema.SqlType;
import com.enderbyte.enderlib.database.api.transaction.IsolationLevel;
import com.enderbyte.enderlib.database.api.transaction.TransactionOptions;
import com.enderbyte.enderlib.database.core.DatabaseRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static com.enderbyte.enderlib.database.api.query.Conditions.and;
import static com.enderbyte.enderlib.database.api.query.Conditions.equal;
import static com.enderbyte.enderlib.database.api.query.Conditions.greaterThan;
import static com.enderbyte.enderlib.database.api.query.Conditions.or;
import static com.enderbyte.enderlib.database.api.query.Conditions.raw;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Executes the H2-runnable snippets from {@code docs/}.
 *
 * <p>Bukkit-only fragments stay in markdown. Everything else that claims to be copy-pasteable is
 * compiled and run here, so the build fails when a page drifts from the API.
 */
class DocsExamplesTest {

    @Table("players")
    record PlayerData(@Id UUID uuid,
                      String name,
                      long balance,
                      @Column("last_seen") Instant lastSeen,
                      String world) {
    }

    @Table("homes")
    record Home(@Id(generated = true) long id, UUID owner, String name) {
    }

    @Table("homes")
    record LocatedHome(@Id UUID id, UUID owner, String name, HomeLocation location) {
    }

    record HomeLocation(String world, double x, double y, double z) {
    }

    @Table("players")
    static final class VersionedPlayer {

        @Id
        private UUID uuid;
        private String name;
        private long balance;
        @Version
        private long version;

        VersionedPlayer() {
        }

        VersionedPlayer(final UUID uuid, final String name, final long balance, final long version) {
            this.uuid = uuid;
            this.name = name;
            this.balance = balance;
            this.version = version;
        }

        UUID uuid() {
            return uuid;
        }

        String name() {
            return name;
        }

        long balance() {
            return balance;
        }

        long version() {
            return version;
        }

        VersionedPlayer withBalance(final long newBalance) {
            return new VersionedPlayer(uuid, name, newBalance, version);
        }
    }

    public static final class HomeLocationConverter implements TypeConverter<HomeLocation> {

        @Override
        public Class<HomeLocation> javaType() {
            return HomeLocation.class;
        }

        @Override
        public Object toDatabase(final HomeLocation value) {
            if (value == null) {
                return null;
            }
            return value.world() + "," + value.x() + "," + value.y() + "," + value.z();
        }

        @Override
        public HomeLocation fromDatabase(final Object value) {
            if (value == null) {
                return null;
            }
            final String[] parts = value.toString().split(",", 4);
            return new HomeLocation(
                    parts[0],
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]),
                    Double.parseDouble(parts[3]));
        }
    }

    public static final class PlayerRepository extends SqlRepository<PlayerData, UUID> {

        public PlayerRepository(final Database database) {
            super(database, PlayerData.class, UUID.class);
        }

        public CompletableFuture<Optional<PlayerData>> findByName(final String name) {
            return findOne(Conditions.equal("name", name));
        }

        public CompletableFuture<List<PlayerData>> richest(final int limit) {
            return database().queryAsync(Query.selectFrom(table())
                    .orderBy("balance", SortDirection.DESCENDING)
                    .limit(limit), entityType());
        }
    }

    private Database database;

    @BeforeEach
    void setUp() {
        database = Database.builder()
                .type(DatabaseType.H2)
                .name("docs")
                .database("mem:docs-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .poolSize(10)
                .build()
                .connect();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void schemaHelpers() {
        database.executeAsync(Schema.createTable("players")
                .ifNotExists()
                .column(ColumnDefinition.of("uuid", SqlType.UUID).primaryKey())
                .column(ColumnDefinition.of("name", SqlType.VARCHAR).length(16).notNull())
                .column(ColumnDefinition.of("balance", SqlType.BIGINT).notNull().defaultLiteral(0))
                .column(ColumnDefinition.of("last_seen", SqlType.TIMESTAMP_WITH_TIMEZONE))
                .column(ColumnDefinition.of("world", SqlType.VARCHAR).length(64))).join();

        database.executeAsync(
                Schema.createIndex("idx_players_name").on("players").columns("name")).join();
        database.executeAsync(Schema.createIndex("idx_players_name_unique").on("players")
                .columns("name").unique()).join();
        database.executeAsync(Schema.addColumn("players",
                ColumnDefinition.of("notes", SqlType.VARCHAR).length(64))).join();
        database.executeAsync(Schema.dropColumn("players", "notes")).join();
        database.executeAsync(Schema.dropIndex("idx_players_name", "players")).join();
        database.executeAsync(Schema.dropTable("players")).join();
    }

    @Test
    void queriesAndBuilders() {
        createPlayers();
        final UUID uuid = UUID.randomUUID();

        database.insertAsync(Query.insertInto("players")
                .value("uuid", uuid)
                .value("name", "Notch")
                .value("balance", 100L)
                .value("world", "survival")).join();

        database.insertAsync(Query.insertInto("players")
                .values(Map.of("uuid", UUID.randomUUID(), "name", "Alex", "balance", 10L))).join();

        database.insertAsync(Query.insertInto("players")
                .value("uuid", uuid)
                .value("name", "Notch")
                .value("balance", 500L)
                .onConflictUpdateAll("uuid")).join();

        database.updateAsync(Query.update("players")
                .set("balance", 250L)
                .set("last_seen", Instant.now())
                .where("uuid", uuid)).join();

        database.updateAsync(Query.update("players")
                .set("balance", Expressions.raw("balance + ?", 5L))
                .where("uuid", uuid)).join();

        assertEquals(2, database.queryAsync(Query.select("uuid", "name", "balance")
                .from("players")
                .where("balance", Operator.GREATER_THAN, 0)
                .orderBy("balance", SortDirection.DESCENDING)
                .limit(10)
                .offset(0), PlayerData.class).join().size());

        assertEquals(2L, database.queryAsync(Query.selectCount("players"))
                .join()
                .value(Long.class)
                .orElseThrow());

        database.queryAsync(Query.select("world").distinct().from("players")).join();

        database.queryAsync(Query.selectFrom("players")
                .where(and(equal("world", "survival"),
                        or(greaterThan("balance", 1000), equal("name", "VIP"))))
                .whereIn("uuid", List.of(uuid))
                .whereBetween("balance", 10, 5000)
                .whereLike("name", "N%")
                .whereNotNull("name")
                .whereNull("last_seen")).join();

        database.queryAsync(Query.selectFrom("players")
                .whereIn("uuid", Query.select("uuid").from("players").where("name", "Notch"))
                .whereExists(Query.selectFrom("players").where("name", "Notch"))).join();

        database.queryAsync(Query.select(
                        Expressions.column("p.name"), Expressions.sum("s.balance").as("total"))
                .from("players", "p")
                .join(JoinType.LEFT, "players", "s", raw("s.uuid = p.uuid"))
                .groupBy("p.name")
                .having(raw("SUM(s.balance) > ?", 0))
                .orderBy("p.name")).join();

        database.queryAsync(Query.selectFrom("players")
                .where("balance", Operator.GREATER_THAN, 0)
                .limit(1)
                .forUpdateSkipLocked()).join();

        database.queryAsync(Query.raw(
                "SELECT * FROM players ORDER BY balance DESC FETCH FIRST ? ROWS ONLY", 10)).join();

        database.insertAsync(Query.insertInto("players")
                .columns("uuid", "name", "balance")
                .row(UUID.randomUUID(), "One", 1L)
                .row(UUID.randomUUID(), "Two", 2L)).join();

        database.updateAsync(Query.update("players")
                .set("world", "hub")
                .allowFullTableUpdate()).join();

        database.deleteAsync(Query.deleteFrom("players").where("uuid", uuid)).join();
        assertTrue(count() >= 1L);
        database.deleteAsync(Query.deleteFrom("players").allowFullTableDelete()).join();
        assertEquals(0L, count());
    }

    @Test
    void rowGetters() {
        createPlayers();
        final UUID uuid = UUID.randomUUID();
        database.executeAsync("INSERT INTO players (uuid, name, balance, last_seen)"
                + " VALUES (?, ?, ?, ?)", uuid, "Steve", 7L, Instant.now()).join();

        database.queryAsync("SELECT * FROM players").join().first().ifPresent(row -> {
            assertEquals("Steve", row.getString("name"));
            assertEquals(7, row.getInt("balance"));
            assertEquals(7L, row.getLong("balance"));
            assertEquals(uuid, row.getUuid("uuid"));
            assertNotNull(row.getInstant("last_seen"));
            assertTrue(row.optional("name", String.class).isPresent());
            assertTrue(row.hasColumn("balance"));
            assertFalse(row.isNull("name"));
            assertNotNull(row.asMap());
            assertNotNull(row.get(0));
        });

        assertEquals("Steve", database.queryAsync("SELECT * FROM players").join()
                .first(PlayerData.class).orElseThrow().name());
    }

    @Test
    void repositoriesAndTransactions() {
        createPlayers();
        final CrudRepository<PlayerData, UUID> players =
                database.repository(PlayerData.class, UUID.class);
        final UUID uuid = UUID.randomUUID();

        players.save(new PlayerData(uuid, "Alex", 10L, Instant.now(), "survival")).join();
        assertTrue(players.existsById(uuid).join());
        assertEquals(1L, players.count().join());
        assertEquals(1, players.find(Conditions.equal("name", "Alex")).join().size());
        assertEquals(1, players.find(Conditions.equal("name", "Alex"), 50, 0).join().size());
        assertEquals(1, players.findAllById(List.of(uuid)).join().size());
        assertTrue(players.findOne(Conditions.equal("name", "Alex")).join().isPresent());

        final PlayerRepository custom = new PlayerRepository(database);
        assertTrue(custom.findByName("Alex").join().isPresent());
        assertEquals(1, custom.richest(5).join().size());

        database.runTransactionAsync(tx -> {
            tx.execute("UPDATE players SET balance = balance - ? WHERE uuid = ?", 5L, uuid);
            tx.execute("UPDATE players SET balance = balance + ? WHERE uuid = ?", 5L, uuid);
        }).join();

        final long balance = database.transactionAsync(tx ->
                tx.query("SELECT balance FROM players WHERE uuid = ?", uuid)
                        .value(Long.class)
                        .orElse(0L)).join();
        assertEquals(10L, balance);

        database.transactionAsync(
                TransactionOptions.isolation(IsolationLevel.SERIALIZABLE)
                        .withTimeout(Duration.ofSeconds(5)),
                tx -> {
                    tx.savepoint("before_risky");
                    tx.execute("UPDATE players SET balance = 0 WHERE uuid = ?", uuid);
                    tx.rollbackTo("before_risky");
                    tx.releaseSavepoint("before_risky");
                    tx.setRollbackOnly();
                    return null;
                }).join();

        database.runTransactionAsync(TransactionOptions.readOnly(), tx ->
                tx.query("SELECT COUNT(*) FROM players")).join();

        assertEquals(10L, players.findById(uuid).join().orElseThrow().balance());
        assertTrue(players.deleteById(uuid).join());
    }

    @Test
    void economyTransferAndOptimisticLock() {
        createVersionedPlayers();
        final CrudRepository<VersionedPlayer, UUID> players =
                database.repository(VersionedPlayer.class, UUID.class);
        final UUID from = UUID.randomUUID();
        final UUID to = UUID.randomUUID();
        players.save(new VersionedPlayer(from, "From", 100L, 0L)).join();
        players.save(new VersionedPlayer(to, "To", 0L, 0L)).join();

        final boolean transferred = database.transactionAsync(tx -> {
            final int debit = tx.execute(Query.update("players")
                    .set("balance", Expressions.raw("balance - ?", 40L))
                    .where("uuid", from)
                    .and("balance", Operator.GREATER_THAN, 39L));
            if (debit == 0) {
                tx.setRollbackOnly();
                return false;
            }
            tx.execute(Query.update("players")
                    .set("balance", Expressions.raw("balance + ?", 40L))
                    .where("uuid", to));
            return true;
        }).join();
        assertTrue(transferred);

        final VersionedPlayer loaded = players.findById(from).join().orElseThrow();
        try {
            players.save(loaded.withBalance(1L)).join();
            players.save(loaded.withBalance(2L)).join();
        } catch (final CompletionException error) {
            assertInstanceOf(OptimisticLockException.class, error.getCause());
        }
    }

    @Test
    void batches() {
        createPlayers();
        final List<List<Object>> rows = List.of(
                List.of(UUID.randomUUID(), "One", 1L),
                List.of(UUID.randomUUID(), "Two", 2L));

        database.batchInsertAsync("players", List.of("uuid", "name", "balance"), rows).join();
        assertEquals(2L, count());

        database.batchAsync("UPDATE players SET balance = ? WHERE name = ?",
                List.of(List.of(9L, "One"), List.of(8L, "Two"))).join();
        database.batchUpdateAsync("UPDATE players SET world = ? WHERE name = ?",
                List.of(List.of("hub", "One"), List.of("hub", "Two"))).join();
    }

    @Test
    void caching() {
        createPlayers();
        final PlayerRepository repository = new PlayerRepository(database);
        final Cache<UUID, PlayerData> cache = database.cache("players", UUID.class, PlayerData.class)
                .maximumSize(100_000)
                .expireAfterWrite(Duration.ofMinutes(10))
                .expireAfterAccess(Duration.ofMinutes(30))
                .recordStats(true)
                .build();

        final UUID uuid = UUID.randomUUID();
        final PlayerData loaded = cache.get(uuid, id -> CompletableFuture.completedFuture(
                new PlayerData(id, "Cached", 1L, Instant.now(), "world"))).join();

        assertEquals("Cached", loaded.name());
        assertTrue(cache.getIfPresent(uuid).isPresent());
        cache.put(uuid, loaded);
        cache.invalidate(uuid);
        cache.invalidateAll();
        assertNotNull(cache.stats());
        assertNotNull(cache.asMap());
        assertTrue(database.caches().cache("players").isPresent());
        database.caches().invalidateAll();

        cache.get(uuid, id -> repository.findById(id).thenApply(found -> found.orElse(null))).join();
    }

    @Test
    void generatedKeys() {
        database.executeAsync(Schema.createTable("homes")
                .ifNotExists()
                .column(ColumnDefinition.of("id", SqlType.BIGINT).autoIncrement().primaryKey())
                .column(ColumnDefinition.of("owner", SqlType.UUID).notNull())
                .column(ColumnDefinition.of("name", SqlType.VARCHAR).length(32).notNull())).join();

        final UUID owner = UUID.randomUUID();
        final QueryResult keys = database.insertReturningKeysAsync(Query.insertInto("homes")
                .value("owner", owner)
                .value("name", "Spawn")).join();

        assertFalse(keys.generatedKeys().isEmpty());
        final long id = ((Number) keys.generatedKeys().get(0)).longValue();
        assertTrue(id > 0L);

        final long fromTx = database.transactionAsync(tx -> {
            final QueryResult inner = tx.executeReturningKeys(Query.insertInto("homes")
                    .value("owner", owner)
                    .value("name", "Mine"));
            return ((Number) inner.generatedKeys().get(0)).longValue();
        }).join();
        assertTrue(fromTx > 0L);
    }

    @Test
    void customTypes() {
        database.executeAsync(Schema.createTable("homes")
                .ifNotExists()
                .column(ColumnDefinition.of("id", SqlType.UUID).primaryKey())
                .column(ColumnDefinition.of("owner", SqlType.UUID).notNull())
                .column(ColumnDefinition.of("name", SqlType.VARCHAR).length(32).notNull())
                .column(ColumnDefinition.of("location", SqlType.VARCHAR).length(128))).join();

        database.converters().register(new HomeLocationConverter());

        final CrudRepository<LocatedHome, UUID> homes =
                database.repository(LocatedHome.class, UUID.class);
        final UUID id = UUID.randomUUID();
        final UUID owner = UUID.randomUUID();
        homes.save(new LocatedHome(id, owner, "Spawn",
                new HomeLocation("world", 0.5, 64.0, 0.5))).join();

        final LocatedHome loaded = homes.findById(id).join().orElseThrow();
        assertEquals("world", loaded.location().world());
        assertEquals(64.0, loaded.location().y());
    }

    @Test
    void workQueue() {
        database.executeAsync("CREATE TABLE pending_rewards ("
                + "id BIGINT PRIMARY KEY, player UUID NOT NULL, payload VARCHAR(128) NOT NULL,"
                + " claimed BOOLEAN NOT NULL DEFAULT FALSE, claimed_by VARCHAR(64))").join();
        database.executeAsync(
                "INSERT INTO pending_rewards (id, player, payload, claimed) VALUES (?, ?, ?, ?)",
                1L, UUID.randomUUID(), "diamond", false).join();

        final Optional<Long> claimed = database.transactionAsync(tx -> {
            final QueryResult result = tx.query(Query.selectFrom("pending_rewards")
                    .where("claimed", false)
                    .orderBy("id")
                    .limit(1)
                    .forUpdateSkipLocked());
            final var row = result.firstOrNull();
            if (row == null) {
                return Optional.<Long>empty();
            }
            final long id = row.getLong("id");
            tx.execute(Query.update("pending_rewards")
                    .set("claimed", true)
                    .set("claimed_by", "server-a")
                    .where("id", id));
            return Optional.of(id);
        }).join();

        assertEquals(Optional.of(1L), claimed);
    }

    @Test
    void healthAndMetrics() {
        createPlayers();
        assertTrue(database.isConnected());
        assertTrue(database.pingAsync().join());
        database.queryAsync("SELECT COUNT(*) FROM players").join();

        final MetricsSnapshot snapshot = database.metrics().snapshot();
        assertTrue(snapshot.queryCount() > 0L);
        assertEquals(0.0d, snapshot.errorRate());
        assertNotNull(snapshot.pool());
        assertTrue(snapshot.pool().utilization() >= 0.0d);
        assertTrue(snapshot.p50QueryNanos() >= 0L);
        assertTrue(snapshot.transactionCount() >= 0L);
    }

    @Test
    void withConnectionEscapeHatch() {
        createPlayers();
        final long counted = database.withConnectionAsync(connection ->
                connection.query("SELECT COUNT(*) FROM players").value(Long.class).orElse(0L)).join();
        assertEquals(0L, counted);
        assertNotNull(database.dialect());
        assertEquals(DatabaseType.H2, database.type());
    }

    @Test
    void fullConfiguration() {
        final DatabaseConfig config = Database.builder()
                .type(DatabaseType.H2)
                .name("configured")
                .database("mem:configured;DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .property("MODE", "REGULAR")
                .validateOnConnect(true)
                .pool(PoolConfig.builder()
                        .maximumPoolSize(20)
                        .minimumIdle(5)
                        .connectionTimeout(Duration.ofSeconds(5))
                        .idleTimeout(Duration.ofMinutes(10))
                        .maxLifetime(Duration.ofMinutes(30))
                        .leakDetectionThreshold(Duration.ofSeconds(30))
                        .build())
                .executor(ExecutorConfig.builder()
                        .coreSize(4)
                        .maximumSize(16)
                        .queueCapacity(10_000)
                        .shutdownTimeout(Duration.ofSeconds(10))
                        .build())
                .timeouts(TimeoutConfig.builder()
                        .query(Duration.ofSeconds(10))
                        .batch(Duration.ofSeconds(60))
                        .transaction(Duration.ofSeconds(30))
                        .connect(Duration.ofSeconds(30))
                        .build())
                .retryPolicy(RetryPolicy.exponentialBackoff()
                        .maxAttempts(5)
                        .initialDelay(Duration.ofMillis(100))
                        .maxDelay(Duration.ofSeconds(5))
                        .retryTransactions(true))
                .metricsEnabled(true)
                .cachingEnabled(true)
                .buildConfig();

        assertEquals(20, config.pool().maximumPoolSize());
        assertTrue(config.validateOnConnect());

        try (Database configured = Database.builder().config(config).build()) {
            assertEquals("configured", configured.name());
        }
    }

    @Test
    void configurationFromAMap() {
        final DatabaseConfig config = ConfigLoader.fromMap(Map.ofEntries(
                Map.entry("type", "h2"),
                Map.entry("name", "from-map"),
                Map.entry("database", "mem:from-map;DB_CLOSE_DELAY=-1"),
                Map.entry("username", "sa"),
                Map.entry("password", ""),
                Map.entry("pool.maximum-size", 20),
                Map.entry("pool.minimum-idle", 5),
                Map.entry("pool.keepalive-ms", 0),
                Map.entry("executor.core-size", 4),
                Map.entry("executor.maximum-size", 16),
                Map.entry("executor.queue-capacity", 10000),
                Map.entry("timeout.query-ms", 10_000),
                Map.entry("timeout.transaction-ms", 30_000),
                Map.entry("timeout.connect-ms", 30_000),
                Map.entry("retry.max-attempts", 5),
                Map.entry("retry.initial-delay-ms", 100),
                Map.entry("retry.max-delay-ms", 5_000),
                Map.entry("metrics.enabled", true),
                Map.entry("cache.enabled", true)));

        assertEquals(DatabaseType.H2, config.type());
        assertEquals(20, config.pool().maximumPoolSize());
        assertEquals(4, config.executor().coreSize());
        assertEquals(Duration.ofSeconds(30), config.timeouts().connect());
    }

    @Test
    void registryOfSeveralDatabases() {
        final DatabaseRegistry registry = new DatabaseRegistry();
        try {
            registry.register(database);
            assertEquals(database, registry.require("docs"));
            assertTrue(registry.get("docs").isPresent());
            assertEquals(1, registry.size());
            assertEquals(1, registry.all().size());
            assertTrue(registry.unregister("docs").isPresent());
            assertEquals(0, registry.size());
            registry.register(database);
        } finally {
            registry.closeAll();
        }
    }

    private void createPlayers() {
        database.executeAsync("CREATE TABLE players ("
                + "uuid UUID PRIMARY KEY, name VARCHAR(16) NOT NULL,"
                + " balance BIGINT NOT NULL DEFAULT 0, last_seen TIMESTAMP WITH TIME ZONE,"
                + " world VARCHAR(64))").join();
    }

    private void createVersionedPlayers() {
        database.executeAsync("CREATE TABLE players ("
                + "uuid UUID PRIMARY KEY, name VARCHAR(16) NOT NULL,"
                + " balance BIGINT NOT NULL DEFAULT 0, version BIGINT NOT NULL DEFAULT 0)").join();
    }

    private long count() {
        return database.queryAsync("SELECT COUNT(*) FROM players").join()
                .value(Long.class).orElseThrow();
    }
}
