package com.enderbyte.enderlib.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A fluent set of MiniMessage placeholders.
 *
 * <pre>{@code
 * Text.mm("<gray><player> paid <gold><amount></gold>", Placeholders.of()
 *         .unparsed("player", sender.getName())
 *         .number("amount", 1500.5)
 *         .build());
 * }</pre>
 *
 * {@link #unparsed} is the safe default for anything a player typed; {@link #parsed} interprets tags.
 */
public final class Placeholders {

    private final List<TagResolver> resolvers = new ArrayList<>();

    private Placeholders() {
    }

    public static Placeholders of() {
        return new Placeholders();
    }

    /** Inserts {@code value} literally; tags inside it are shown, not interpreted. */
    public Placeholders unparsed(String key, Object value) {
        resolvers.add(Placeholder.unparsed(key, String.valueOf(value)));
        return this;
    }

    /** Inserts {@code value} as MiniMessage. Never use for player input. */
    public Placeholders parsed(String key, String value) {
        resolvers.add(Placeholder.parsed(key, value));
        return this;
    }

    public Placeholders component(String key, ComponentLike value) {
        resolvers.add(Placeholder.component(key, value));
        return this;
    }

    /** Lazily computed component; the supplier only runs if the tag appears in the template. */
    public Placeholders lazy(String key, Supplier<? extends ComponentLike> value) {
        Objects.requireNonNull(value, "value");
        resolvers.add(TagResolver.resolver(key, (args, ctx) -> Tag.inserting(value.get())));
        return this;
    }

    /** Formats a number with grouping separators in the given locale. */
    public Placeholders number(String key, Number value, Locale locale) {
        return unparsed(key, NumberFormat.getNumberInstance(locale).format(value));
    }

    public Placeholders number(String key, Number value) {
        return number(key, value, Locale.US);
    }

    public Placeholders resolver(TagResolver resolver) {
        resolvers.add(Objects.requireNonNull(resolver, "resolver"));
        return this;
    }

    public Placeholders and(Placeholders other) {
        resolvers.addAll(other.resolvers);
        return this;
    }

    public boolean isEmpty() {
        return resolvers.isEmpty();
    }

    /** The combined resolver. */
    public TagResolver resolver() {
        return TagResolver.resolver(resolvers);
    }

    /** Alias of {@link #resolver()} that reads better at the end of a chain. */
    public TagResolver build() {
        return resolver();
    }

    public TagResolver[] toArray() {
        return new TagResolver[] {resolver()};
    }

    /** Convenience: a single unparsed placeholder. */
    public static TagResolver unparsedOf(String key, Object value) {
        return Placeholder.unparsed(key, String.valueOf(value));
    }

    /** Convenience: a single component placeholder. */
    public static TagResolver componentOf(String key, Component value) {
        return Placeholder.component(key, value);
    }
}
