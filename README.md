# EnderLib

A toolkit for Paper plugins: database, menus, commands, items, config, text, scheduling, events and
displays, in one multi-module Maven library.

EnderLib is **not a plugin**. Nothing is installed on the server. Each plugin adds the modules it
needs as Maven dependencies and shades them (relocated) into its own jar, the same way it would
shade any other library.

**Requires:** Java 25, Paper 26.2 (built against `26.2.build.121-stable`). ItemsAdder 4.0+ is
optional, for the ItemsAdder addon only.

---

## Modules

| Module | What it gives you |
| --- | --- |
| `enderlib-core` | `EnderLib` bootstrap, Folia-safe `Scheduler`, shared `Ticker`, functional `Events`, MiniMessage `Text` and `Placeholders`, `Cooldowns`, `Durations`, terminables |
| `enderlib-config` | YAML bound to plain classes and records (validation, comments, defaults written back), locale-aware `Messages` |
| `enderlib-item` | `ItemBuilder` over data components, typed `ItemTag`s, `CustomItem`s with click/hit/consume/equip handlers and cooldowns, `ItemCodec`, `Inventories` |
| `enderlib-menu` | Menus: patterns, reactive `State`, diffed rendering, animations, pagination and scrolling (sync or async), input slots, navigation history, dialogs for text input, confirm/selector templates, YAML-defined menus |
| `enderlib-command` | Brigadier commands: fluent builder, typed arguments, flags, optional arguments, cooldowns, confirmation, async handlers, generated help, plus an annotation layer |
| `enderlib-display` | Per-player sidebars, boss bars and countdowns, priority action bars, `TextDisplay` holograms (shared or per-player) |
| `enderlib-database-api` / `-core` / `-sql` / `-migrations` | The async database library (PostgreSQL, MySQL, MariaDB, SQLite, H2) with query builder, mapping, transactions, caching, metrics and migrations |
| `enderlib-itemsadder` | Optional: ItemsAdder glyphs, progress bars, textured menu backgrounds, ItemsAdder-backed custom items |
| `enderlib-all` | Every module above except the ItemsAdder addon, as one dependency (`<type>pom</type>`) |
| `enderlib-bom` | Version alignment |

`enderlib-example` is a sample plugin that uses every module; it is built but never installed.

---

## Installation

Build and install into your local Maven repository once:

```bash
mvn -f enderlib/pom.xml install
```

Then in your plugin's `pom.xml`:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.enderbyte</groupId>
            <artifactId>enderlib-bom</artifactId>
            <version>1.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- Pick modules... -->
    <dependency>
        <groupId>com.enderbyte</groupId>
        <artifactId>enderlib-menu</artifactId>
    </dependency>
    <dependency>
        <groupId>com.enderbyte</groupId>
        <artifactId>enderlib-command</artifactId>
    </dependency>
    <!-- ...or everything -->
    <dependency>
        <groupId>com.enderbyte</groupId>
        <artifactId>enderlib-all</artifactId>
        <type>pom</type>
    </dependency>
</dependencies>
```

### Shading (required)

Shade EnderLib into your plugin and **relocate** it, so two plugins using different EnderLib
versions never clash. Keep the services transformer: the database finds its JDBC provider through
`META-INF/services`. Do not shade SLF4J; the server provides it.

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.6.2</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
                <createDependencyReducedPom>false</createDependencyReducedPom>
                <artifactSet>
                    <excludes>
                        <exclude>org.slf4j:*</exclude>
                    </excludes>
                </artifactSet>
                <relocations>
                    <relocation>
                        <pattern>com.enderbyte.enderlib</pattern>
                        <shadedPattern>com.yourplugin.libs.enderlib</shadedPattern>
                    </relocation>
                    <relocation>
                        <pattern>com.zaxxer.hikari</pattern>
                        <shadedPattern>com.yourplugin.libs.hikari</shadedPattern>
                    </relocation>
                </relocations>
                <transformers>
                    <transformer implementation="org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"/>
                </transformers>
            </configuration>
        </execution>
    </executions>
</plugin>
```

Compile your plugin with `-parameters` (`<parameters>true</parameters>` in the compiler plugin) if
you use annotated commands without `@Arg` names.

---

## Bootstrap

```java
public final class MyPlugin extends JavaPlugin {

    private EnderLib lib;

    @Override
    public void onEnable() {
        lib = EnderLib.create(this);
        // Modules attach to the same instance:
        //   Menus.of(lib), CommandManager.of(lib), CustomItems.of(lib), Configs.of(lib), Sidebars.of(lib) ...
    }

    @Override
    public void onDisable() {
        lib.shutdown(); // closes menus, listeners, tasks, holograms, sidebars...
    }
}
```

