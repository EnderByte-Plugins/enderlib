package com.enderbyte.enderlib.itemsadder;

import dev.lone.itemsadder.api.FontImages.FontImageWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A cached ItemsAdder font image ({@code namespace:id}).
 *
 * <pre>{@code
 * Glyph coin = Glyph.require("myhud:coin");
 * player.sendActionBar(coin.component().append(Component.text(" 1,250")));
 * Text.mm("<coin> 1,250", coin.tag("coin"));
 * }</pre>
 *
 * Instances are cached; the cache is flushed when ItemsAdder reloads its data
 * (see {@link ItemsAdder}).
 */
public final class Glyph {

    private static final Map<String, Glyph> CACHE = new ConcurrentHashMap<>();

    private final String id;
    private final FontImageWrapper wrapper;

    private Glyph(String id, FontImageWrapper wrapper) {
        this.id = id;
        this.wrapper = wrapper;
    }

    /** The glyph, or empty if ItemsAdder is missing or has no such font image. */
    public static Optional<Glyph> of(String namespacedId) {
        if (!ItemsAdder.isInstalled()) {
            return Optional.empty();
        }
        Glyph cached = CACHE.get(namespacedId);
        if (cached != null) {
            return Optional.of(cached);
        }
        try {
            FontImageWrapper wrapper = new FontImageWrapper(namespacedId);
            if (!wrapper.exists()) {
                return Optional.empty();
            }
            Glyph glyph = new Glyph(namespacedId, wrapper);
            CACHE.put(namespacedId, glyph);
            return Optional.of(glyph);
        } catch (RuntimeException | LinkageError e) {
            return Optional.empty();
        }
    }

    public static Glyph require(String namespacedId) {
        return of(namespacedId).orElseThrow(() -> new IllegalArgumentException("ItemsAdder font image not found: " + namespacedId));
    }

    static void invalidateCache() {
        CACHE.clear();
    }

    public String id() {
        return id;
    }

    /** The raw characters of the image. */
    public String string() {
        return wrapper.getString();
    }

    /** The characters moved {@code pixels} to the right (negative: left). */
    public String string(int pixels) {
        return FontImageWrapper.applyPixelsOffsetToString(wrapper.getString(), pixels);
    }

    /** The image as a component, coloured white so surrounding text colours do not tint it. */
    public Component component() {
        return Component.text(wrapper.getString(), NamedTextColor.WHITE);
    }

    public Component component(int pixels) {
        return Component.text(string(pixels), NamedTextColor.WHITE);
    }

    /** A self-closing MiniMessage tag, e.g. {@code <coin>}. */
    public TagResolver tag(String name) {
        return TagResolver.resolver(name, Tag.selfClosingInserting(component()));
    }

    public int width() {
        return wrapper.getWidth();
    }

    public int height() {
        return wrapper.getHeight();
    }

    public FontImageWrapper wrapper() {
        return wrapper;
    }

    /** Characters that move the cursor by {@code pixels} without drawing anything. */
    public static String space(int pixels) {
        return pixels == 0 ? "" : FontImageWrapper.applyPixelsOffsetToString("", pixels);
    }

    @Override
    public String toString() {
        return "Glyph[" + id + "]";
    }
}
