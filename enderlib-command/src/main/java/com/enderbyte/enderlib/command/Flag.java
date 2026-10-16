package com.enderbyte.enderlib.command;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A trailing command option: {@code --silent}, {@code -s}, {@code --reason "spam bot"}, {@code -d 1h}.
 *
 * <pre>{@code
 * Cmd.literal("ban").then(Cmd.arg("target", Args.offlinePlayer())
 *         .flag(Flag.of("silent", 's'))
 *         .flag(Flag.value("duration", 'd', Durations::parse))
 *         .executes(ctx -> {
 *             boolean silent = ctx.flag("silent");
 *             Duration d = ctx.<Duration>flagValue("duration").orElse(Duration.ofDays(1));
 *         }));
 * }</pre>
 *
 * Flags follow the last argument of the command they belong to, in any order.
 */
public final class Flag<T> {

    private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9-]*");

    private final String name;
    private final @Nullable Character shortName;
    private final Arg.@Nullable StringParser<T> parser;
    private String description = "";
    private @Nullable String permission;

    private Flag(String name, @Nullable Character shortName, Arg.@Nullable StringParser<T> parser) {
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid flag name '" + name + "' (use a-z, 0-9 and -)");
        }
        this.name = name;
        this.shortName = shortName;
        this.parser = parser;
    }

    /** A switch: present or not. */
    public static Flag<Boolean> of(String name) {
        return new Flag<>(name, null, null);
    }

    public static Flag<Boolean> of(String name, char shortName) {
        return new Flag<>(name, shortName, null);
    }

    /** A flag that takes a value. */
    public static <T> Flag<T> value(String name, Arg.StringParser<T> parser) {
        return new Flag<>(name, null, Objects.requireNonNull(parser, "parser"));
    }

    public static <T> Flag<T> value(String name, char shortName, Arg.StringParser<T> parser) {
        return new Flag<>(name, shortName, Objects.requireNonNull(parser, "parser"));
    }

    /** A value flag typed like an argument (the argument type must parse plain text). */
    public static <T> Flag<T> value(String name, char shortName, Arg<T> type) {
        if (!type.parsesStrings()) {
            throw new IllegalArgumentException("Argument type '" + type.display() + "' cannot be used as a flag value");
        }
        return new Flag<>(name, shortName, type::parse);
    }

    public Flag<T> description(String description) {
        this.description = description;
        return this;
    }

    /** Only senders with {@code permission} may use this flag. */
    public Flag<T> permission(String permission) {
        this.permission = permission;
        return this;
    }

    public String name() {
        return name;
    }

    public @Nullable Character shortName() {
        return shortName;
    }

    public boolean takesValue() {
        return parser != null;
    }

    public String description() {
        return description;
    }

    public @Nullable String permission() {
        return permission;
    }

    T parse(String value) {
        if (parser == null) {
            throw new IllegalStateException("--" + name + " takes no value");
        }
        return parser.parse(value);
    }

    /** Usage form, for example {@code [-s|--silent]} or {@code [--reason <value>]}. */
    public String usage() {
        String names = shortName != null ? "-" + shortName + "|--" + name : "--" + name;
        return "[" + names + (takesValue() ? " <" + name + ">" : "") + "]";
    }

    @Override
    public String toString() {
        return "--" + name;
    }
}