There is no global singleton: every plugin has its own `EnderLib`, so plugins never share or close
each other's menus, listeners or tasks.

---

## A quick tour

### Commands

```java
CommandManager.of(lib).register(Cmd.command("pay")
        .playerOnly()
        .cooldown(Duration.ofSeconds(3))
        .then(Cmd.arg("target", Args.player())
                .then(Cmd.arg("amount", Args.decimal(0.01, 1_000_000))
                        .flag(Flag.of("silent", 's'))
                        .executesAsync(ctx -> economy.pay(ctx.player(), ctx.get("target"), ctx.get("amount"))))));
```

or, annotated:

```java
@Command(value = "kit", help = true)
public final class KitCommand {
    @Subcommand("give")
    @Permission("kits.give")
    public void give(CommandSender sender, Player target, Kit kit, @Opt("1") @Range(min = 1, max = 64) int amount) { ... }
}
CommandManager.of(lib).register(new KitCommand());
```

### Menus

```java
Menu shop = Menu.chest(6)
        .title("<gold>Shop <gray>(<page>/<pages>)")
        .pattern("#########", "#.......#", "#.......#", "#.......#", "#.......#", "#<##X##>#")
        .ingredient('#', Button.filler(Material.BLACK_STAINED_GLASS_PANE))
        .ingredient('X', Button.close(closeIcon))
        .ingredient('<', PageButtons.previous(prevIcon, null))
        .ingredient('>', PageButtons.next(nextIcon, null))
        .paginate('.', PageSource.async((offset, limit) -> shopRepository.page(offset, limit)),
                (product, view) -> Button.of(product.icon()).onClick(ctx -> buy(ctx.player(), product)))
        .build();

Menus.of(lib).open(player, shop);
```

### Items

```java
CustomItem wand = CustomItem.builder("fire_wand")
        .item(ItemBuilder.of(Material.BLAZE_ROD).itemName("<gold>Fire Wand").glow())
        .on(ItemTrigger.RIGHT_CLICK, ctx -> ctx.player().launchProjectile(SmallFireball.class))
        .cooldown(Duration.ofSeconds(2))
        .build();
CustomItems.of(lib).register(wand);
```

### Config and messages

```java
ConfigHandle<Settings> settings = Configs.of(lib).load("config.yml", Settings.class);
Messages messages = Messages.load(lib);
messages.send(player, "shop.bought", Placeholders.of().unparsed("item", "Apple").build());
```

### Database

```java
Database database = Database.builder()
        .type(DatabaseType.POSTGRESQL).name("main")
        .host("localhost").port(5432).database("minecraft")
        .username("minecraft").password(settings.get().dbPassword)
        .build().connect();

database.queryAsync("SELECT balance FROM players WHERE uuid = ?", player.getUniqueId())
        .thenAcceptAsync(r -> player.sendMessage("Balance: " + r.first().map(row -> row.getLong("balance")).orElse(0L)),
                lib.scheduler().entity(player));
```

### Displays

```java
Sidebars.of(lib).create(player).title("<gold><bold>SERVER")
        .dynamicLines(p -> List.of(Text.mm("<gray>Coins: <gold>" + coins(p))), 20).show();
Holograms.of(lib).create(location).line("<gold>Welcome").spawn();
```

---

## The threading rule

Everything that touches the world, players or menus runs on the main thread (on Folia: the thread
owning that player or region). Database futures complete on database threads: hop back with
`lib.scheduler().entity(player)` or `lib.scheduler().sync()` before using the Bukkit API.

---

## Documentation

| Page | Contents |
| --- | --- |
| [Core](docs/core.md) | Bootstrap, scheduler, ticker, events, text, utilities |
| [Config & messages](docs/config.md) | Typed YAML, validation, message bundles |
| [Items](docs/items.md) | Item builder, tags, custom items, codecs |
| [Menus](docs/menus.md) | Menus, state, pagination, inputs, dialogs, templates |
| [Commands](docs/commands.md) | Builder, arguments, flags, annotations |
| [Displays](docs/display.md) | Sidebars, boss bars, action bars, holograms |
| [ItemsAdder](docs/itemsadder.md) | Glyphs, textured menus, ItemsAdder items |
| [Database](docs/database/README.md) | The full database manual and cookbook |

---

## Building

```bash
mvn clean install                 # build everything and run the unit tests
mvn verify -Pintegration-tests    # adds Testcontainers database tests (needs Docker)
mvn install -Prelease             # also attaches sources and javadoc jars
```
