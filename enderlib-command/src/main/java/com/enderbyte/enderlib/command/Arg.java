package com.enderbyte.enderlib.command;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * A typed command argument: what the client sees (a Brigadier type), how the value is resolved,
 * and optional custom suggestions. Get one from {@link Args}.
 *
 * <pre>{@code
 * Args.word().suggest(sender -> warps.names())
 * Args.integer(1, 64).display("amount")
 * }</pre>
 */
public final class Arg<T> {

    /** Reads the parsed value out of a Brigadier context. */
    @FunctionalInterface
    public interface Resolver<T> {
        T resolve(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException;
    }

    /** Converts plain text (for flag values and annotation defaults). */
    @FunctionalInterface
    public interface StringParser<T> {
        /** @throws IllegalArgumentException with a user-facing message */
        T parse(String input);
    }

    private final ArgumentType<?> type;
    private final Resolver<T> resolver;
    private final @Nullable StringParser<T> stringParser;
    private String display;
    private @Nullable SuggestionProvider<CommandSourceStack> suggestions;

    public Arg(ArgumentType<?> type, Resolver<T> resolver, String display, @Nullable StringParser<T> stringParser) {
        this.type = Objects.requireNonNull(type, "type");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.display = Objects.requireNonNull(display, "display");
        this.stringParser = stringParser;
    }

    /** Overrides suggestions with a static or computed list, filtered by what the player typed. */
    public Arg<T> suggest(Function<CommandSender, ? extends Collection<String>> values) {
        Objects.requireNonNull(values, "values");
        this.suggestions = (ctx, builder) -> {
            String typed = builder.getRemainingLowerCase();
            for (String v : values.apply(ctx.getSource().getSender())) {
                if (v.toLowerCase(Locale.ROOT).startsWith(typed)) {
                    builder.suggest(v);
                }
            }
            return builder.buildFuture();
        };
        return this;
    }

    /** Suggestions computed off-thread (for example from the database). */
    public Arg<T> suggestAsync(Function<CommandSender, CompletableFuture<? extends Collection<String>>> values) {
        Objects.requireNonNull(values, "values");
        this.suggestions = (ctx, builder) -> values.apply(ctx.getSource().getSender()).thenApply(list -> {
            String typed = builder.getRemainingLowerCase();
            for (String v : list) {
                if (v.toLowerCase(Locale.ROOT).startsWith(typed)) {
                    builder.suggest(v);
                }
            }
            return builder.build();
        });
        return this;
    }

    /** Full control over suggestions. */
    public Arg<T> suggestions(SuggestionProvider<CommandSourceStack> provider) {
        this.suggestions = provider;
        return this;
    }

    /** Name shown in usage and help, for example {@code <amount>}. Defaults to the argument name. */
    public Arg<T> display(String display) {
        this.display = display;
        return this;
    }

    public ArgumentType<?> type() {
        return type;
    }

    public String display() {
        return display;
    }

    public @Nullable SuggestionProvider<CommandSourceStack> suggestions() {
        return suggestions;
    }

    public T resolve(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        return resolver.resolve(ctx, name);
    }

    /** Whether values can be parsed from plain text (needed for flags and {@code @Default}). */
    public boolean parsesStrings() {
        return stringParser != null;
    }

    public T parse(String input) {
        if (stringParser == null) {
            throw new IllegalArgumentException("This argument type cannot be parsed from text");
        }
        return stringParser.parse(input);
    }

    /** Whether this argument consumes the rest of the input. */
    public boolean isGreedy() {
        return type instanceof com.mojang.brigadier.arguments.StringArgumentType s
                && s.getType() == com.mojang.brigadier.arguments.StringArgumentType.StringType.GREEDY_PHRASE;
    }
}
