# Displays

`enderlib-display`.

## Sidebars

```java
Sidebars.of(lib).create(player)
        .title("<gradient:gold:yellow><bold>MY SERVER")
        .dynamicLines(p -> List.of(
                Text.mm("<gray>Rank: <white>" + ranks.of(p)),
                Component.empty(),
                Text.mm("<gray>Coins: <gold>" + economy.balance(p))), 20)
        .show();

Sidebars.of(lib).remove(player);
```

Up to 15 lines, no red score numbers, only changed lines are sent. A sidebar uses its own
scoreboard and restores the player's previous one when hidden. Scoreboards are not available on Folia.

## Boss bars

```java
BossBars bars = BossBars.of(lib);
bars.show(player, bar, Duration.ofSeconds(10));
bars.countdown(player, Duration.ofSeconds(30), left -> Text.mm("<red>" + left.toSeconds() + "s left"), BossBar.Color.RED);
bars.global(eventBar);           // everyone online now and later
bars.removeGlobal(eventBar);
```

## Action bars

```java
ActionBars bars = ActionBars.of(lib);
bars.show(player, "combat", p -> Text.mm("<red>In combat: " + combat.left(p) + "s"), 100, Duration.ofSeconds(15));
bars.show(player, "zone", Text.mm("<green>Safe zone"), 10, null);   // until removed
bars.remove(player, "combat");
```

Entries are re-sent every second so they never fade; the highest priority wins.

## Holograms

```java
Hologram h = Holograms.of(lib).create(location)
        .line("<gold><bold>Top killers")
        .lines(leaderboard.lines())
        .item(ItemStack.of(Material.DIAMOND_SWORD))
        .billboard(Display.Billboard.CENTER)
        .background(Color.fromARGB(0x40000000))
        .visibleTo(p -> p.hasPermission("vip"))
        .spawn();

h.lines(newLines);
h.teleport(newLocation, 10);   // smooth
h.close();

// different text per viewer
Holograms.of(lib).create(npc).perPlayer(p -> List.of(Text.mm("<yellow>Hi " + p.getName())), 20).spawn();
```

Holograms use `TextDisplay`/`ItemDisplay` entities that are not saved with the world; they respawn
after chunk reloads and are removed on shutdown.
