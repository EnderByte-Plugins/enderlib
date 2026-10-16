# Mapping

Entities and repositories are optional. Raw SQL and the query builder stay first-class.

## Annotations

Fields map to `snake_case` columns automatically, so most entities need only `@Table` and `@Id`.
Records work, which keeps entities immutable:

```java
@Table("players")
public record PlayerData(
        @Id UUID uuid,
        String name,
        long balance,
        @Column("last_seen") Instant lastSeen) {
}
```

Classes work too, and need a no-argument constructor (it may be private):

```java
@Table(value = "players", schema = "public")
public class PlayerData {

    @Id
    private UUID uuid;

    private String name;                 // -> name
    private Instant lastSeenAt;          // -> last_seen_at

    @Column(value = "coins", updatable = false)
    private long balance;

    @Version
    private long version;                // optimistic locking

    @Transient
    private transient boolean dirty;     // never persisted

    PlayerData() {
    }
}
```

| Annotation | Purpose |
| --- | --- |
| `@Table` | Table name, plus an optional `schema` |
| `@Id` | Primary key; `@Id(generated = true)` for auto-increment columns |
| `@Column` | Explicit column name, or `insertable` / `updatable = false` |
| `@Version` | Optimistic locking counter |
| `@Transient` | Exclude the field entirely |

Composite keys are not supported.

Reflection runs once per class: field handles are resolved on first use and cached, so per-row
mapping is plain field access.

### Generated keys

```java
@Table("homes")
public record Home(
        @Id(generated = true) long id,
        UUID owner,
        String name) {
}
```

The generated field is omitted from inserts and populated from the keys the database returns. The
insert path is `insertReturningKeysAsync` when you are not going through a repository — see
[Generated keys](../cookbook/generated-keys.md).

## TypeConverter

Register a converter to support a type the library does not know about — a `Location`, an
`ItemStack` blob, a JSON document — without touching the mapper itself.

Each database owns one `ConverterRegistry`, pre-populated with String, primitives and their boxes,
`UUID`, enums, `Instant`, `LocalDate`, `LocalDateTime`, `byte[]` and `BigDecimal`. Plugins add
their own on top.

```java
database.converters().register(new TypeConverter<HomeLocation>() {
    @Override
    public Class<HomeLocation> javaType() {
        return HomeLocation.class;
    }

    @Override
    public Object toDatabase(HomeLocation value) {
        return value == null ? null : value.world() + "," + value.x() + "," + value.y() + "," + value.z();
    }

    @Override
    public HomeLocation fromDatabase(Object value) {
        if (value == null) {
            return null;
        }
        String[] parts = value.toString().split(",");
        return new HomeLocation(parts[0],
                Double.parseDouble(parts[1]),
                Double.parseDouble(parts[2]),
                Double.parseDouble(parts[3]));
    }
});
```

Implementations must be thread-safe and must tolerate `null` on both sides. `toDatabase` must
return something the JDBC driver accepts (String, Number, `byte[]`, a `java.sql` / `java.time`
type).

`ConverterRegistry` also exposes `toDatabase(Object)` and `fromDatabase(Object, Class)` for
one-off conversions, and `converter(Class)` to look one up.

A full recipe: [Custom types](../cookbook/custom-types.md).

## Mapping without annotations

```java
database.queryAsync(query, row -> new PlayerData(
        row.getUuid("uuid"), row.getString("name"), row.getLong("balance")));
```

## Repositories

```java
CrudRepository<PlayerData, UUID> players = database.repository(PlayerData.class, UUID.class);

players.findById(uuid);                       // CompletableFuture<Optional<PlayerData>>
players.findAll();                            // every row — only for small tables
players.findAllById(onlineIds);
players.find(Conditions.equal("world", "survival"));
players.find(condition, 50, 0);               // limit, offset
players.findOne(Conditions.equal("name", name));
players.existsById(uuid);
players.count();
players.save(player);                         // insert or update
players.saveAll(everyOnlinePlayer);           // one batch (unless @Version is present)
players.delete(player);
players.deleteById(uuid);
```

`database.repository(...)` returns the same instance for the same entity type.

### SqlRepository

Add your own lookups by subclassing `com.enderbyte.enderlib.database.sql.SqlRepository`:

```java
public final class PlayerRepository extends SqlRepository<PlayerData, UUID> {

    public PlayerRepository(Database database) {
        super(database, PlayerData.class, UUID.class);
    }

    public CompletableFuture<Optional<PlayerData>> findByName(String name) {
        return findOne(Conditions.equal("name", name));
    }

    public CompletableFuture<List<PlayerData>> richest(int limit) {
        return database().queryAsync(Query.selectFrom(table())
                .orderBy("balance", SortDirection.DESCENDING)
                .limit(limit), entityType());
    }
}
```

Protected helpers for subclasses:

| Method | Returns |
| --- | --- |
| `database()` | the `Database` |
| `table()` | table name from `@Table` |
| `entityType()` | entity class |
| `idType()` | primary key class |

The class is deliberately small. Anything more interesting than key lookups, filtered lookups,
upserts and bulk saves is a query, and the query builder is a better tool for it than a growing set
of magic method names.

## Optimistic locking

Give the entity a `@Version` field and `save()` will only update a row while the stored version
still matches the one you read. When two servers save the same player at once, the loser gets an
`OptimisticLockException` instead of silently discarding the other's write:

```java
players.save(player).exceptionally(error -> {
    if (error.getCause() instanceof OptimisticLockException) {
        // another server changed this player: re-read and re-apply
    }
    return null;
});
```

`saveAll` on versioned entities does not use a JDBC batch: optimistic locking needs the
affected-row count per entity, which a batch does not give reliably across drivers.

A complete transfer with retry: [Economy transfer](../cookbook/economy-transfer.md).
