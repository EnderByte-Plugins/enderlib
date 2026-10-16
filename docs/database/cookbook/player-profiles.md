# Player profiles

Load, save and cache a player row for the time they are online on this server.

## Schema

```sql
-- V1__create_players.sql
CREATE TABLE players (
    uuid      UUID PRIMARY KEY,
    name      VARCHAR(16) NOT NULL,
    balance   BIGINT NOT NULL DEFAULT 0,
    last_seen TIMESTAMP WITH TIME ZONE,
    world     VARCHAR(64)
);
```

Or, in tests and first-run bootstrapping:

```java
database.executeAsync(Schema.createTable("players")
        .ifNotExists()
        .column(ColumnDefinition.of("uuid", SqlType.UUID).primaryKey())
        .column(ColumnDefinition.of("name", SqlType.VARCHAR).length(16).notNull())
        .column(ColumnDefinition.of("balance", SqlType.BIGINT).notNull().defaultLiteral(0))
        .column(ColumnDefinition.of("last_seen", SqlType.TIMESTAMP_WITH_TIMEZONE))
        .column(ColumnDefinition.of("world", SqlType.VARCHAR).length(64))).join();
```

## Entity

```java
@Table("players")
public record PlayerData(
        @Id UUID uuid,
        String name,
        long balance,
        @Column("last_seen") Instant lastSeen,
        String world) {
}
```

## Repository

```java
public final class PlayerRepository extends SqlRepository<PlayerData, UUID> {

    public PlayerRepository(Database database) {
        super(database, PlayerData.class, UUID.class);
    }

    public CompletableFuture<Optional<PlayerData>> findByName(String name) {
        return findOne(Conditions.equal("name", name));
    }

    public CompletableFuture<List<PlayerData>> inWorld(String world) {
        return find(Conditions.equal("world", world));
    }

    public CompletableFuture<PlayerData> loadOrCreate(UUID uuid, String name) {
        return findById(uuid).thenCompose(found -> {
            if (found.isPresent()) {
                return CompletableFuture.completedFuture(found.get());
            }
            PlayerData created = new PlayerData(uuid, name, 0L, Instant.now(), "world");
            return save(created).thenApply(ignored -> created);
        });
    }
}
```

`database.repository(PlayerData.class, UUID.class)` is enough when you do not need extra lookups.

## Join load

On join, upsert the name and timestamp, then cache:

```java
Cache<UUID, PlayerData> online = database.cache("online-players", UUID.class, PlayerData.class)
        .maximumSize(10_000)
        .expireAfterAccess(Duration.ofHours(6))
        .recordStats(true)
        .build();

public void onJoin(UUID uuid, String name) {
    database.insertAsync(Query.insertInto("players")
                    .value("uuid", uuid)
                    .value("name", name)
                    .value("balance", 0L)
                    .value("last_seen", Instant.now())
                    .onConflictUpdate(List.of("uuid"), List.of("name", "last_seen")))
            .thenCompose(ignored -> players.findById(uuid))
            .thenAccept(found -> found.ifPresent(data -> online.put(uuid, data)))
            .exceptionally(error -> {
                logger.log(Level.SEVERE, "Could not load player " + uuid, error);
                return null;
            });
}

public void onQuit(UUID uuid) {
    PlayerData cached = online.getIfPresent(uuid).orElse(null);
    online.invalidate(uuid);
    if (cached == null) {
        return;
    }
    players.save(new PlayerData(cached.uuid(), cached.name(), cached.balance(),
            Instant.now(), cached.world()));
}
```

The cache is scoped to players currently online on **this** server. That is the safe boundary: you
own the session, you invalidate on quit, and another server cannot see a stale copy of a player who
is not here.

Lookup from a command (after hopping back to the server thread):

```java
online.get(uuid, id -> players.findById(id).thenApply(found -> found.orElse(null)))
        .thenAccept(data -> { /* hop back, then sendMessage */ });
```

See [Mapping](../api/mapping.md) and [Operations](../api/operations.md#caching).
