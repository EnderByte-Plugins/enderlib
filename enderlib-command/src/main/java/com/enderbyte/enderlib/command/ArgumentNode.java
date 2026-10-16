package com.enderbyte.enderlib.command;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A typed argument such as {@code <player>} or {@code [amount]}.
 */
public final class ArgumentNode<T> extends CommandNode<ArgumentNode<T>> {

    private static final Pattern NAME = Pattern.compile("[a-zA-Z0-9_]+");

    final Arg<T> arg;
    boolean optional;
    @Nullable T defaultValue;

    ArgumentNode(String name, Arg<T> arg) {
        super(name);
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid argument name '" + name + "'");
        }
        this.arg = Objects.requireNonNull(arg, "arg");
    }

    /**
     * Makes the argument optional: the command also runs without it, and
     * {@link CommandContext#get} returns {@code defaultValue}. The node needs its own executor.
     */
    public ArgumentNode<T> optional(@Nullable T defaultValue) {
        this.optional = true;
        this.defaultValue = defaultValue;
        return this;
    }

    /** Optional without a default: {@link CommandContext#optional} is empty when omitted. */
    public ArgumentNode<T> optional() {
        return optional(null);
    }

    public Arg<T> arg() {
        return arg;
    }

    public boolean isOptional() {
        return optional;
    }

    @Override
    String usageToken() {
        return optional ? "[" + name + "]" : "<" + name + ">";
    }
}
