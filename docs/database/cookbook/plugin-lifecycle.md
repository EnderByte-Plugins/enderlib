# Plugin lifecycle

A Paper plugin that owns one database for its lifetime.

Bukkit types in this page are illustrative and are not compile-checked (the library has no Bukkit
dependency). The connect / migrate / close sequence is the same in a Velocity plugin or a
standalone service.

## Dependencies

```xml
<dependency>
    <groupId>com.enderbyte</groupId>
    <artifactId>enderlib-database-sql</artifactId>
    <version>1.0.0</version>
</dependency>
<dependency>
    <groupId>com.enderbyte</groupId>
    <artifactId>enderlib-database-migrations</artifactId>
    <version>1.0.0</version>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>42.7.3</version>
</dependency>
```

## The plugin

```java
public final class EconomyPlugin extends JavaPlugin {

    private Database database;
    private PlayerRepository players;

    @Override
    public void onEnable() {
        DatabaseConfig config = ConfigLoader.fromMap(
                getConfig().getConfigurationSection("database").getValues(true));

        database = Database.builder()
                .config(config)
                .name("economy")
                .build()
                .connect();

        database.migrations()
                .source(MigrationSources.classpath("migrations", getClassLoader()))
                .historyTable("economy_migrations")
                .serverId(getServer().getName())
                .migrateAndWait();

        players = new PlayerRepository(database);
    }

    @Override
    public void onDisable() {
        if (database != null) {
            database.close();
        }
    }

    public Database database() {
        return database;
    }

    public PlayerRepository players() {
        return players;
    }
}
```

`connect()` during `onEnable` is intentional: the server is not serving players yet, and starting
a plugin whose database is unreachable is worse than a slow boot.

### Let the server finish starting

```java
Database.builder().config(config).name("economy").build()
        .connectAsync()
        .thenAccept(connected -> {
            this.database = connected;
            connected.migrations()
                    .source(MigrationSources.classpath("migrations", getClassLoader()))
                    .migrateAndWait();
        })
        .exceptionally(error -> {
            getLogger().log(Level.SEVERE, "Database unavailable", error);
            getServer().getPluginManager().disablePlugin(this);
            return null;
        });
```

Do not serve players until `connectAsync` completes. If you would rather refuse to start at all,
stick with `connect()`.

`close()` waits up to the executor shutdown timeout for in-flight work, then gives up rather than
hanging the server. `closeAsync()` if disable itself must not block.

## Hop back to the server thread

Callbacks run on a database worker. Touching a `Player` from there is undefined behaviour:

```java
public void showBalance(Player player) {
    UUID uuid = player.getUniqueId();
    database.queryFirstAsync(Query.selectFrom("players").where("uuid", uuid), PlayerData.class)
            .thenAccept(found -> Bukkit.getScheduler().runTask(this, () -> {
                if (!player.isOnline()) {
                    return;
                }
                found.ifPresentOrElse(
                        data -> player.sendMessage("Balance: " + data.balance()),
                        () -> player.sendMessage("No account yet."));
            }))
            .exceptionally(error -> {
                getLogger().log(Level.SEVERE, "Could not load " + uuid, error);
                Bukkit.getScheduler().runTask(this, () ->
                        player.sendMessage("Could not load your account."));
                return null;
            });
}
```

Never `.join()` or `.get()` on the server thread. The only reasonable place for `join()` is
startup — which is why `migrateAndWait()` exists.

## Several databases

```java
private final DatabaseRegistry registry = new DatabaseRegistry();

@Override
public void onEnable() {
    registry.register(Database.builder().config(playersConfig).name("players").connect());
    registry.register(Database.builder().config(logsConfig).name("logs").connect());
}

@Override
public void onDisable() {
    registry.closeAll();
}
```

See [Database](../api/database.md#several-databases) and
[Minecraft integration](../minecraft-integration.md) for shading and class loaders.
