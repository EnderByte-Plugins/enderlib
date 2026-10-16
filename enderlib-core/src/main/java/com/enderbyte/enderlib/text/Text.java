package com.enderbyte.enderlib.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MiniMessage-first text utilities.
 *
 * <p>Parsing without resolvers is cached (bounded), because menus and sidebars re-render the same
 * static strings every refresh. Parsing with resolvers is never cached: the output depends on them.</p>
 */
public final class Text {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.builder()
            .character('&').hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static final int CACHE_LIMIT = 4096;
    private static final Map<String, Component> CACHE = new ConcurrentHashMap<>();

    private Text() {
    }

    public static MiniMessage miniMessage() {
        return MINI;
    }

    /** Parses MiniMessage. */
    public static Component mm(String input) {
        Component cached = CACHE.get(input);
        if (cached != null) {
            return cached;
        }
        Component parsed = MINI.deserialize(input);
        if (CACHE.size() >= CACHE_LIMIT) {
            CACHE.clear(); // crude but allocation-free bound; hot strings repopulate within a tick
        }
        CACHE.put(input, parsed);
        return parsed;
    }

    /** Parses MiniMessage with tag resolvers (placeholders). */
    public static Component mm(String input, TagResolver... resolvers) {
        return resolvers.length == 0 ? mm(input) : MINI.deserialize(input, resolvers);
    }

    /** Parses MiniMessage with a {@link Placeholders} set. */
    public static Component mm(String input, Placeholders placeholders) {
        return MINI.deserialize(input, placeholders.resolver());
    }

    /** Parses each line, for lore. */
    public static List<Component> mm(Collection<String> lines, TagResolver... resolvers) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(mm(line, resolvers));
        }
        return out;
    }

    /**
     * Parses MiniMessage for item names and lore: italic is switched off unless the string asks for it,
     * which is what nearly every GUI wants.
     */
    public static Component item(String input, TagResolver... resolvers) {
        return noItalic(mm(input, resolvers));
    }

    public static List<Component> itemLines(Collection<String> lines, TagResolver... resolvers) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(item(line, resolvers));
        }
        return out;
    }

    /** Removes the default italic that Minecraft applies to custom item names and lore. */
    public static Component noItalic(Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Escapes user input so it is shown literally instead of being parsed as tags. */
    public static String escape(String input) {
        return MINI.escapeTags(input);
    }

    /** Removes all MiniMessage tags. */
    public static String strip(String input) {
        return MINI.stripTags(input);
    }

    public static String serialize(Component component) {
        return MINI.serialize(component);
    }

    /** Parses legacy {@code &} codes (including {@code &#rrggbb} and {@code &x&r&r...}). */
    public static Component legacy(String input) {
        return input.indexOf('§') >= 0 ? SECTION.deserialize(input) : AMPERSAND.deserialize(input);
    }

    public static String toLegacy(Component component) {
        return SECTION.serialize(component);
    }

    public static String plain(Component component) {
        return PLAIN.serialize(component);
    }

    /**
     * Centers a line in chat (default chat width, 320 px) using the vanilla font widths.
     */
    public static Component center(Component line) {
        int width = FontWidth.of(plain(line), line.hasDecoration(TextDecoration.BOLD));
        int pad = (FontWidth.CHAT_WIDTH / 2 - width / 2) / FontWidth.SPACE;
        return pad <= 0 ? line : Component.text(" ".repeat(pad)).append(line);
    }

    /** Clears the parse cache (for example after a config reload). */
    public static void clearCache() {
        CACHE.clear();
    }
}
