package com.enderbyte.enderlib.command;

import com.enderbyte.enderlib.util.Durations;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.FinePositionResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.EntitySelectorArgumentResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Argument types.
 *
 * <p>Vanilla-backed types ({@link #player()}, {@link #world()}, {@link #itemStack()}, ...) get the
 * client's native parsing, colouring and selector support. Custom types ({@link #enumArg},
 * {@link #duration()}, {@link #offlinePlayer()}, {@link #custom}) are sent to the client as a word
 * and validated on the server with a friendly error.</p>
 */
public final class Args {

    private Args() {
    }

    // ================================================================== strings

    /** A single word (no spaces). */
    public static Arg<String> word() {
        return simple(StringArgumentType.word(), String.class, "text", s -> s);
    }

    /** A word, or a "quoted phrase". */
    public static Arg<String> string() {
        return simple(StringArgumentType.string(), String.class, "text", s -> s);
    }

    /** Everything until the end of the line. Must be the last argument. */
    public static Arg<String> greedy() {
        return simple(StringArgumentType.greedyString(), String.class, "text...", s -> s);
    }

    /** One of a fixed set of words (case-insensitive). */
    public static Arg<String> choice(String... values) {
        List<String> options = List.of(values);
        return custom("choice", input -> {
            for (String v : options) {
                if (v.equalsIgnoreCase(input)) {
                    return v;
                }
            }
            throw new IllegalArgumentException("Expected one of " + String.join(", ", options));
        }, () -> options);
    }

    // ================================================================== numbers

    public static Arg<Integer> integer() {
        return simple(IntegerArgumentType.integer(), Integer.class, "number", Args::parseInt);
    }

    public static Arg<Integer> integer(int min, int max) {
        return simple(IntegerArgumentType.integer(min, max), Integer.class, "number", s -> checkRange(parseInt(s), min, max));
    }

    public static Arg<Long> longArg(long min, long max) {
        return simple(LongArgumentType.longArg(min, max), Long.class, "number", s -> {
            long v = parseLong(s);
            if (v < min || v > max) {
                throw new IllegalArgumentException("Must be between " + min + " and " + max);
            }
            return v;
        });
    }

    public static Arg<Double> decimal() {
        return simple(DoubleArgumentType.doubleArg(), Double.class, "number", Args::parseDouble);
    }

    public static Arg<Double> decimal(double min, double max) {
        return simple(DoubleArgumentType.doubleArg(min, max), Double.class, "number", s -> {
            double v = parseDouble(s);
            if (v < min || v > max) {
                throw new IllegalArgumentException("Must be between " + min + " and " + max);
            }
            return v;
        });
    }

    public static Arg<Float> floatArg(float min, float max) {
        return simple(FloatArgumentType.floatArg(min, max), Float.class, "number", s -> (float) parseDouble(s));
    }

    public static Arg<Boolean> bool() {
        return simple(BoolArgumentType.bool(), Boolean.class, "true|false", s -> switch (s.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "on" -> true;
            case "false", "no", "off" -> false;
            default -> throw new IllegalArgumentException("Expected true or false");
        });
    }

    // ================================================================== entities & players

    /** One online player; supports selectors such as {@code @p}. */
    public static Arg<Player> player() {
        return new Arg<>(ArgumentTypes.player(), (ctx, name) -> {
            List<Player> players = ctx.getArgument(name, PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());
            if (players.isEmpty()) {
                throw error("No player was found");
            }
            return players.getFirst();
        }, "player", s -> {
            Player p = Bukkit.getPlayerExact(s);
            if (p == null) {
                throw new IllegalArgumentException("Player '" + s + "' is not online");
            }
            return p;
        });
    }

    /** One or more online players ({@code @a}, names). */
    public static Arg<List<Player>> players() {
        return new Arg<>(ArgumentTypes.players(), (ctx, name) ->
                ctx.getArgument(name, PlayerSelectorArgumentResolver.class).resolve(ctx.getSource()), "players", null);
    }

    public static Arg<Entity> entity() {
        return new Arg<>(ArgumentTypes.entity(), (ctx, name) -> {
            List<Entity> entities = ctx.getArgument(name, EntitySelectorArgumentResolver.class).resolve(ctx.getSource());
            if (entities.isEmpty()) {
                throw error("No entity was found");
            }
            return entities.getFirst();
        }, "entity", null);
    }

    public static Arg<List<Entity>> entities() {
        return new Arg<>(ArgumentTypes.entities(), (ctx, name) ->
                ctx.getArgument(name, EntitySelectorArgumentResolver.class).resolve(ctx.getSource()), "entities", null);
    }

    /**
     * A player who has joined before (online or not), by name or UUID. Uses the server's profile
     * cache only: it never blocks on a Mojang lookup.
     */
    public static Arg<OfflinePlayer> offlinePlayer() {
        return custom("player", input -> {
            OfflinePlayer online = Bukkit.getPlayerExact(input);
            if (online != null) {
                return online;
            }
            OfflinePlayer cached;
            try {
                cached = Bukkit.getOfflinePlayer(UUID.fromString(input));
            } catch (IllegalArgumentException notUuid) {
                cached = Bukkit.getOfflinePlayerIfCached(input);
            }
            if (cached == null || !cached.hasPlayedBefore() && !cached.isOnline()) {
                throw new IllegalArgumentException("Player '" + input + "' has never joined");
            }
            return cached;
        }, Args::onlineNames);
    }

    // ================================================================== world & positions

    public static Arg<World> world() {
        return new Arg<>(ArgumentTypes.world(), (ctx, name) -> ctx.getArgument(name, World.class), "world", s -> {
            World w = Bukkit.getWorld(s);
            if (w == null) {
                throw new IllegalArgumentException("Unknown world '" + s + "'");
            }
            return w;
        });
    }

    /** A block position ({@code ~ ~1 ~}) in the sender's world. */
    public static Arg<Location> blockPosition() {
        return new Arg<>(ArgumentTypes.blockPosition(), (ctx, name) -> ctx.getArgument(name, BlockPositionResolver.class)
                .resolve(ctx.getSource()).toLocation(ctx.getSource().getLocation().getWorld()), "x y z", null);
    }

    /** A precise position ({@code ~ ~ ~0.5}) in the sender's world. */
    public static Arg<Location> position() {
        return new Arg<>(ArgumentTypes.finePosition(true), (ctx, name) -> ctx.getArgument(name, FinePositionResolver.class)
                .resolve(ctx.getSource()).toLocation(ctx.getSource().getLocation().getWorld()), "x y z", null);
    }

    public static Arg<GameMode> gameMode() {
        return new Arg<>(ArgumentTypes.gameMode(), (ctx, name) -> ctx.getArgument(name, GameMode.class), "gamemode",
                s -> GameMode.valueOf(s.toUpperCase(Locale.ROOT)));
    }

    // ================================================================== misc

    public static Arg<UUID> uuid() {
        return new Arg<>(ArgumentTypes.uuid(), (ctx, name) -> ctx.getArgument(name, UUID.class), "uuid", UUID::fromString);
    }

    /** An item with optional components, as in {@code /give}: {@code diamond_sword[enchantments={...}]}. */
    public static Arg<ItemStack> itemStack() {
        return new Arg<>(ArgumentTypes.itemStack(), (ctx, name) -> ctx.getArgument(name, ItemStack.class), "item", null);
    }

    /** A JSON text component. */
    public static Arg<Component> component() {
        return new Arg<>(ArgumentTypes.component(), (ctx, name) -> ctx.getArgument(name, Component.class), "text", null);
    }

    public static Arg<NamedTextColor> color() {
        return new Arg<>(ArgumentTypes.namedColor(), (ctx, name) -> ctx.getArgument(name, NamedTextColor.class), "color", s -> {
            NamedTextColor c = NamedTextColor.NAMES.value(s.toLowerCase(Locale.ROOT));
            if (c == null) {
                throw new IllegalArgumentException("Unknown color '" + s + "'");
            }
            return c;
        });
    }

    public static Arg<Key> key() {
        return new Arg<>(ArgumentTypes.key(), (ctx, name) -> ctx.getArgument(name, Key.class), "key", s -> Key.key(s));
    }

    /** An entry of a game registry, for example {@code Args.registry(RegistryKey.ENCHANTMENT)}. */
    public static <T> Arg<T> registry(RegistryKey<T> registry) {
        return new Arg<>(ArgumentTypes.resource(registry), (ctx, name) -> {
            @SuppressWarnings("unchecked")
            T value = (T) ctx.getArgument(name, Object.class);
            return value;
        }, registry.key().value(), null);
    }

    /** A constant of {@code type}, typed in lower case ({@code survival}, {@code diamond_sword}). */
    public static <E extends Enum<E>> Arg<E> enumArg(Class<E> type) {
        List<String> names = new ArrayList<>();
        for (E e : type.getEnumConstants()) {
            names.add(e.name().toLowerCase(Locale.ROOT));
        }
        return custom(type.getSimpleName().toLowerCase(Locale.ROOT), input -> {
            try {
                return Enum.valueOf(type, input.toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown " + type.getSimpleName().toLowerCase(Locale.ROOT) + " '" + input + "'");
            }
        }, () -> names);
    }

    /** A duration such as {@code 30s}, {@code 5m}, {@code 1h30m}, {@code 2d}. */
    public static Arg<Duration> duration() {
        List<String> examples = List.of("30s", "5m", "1h", "1d", "7d");
        return custom("duration", Durations::parse, () -> examples);
    }

    /**
     * Any type parsed from a single word on the server.
     *
     * @param parser      throws {@link IllegalArgumentException} with a user-facing message on bad input
     * @param suggestions candidate values (filtered by what was typed)
     */
    public static <T> Arg<T> custom(String display, Arg.StringParser<T> parser, Supplier<? extends Collection<String>> suggestions) {
        CustomArgumentType.Converted<T, String> type = new CustomArgumentType.Converted<>() {
            @Override
            public T convert(String nativeValue) throws CommandSyntaxException {
                try {
                    return parser.parse(nativeValue);
                } catch (IllegalArgumentException e) {
                    throw error(e.getMessage() == null ? "Invalid value '" + nativeValue + "'" : e.getMessage());
                }
            }

            @Override
            public ArgumentType<String> getNativeType() {
                return StringArgumentType.word();
            }

            @Override
            public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
                String typed = builder.getRemainingLowerCase();
                for (String s : suggestions.get()) {
                    if (s.toLowerCase(Locale.ROOT).startsWith(typed)) {
                        builder.suggest(s);
                    }
                }
                return builder.buildFuture();
            }
        };
        return new Arg<>(type, (ctx, name) -> {
            @SuppressWarnings("unchecked")
            T value = (T) ctx.getArgument(name, Object.class);
            return value;
        }, display, parser);
    }

    /** Like {@link #custom} for types whose candidates depend on the sender. */
    public static <T> Arg<T> custom(String display, Arg.StringParser<T> parser) {
        return custom(display, parser, List::of);
    }

    /** Maps the value of another argument: {@code Args.map(Args.word(), warps::byName)}. */
    public static <A, B> Arg<B> map(Arg<A> base, Function<A, B> mapper) {
        return new Arg<>(base.type(), (ctx, name) -> {
            try {
                return mapper.apply(base.resolve(ctx, name));
            } catch (IllegalArgumentException e) {
                throw error(e.getMessage() == null ? "Invalid value" : e.getMessage());
            }
        }, base.display(), base.parsesStrings() ? s -> mapper.apply(base.parse(s)) : null);
    }

    // ================================================================== helpers

    /** A Brigadier error with a plain message, shown red on the client. */
    public static CommandSyntaxException error(String message) {
        Message msg = MessageComponentSerializer.message().serialize(Component.text(message));
        return new SimpleCommandExceptionType(msg).create();
    }

    private static <T> Arg<T> simple(ArgumentType<T> type, Class<T> cls, String display, Arg.StringParser<T> parser) {
        return new Arg<>(type, (ctx, name) -> ctx.getArgument(name, cls), display, parser);
    }

    private static List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            names.add(p.getName());
        }
        return names;
    }

    private static int checkRange(int v, int min, int max) {
        if (v < min || v > max) {
            throw new IllegalArgumentException("Must be between " + min + " and " + max);
        }
        return v;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + s + "' is not a whole number");
        }
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + s + "' is not a whole number");
        }
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + s + "' is not a number");
        }
    }
}
