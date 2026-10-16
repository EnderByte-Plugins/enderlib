# Items

`enderlib-item`.

## ItemBuilder

```java
ItemStack sword = ItemBuilder.of(Material.NETHERITE_SWORD)
        .name("<gradient:gold:yellow>Kingslayer")      // MiniMessage, italics off
        .lore("<gray>Forged in the end.", "", "<yellow>Right-click: <white>dash")
        .enchant(Enchantment.SHARPNESS, 7)
        .hideEnchants()
        .glint(true)
        .unbreakable()
        .model(Key.key("myplugin", "kingslayer"))      // item model
        .customModelData(1001)
        .attribute(Attribute.ATTACK_SPEED, modifier)
        .tag(LEVEL, 3)
        .build();
```

Also: `itemName` (cannot be renamed away), `addLore`, `rarity`, `color` (leather/dyeables),
`maxStackSize`, `maxDamage`, `damage`, `food`, `consumable`, `useCooldown`, `potion`, `firework`,
`skull(player | uuid)`, `skullTexture(base64)`, `skullTextureHash(hash)`, `tooltipStyle`,
`hide(DataComponentType...)`, `hideAll()`, `hideTooltip()`, `component(type, value)`,
`meta(...)` for the legacy API, and `copy()`. `build()` returns a fresh copy every time, so a
builder can be a template.

## ItemTag

```java
static final ItemTag<Integer> LEVEL = ItemTag.of(lib.key("level"), PersistentDataType.INTEGER);

LEVEL.set(stack, 3);
int level = LEVEL.getOrDefault(stack, 1);
LEVEL.set(player, 3);   // any PersistentDataHolder
```

## Custom items

```java
CustomItem wand = CustomItem.builder("fire_wand")
        .item(ItemBuilder.of(Material.BLAZE_ROD).itemName("<gold>Fire Wand").glow())
        .on(ItemTrigger.RIGHT_CLICK, ctx -> ctx.player().launchProjectile(SmallFireball.class))
        .on(ItemTrigger.SHIFT_RIGHT_CLICK, ctx -> openWandMenu(ctx.player()))
        .on(ItemTrigger.HIT_ENTITY, ctx -> ctx.targetIfPresent().ifPresent(e -> e.setFireTicks(60)))
        .cooldown(Duration.ofSeconds(2))                 // shown with the vanilla cooldown sweep
        .cooldownMessage(Component.text("Recharging..."))
        .whileHeld(20, (player, stack) -> player.addPotionEffect(...))
        .unplaceable().undroppable().uncraftable()
        .build();

CustomItems items = CustomItems.of(lib);
items.register(wand);
player.give(wand.create());
items.give(player, "fire_wand", 3);
items.find(stack).ifPresent(item -> ...);
```

Triggers: `RIGHT_CLICK`, `SHIFT_RIGHT_CLICK`, `LEFT_CLICK`, `SHIFT_LEFT_CLICK`, `INTERACT_ENTITY`,
`HIT_ENTITY`, `BREAK_BLOCK`, `CONSUME`, `DROP`, `SWAP_HANDS`, `SELECT`, `DESELECT`, `EQUIP`,
`UNEQUIP`. Handlers get an `ItemContext` with the player, stack, hand, block, target and event,
plus `cancel()` and `consume(n)`. `cancelVanilla()` cancels the vanilla action for every handled click.

Recognition uses a persistent id tag, so renaming or enchanting never breaks it. Other plugins'
items can be recognised through `CustomItems#addResolver` (the ItemsAdder addon uses this).

## Codecs and the database

```java
String b64 = ItemCodec.toBase64(stack);
byte[] inventory = ItemCodec.inventoryToBytes(player.getInventory());
ItemCodec.inventoryFromBytes(player.getInventory(), inventory);

ItemConverters.register(database.converters());   // ItemStack and ItemStack[] columns (binary)
```

Serialisation uses Paper's versioned format, so data survives Minecraft updates.

## Inventories

`giveOrDrop`, `giveAll` (all-or-nothing), `fits`, `count`, `remove`, `removeExactly`, `freeSlots`,
`firstMatching`. Player inventories only use the 36 storage slots, never armour or the off-hand.
