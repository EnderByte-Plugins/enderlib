# Custom types

Register a `TypeConverter` so an entity field that is not a JDBC-native type still round-trips.

Built-in converters already cover String, primitives and their boxes, `UUID`, enums, `Instant`,
`LocalDate`, `LocalDateTime`, `byte[]` and `BigDecimal`. Add one for everything else: a home
location, an `ItemStack` blob, a JSON document.

## A location stored as text

Keep Bukkit types out of the library boundary. A tiny value object serialises cleanly and does not
force a Paper dependency on tests:

```java
public record HomeLocation(String world, double x, double y, double z) {
}
```

```java
public final class HomeLocationConverter implements TypeConverter<HomeLocation> {

    @Override
    public Class<HomeLocation> javaType() {
        return HomeLocation.class;
    }

    @Override
    public Object toDatabase(HomeLocation value) {
        if (value == null) {
            return null;
        }
        return value.world() + "," + value.x() + "," + value.y() + "," + value.z();
    }

    @Override
    public HomeLocation fromDatabase(Object value) {
        if (value == null) {
            return null;
        }
        String[] parts = value.toString().split(",", 4);
        return new HomeLocation(
                parts[0],
                Double.parseDouble(parts[1]),
                Double.parseDouble(parts[2]),
                Double.parseDouble(parts[3]));
    }
}
```

Register it once after `connect()`, before the first query that needs it:

```java
database.converters().register(new HomeLocationConverter());
```

Implementations must be thread-safe and must tolerate `null` on both sides. `toDatabase` must
return something the driver accepts (here, a String).

## Use it on an entity

```java
@Table("homes")
public record Home(
        @Id UUID id,
        UUID owner,
        String name,
        HomeLocation location) {
}
```

```sql
CREATE TABLE homes (
    id       UUID PRIMARY KEY,
    owner    UUID NOT NULL,
    name     VARCHAR(32) NOT NULL,
    location VARCHAR(128)
);
```

```java
CrudRepository<Home, UUID> homes = database.repository(Home.class, UUID.class);
homes.save(new Home(UUID.randomUUID(), owner, "Spawn",
        new HomeLocation("world", 0.5, 64.0, 0.5)));

homes.findById(id).thenAccept(found ->
        found.ifPresent(home -> logger.info(home.location().world())));
```

`row.get("location", HomeLocation.class)` and `optional("location", HomeLocation.class)` use the
same registry.

## A blob

```java
public final class InventoryConverter implements TypeConverter<byte[]> {
    // byte[] is already built in — this pattern is for a wrapper type, e.g. SerializedInventory
}
```

For a wrapper:

```java
public record SerializedInventory(byte[] nbt) {
}

database.converters().register(new TypeConverter<SerializedInventory>() {
    @Override public Class<SerializedInventory> javaType() { return SerializedInventory.class; }
    @Override public Object toDatabase(SerializedInventory value) {
        return value == null ? null : value.nbt();
    }
    @Override public SerializedInventory fromDatabase(Object value) {
        return value == null ? null : new SerializedInventory((byte[]) value);
    }
});
```

Store the column as `BLOB` / `BYTEA`.

## Replacing a converter

`register` replaces an existing converter for the same `javaType()`. Do this only at startup;
changing converters while queries are in flight is a race.

See [Mapping](../api/mapping.md#typeconverter).
