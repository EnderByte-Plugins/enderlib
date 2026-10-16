# ItemsAdder addon

`enderlib-itemsadder` — optional. Add it only if your server runs ItemsAdder, and declare
`softdepend: [ItemsAdder]` in your `plugin.yml`. ItemsAdder is a `provided` dependency: never shade it.

```xml
<dependency>
    <groupId>com.enderbyte</groupId>
    <artifactId>enderlib-itemsadder</artifactId>
</dependency>
```

## Items

```java
ItemsAdder ia = ItemsAdder.of(lib);
ItemStack ruby = ia.item("myitems:ruby").orElseThrow();
ItemBuilder sword = ia.builder("myitems:ruby_sword").orElseThrow().name("<red>Ruby Sword");
ia.id(stack);                       // Optional<String>
ia.is(stack, "myitems:ruby");
ia.onLoad(() -> ...);               // after every ItemsAdder data load
```

Templates are cached and flushed whenever ItemsAdder reloads.

### Behaviour for ItemsAdder items

```java
ia.bindCustomItems(CustomItems.of(lib));
CustomItems.of(lib).register(CustomItem.builder("myitems:ruby_sword")
        .item(ia.supplier("myitems:ruby_sword"))
        .on(ItemTrigger.HIT_ENTITY, ctx -> ...)
        .build());
```

After `bindCustomItems`, a custom item whose id equals an ItemsAdder id reacts to every copy of that
item, including ones given by `/iagive`.

## Glyphs

```java
Glyph coin = Glyph.require("myhud:coin");
player.sendActionBar(coin.component().append(Component.text(" 1,250")));
Text.mm("<coin> 1,250", coin.tag("coin"));

Component hud = GlyphBuilder.create().glyph(panel, -91).shift(-170).text("<white>Level 12").build();

GlyphProgressBar mana = GlyphProgressBar.create()
        .filled(Glyph.require("hud:mana_full")).half(Glyph.require("hud:mana_half"))
        .empty(Glyph.require("hud:mana_empty")).segments(10).spacing(-1);
player.sendActionBar(mana.render(current / max));

ia.alias("coin", "myhud:coin");
ia.glyph("coin");
```

## Textured menus

```java
Menu shop = Menu.chest(6)
        .title("<dark_gray>Shop")
        .titleDecorator(TexturedMenus.background("mygui:shop_bg"))     // or (id, textureOffset, titleOffset)
        ...
        .build();
```

The decorator draws the background glyph and moves the title text back into place, so live titles
and `<page>` tags keep working. Without ItemsAdder, or if the image is missing, the plain title is used.
