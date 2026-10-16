# Core

`enderlib-core` is the base every other module builds on.

## EnderLib

```java
EnderLib lib = EnderLib.create(this);   // in onEnable
lib.shutdown();                         // in onDisable (also runs automatically on PluginDisableEvent)
```

| Method | Purpose |
| --- | --- |
| `scheduler()` | Folia-safe scheduling and executors |
| `ticker()` | One shared tick loop for animations and live displays |
| `events()` | Functional event subscriptions |
| `key("name")` | `NamespacedKey` in your plugin's namespace |
| `bind(closeable)` | Close something on shutdown |
| `service(Type.class, factory)` | Per-plugin module services (`Menus.of(lib)` uses it) |

## Scheduler

On Paper every method runs on the main thread; on Folia the same calls run on the thread owning the
region or entity.

```java
Scheduler s = lib.scheduler();
s.run(task);                                 // next tick, main thread
s.runLater(task, 20);                        // in one second
Task t = s.runTimer(task, 0, 20);            // every second, stopped on shutdown
s.runAsync(task);
s.runAt(player, task);                       // on the player's thread
s.runAt(location, task);                     // on the region's thread

future.thenAcceptAsync(v -> ..., s.sync());          // hop to the main thread
future.thenAcceptAsync(v -> ..., s.entity(player));  // hop to the player's thread
s.ensureMain("Opening a menu");                      // throws off-thread
```

## Ticker

```java
Terminable sub = lib.ticker().subscribe(tick -> { ... });          // global work
Terminable sub2 = lib.ticker().subscribe(player, tick -> { ... }); // ends when the player leaves
```

One repeating task serves every subscription and only runs while something is subscribed.

## Events

```java
lib.events().listen(PlayerJoinEvent.class)
        .priority(EventPriority.MONITOR)
        .filter(e -> !e.getPlayer().hasPlayedBefore())
        .handler(e -> welcome(e.getPlayer()));

// one-shot with timeout
lib.events().listen(AsyncChatEvent.class)
        .filter(e -> e.getPlayer().equals(player))
        .expireAfterCalls(1)
        .expireAfter(Duration.ofSeconds(30))
        .handler((sub, e) -> { e.setCancelled(true); ... });
```

Also: `ignoreCancelled()`, `expireIf(predicate)`, `exceptionHandler(...)`, `Events.call(event)`,
`Events.callAndCheck(event)`.

## Text

```java
Component c = Text.mm("<gradient:gold:yellow>Hello <name>", Placeholders.of().unparsed("name", player.getName()).build());
Component lore = Text.item("<gray>No italics in item names and lore");
Text.legacy("&aOld &#ff8800colors");
Text.plain(component);
Text.escape(userInput);
Text.center(line);
```

Resolver-less parses are cached. `Placeholders` supports `unparsed`, `parsed`, `component`, `lazy`
and locale-aware `number`.

## Utilities

- `Cooldowns<K>`: `tryUse(key)`, `remaining(key)`, `put`, `reset`, automatic cleanup.
- `Durations`: `parse("1d2h30m")`, `format(duration)` → `1d 2h 30m`, `clock(duration)` → `01:15`.
- `PdcTypes.UUID`, `PdcTypes.enumType(Type.class)`.
- `Terminable` / `CompositeTerminable`: close groups of resources, newest first.
