# Minecraft integration

This library has no Bukkit, Spigot or Paper types in it. It still has to live *inside* a plugin
jar, next to the server's class loaders, and next to other plugins that may shade a different
version of HikariCP. The details below are the ones that actually bite.

## Class loaders

On a Minecraft server every plugin has its own class loader.

- **JDBC drivers** are loaded by name from *your* plugin jar. Keep the driver on `compile` scope.
  The library never asks `DriverManager` to find it — `DriverManager` searches with the system
  class loader, which cannot see anything inside a plugin jar.
- **Migrations** must be loaded with *your* class loader:

  ```java
  database.migrations()
          .source(MigrationSources.classpath("migrations", getClassLoader()))
          .migrateAndWait();
  ```

  `MigrationSources.classpath("migrations")` uses the library's loader, which cannot see your
  resources.
- **Providers** (`DatabaseProvider`, `MigrationServiceFactory`) are discovered through
  `META-INF/services`. The builder looks in this library's class loader first (the plugin's
  loader when shaded into your jar), then the thread context loader.

If `build()` says there is no provider for the engine, the services file did not survive packaging.
That is almost always a missing Shade transformer — see below.

## Shading

Shading avoids version conflicts with other plugins on the same server. **Relocate** the library
and its dependencies, and enable the services transformer:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.5.3</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
                <relocations>
                    <relocation>
                        <pattern>com.enderbyte.enderlib.database</pattern>
                        <shadedPattern>com.yourplugin.libs.enderlib</shadedPattern>
                    </relocation>
                    <relocation>
                        <pattern>com.zaxxer.hikari</pattern>
                        <shadedPattern>com.yourplugin.libs.hikari</shadedPattern>
                    </relocation>
                    <relocation>
                        <pattern>com.github.benmanes.caffeine</pattern>
                        <shadedPattern>com.yourplugin.libs.caffeine</shadedPattern>
                    </relocation>
                </relocations>
                <transformers>
                    <!-- REQUIRED: without this, provider lookup fails at runtime -->
                    <transformer implementation=
                        "org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"/>
                </transformers>
            </configuration>
        </execution>
    </executions>
</plugin>
```

The `ServicesResourceTransformer` is not optional. The JDBC provider and the migration service are
both discovered through `META-INF/services`, and without the transformer those files are overwritten
rather than merged — producing a runtime "no provider found" failure.

Do **not** relocate `org.slf4j`: you want the server's own logging implementation.

Do **not** relocate your JDBC driver either. Drivers load classes by name internally, and relocation
rewrites those names in the bytecode but not in the strings they are looked up from.

## Threading

Every `Database` method except `connect()` and `migrateAndWait()` is asynchronous. Callbacks run on
a database worker thread named `EnderDB-<database>-SQL-<n>`. Hop back with your plugin's scheduler
before touching the Bukkit API. Full rule: [Getting started](getting-started.md#the-one-rule-never-block-the-server-thread).

`withConnectionAsync` and `transactionAsync` hand you a *synchronous* `DatabaseConnection`. That is
safe because those callbacks already run on a database thread. Do not capture the connection or the
raw `jdbc()` handle and use it later — it is returned to the pool when the callback finishes.

## Several plugins, one JVM

There is no global singleton. Each plugin creates its own `Database` instances (and optionally a
`DatabaseRegistry`) and closes them in `onDisable`. Two plugins cannot accidentally close each
other's pools.

Give each plugin its own migration `historyTable` when they share a database, otherwise version
numbers collide.

## Logging

Logging goes through **SLF4J**, using whatever the server already has. Credentials are never logged,
and SQL parameters are redacted from exception messages — a failed query reports the statement, not
the player data that was bound to it.
