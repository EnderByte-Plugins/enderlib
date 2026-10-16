# Config & messages

`enderlib-config`.

## Typed config files

```java
@Header("MyPlugin settings")
public final class Settings {
    @Comment("How many homes a player may set")
    @Range(min = 1, max = 100)
    public int maxHomes = 3;

    public Duration teleportDelay = Duration.ofSeconds(3);   // "3s", "1m30s"
    public Component motd = Text.mm("<green>Welcome!");       // MiniMessage
    public Map<String, Integer> rankLimits = Map.of("vip", 10);
    public List<Material> blockedItems = List.of(Material.TNT);
    public Database database = new Database();

    public static final class Database {
        @NotBlank public String host = "localhost";
        public int port = 5432;
    }
}

ConfigHandle<Settings> settings = Configs.of(lib).load("config.yml", Settings.class);
int max = settings.get().maxHomes;
settings.onReload(s -> ...);
settings.reload();        // throws ConfigException listing every problem; the old value stays active
settings.save();          // writes the in-memory value, keeping comments
```

- Field names map to kebab-case keys (`maxHomes` → `max-homes`); override with `@Key("name")`.
- On load, missing keys are added with their defaults and `@Comment`s; existing comments and values
  are kept. A bundled `config.yml` in the jar is copied out first.
- Records work too; missing components come from a static `defaults()` method or `DEFAULT` constant.
- Validation: `@Range`, `@NotBlank`, `@Size`.
- Supported: strings, numbers, booleans, enums (case-insensitive), `UUID`, `Duration`, `Component`,
  `NamespacedKey`, `Key`, `Locale`, `Pattern`, registry types (`Sound`, `Enchantment`, ...) by key,
  `Optional`, lists, sets, maps, arrays, nested classes, and anything Bukkit serialises
  (`ItemStack`, `Location`).
- Custom types: `Configs.of(lib).mapper().register(Money.class, TypeSerializer.of(Money::parse, Money::toString))`.

`ConfigMapper` itself is server-independent, so you can unit-test config classes.

## Messages

Files live in `plugins/YourPlugin/lang/<locale>.yml`. Files bundled under `lang/` in the jar are
copied out on first start.

```yaml
# lang/en_us.yml
prefix: "<dark_gray>[<gold>Shop</gold>]</dark_gray> "
shop:
  bought: "<prefix><gray>You bought <white><item></white>."
  full: "[actionbar]<red>Your inventory is full"
  welcome: "[title]<gold>Welcome|<gray>to the shop"
  help:
    - "<gold>/shop buy <item>"
    - "<gold>/shop sell <item>"
```

```java
Messages messages = Messages.load(lib);              // default locale en_us
messages.send(player, "shop.bought", Placeholders.of().unparsed("item", "Apple").build());
Component c = messages.get(player, "shop.bought", ...);
List<Component> lines = messages.lines(player, "shop.help");
messages.addDefaults(Map.of("errors.generic", "<red>Something went wrong"));
messages.reload();
```

Players get their client language (`de_de`), then the bare language (`de`), then the default.
`<prefix>` is available in every message. Prefixes `[actionbar]`, `[title]title|subtitle` and
`[none]` change how a message is sent.
