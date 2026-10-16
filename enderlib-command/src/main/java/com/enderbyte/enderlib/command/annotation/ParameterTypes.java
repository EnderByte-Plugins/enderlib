package com.enderbyte.enderlib.command.annotation;

import com.enderbyte.enderlib.command.Arg;
import com.enderbyte.enderlib.command.Args;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Parameter;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Maps parameter types of annotated command methods to {@link Arg}s.
 *
 * <pre>{@code
 * commands.parameterTypes().register(Warp.class, () -> Args.custom("warp", warps::byName, warps::names));
 * }</pre>
 *
 * Built in: {@code String} ({@link Greedy}, {@link Quoted}), all primitive numbers and booleans
 * ({@link Range}), {@code Player}, {@code OfflinePlayer}, {@code Entity}, {@code World},
 * {@code Location} (block position), {@code UUID}, {@code Duration}, {@code GameMode},
 * {@code ItemStack}, {@code NamedTextColor}, {@code Key} and every enum.
 */
public final class ParameterTypes {

    private final Map<Class<?>, Function<Parameter, Arg<?>>> factories = new ConcurrentHashMap<>();

    private ParameterTypes() {
    }

    public static ParameterTypes standard() {
        ParameterTypes t = new ParameterTypes();
        t.factories.put(String.class, p -> p.isAnnotationPresent(Greedy.class) ? Args.greedy()
                : p.isAnnotationPresent(Quoted.class) ? Args.string() : Args.word());
        t.factories.put(Integer.class, p -> {
            Range r = p.getAnnotation(Range.class);
            return r == null ? Args.integer() : Args.integer(clampInt(r.min()), clampInt(r.max()));
        });
        t.factories.put(Long.class, p -> {
            Range r = p.getAnnotation(Range.class);
            return r == null ? Args.longArg(Long.MIN_VALUE, Long.MAX_VALUE) : Args.longArg(clampLong(r.min()), clampLong(r.max()));
        });
        t.factories.put(Double.class, p -> {
            Range r = p.getAnnotation(Range.class);
            return r == null ? Args.decimal() : Args.decimal(r.min(), r.max());
        });
        t.factories.put(Float.class, p -> {
            Range r = p.getAnnotation(Range.class);
            return r == null ? Args.floatArg(-Float.MAX_VALUE, Float.MAX_VALUE) : Args.floatArg((float) r.min(), (float) r.max());
        });
        t.factories.put(Boolean.class, p -> Args.bool());
        t.factories.put(Player.class, p -> Args.player());
        t.factories.put(OfflinePlayer.class, p -> Args.offlinePlayer());
        t.factories.put(Entity.class, p -> Args.entity());
        t.factories.put(World.class, p -> Args.world());
        t.factories.put(Location.class, p -> Args.blockPosition());
        t.factories.put(UUID.class, p -> Args.uuid());
        t.factories.put(Duration.class, p -> Args.duration());
        t.factories.put(GameMode.class, p -> Args.gameMode());
        t.factories.put(ItemStack.class, p -> Args.itemStack());
        t.factories.put(NamedTextColor.class, p -> Args.color());
        t.factories.put(Key.class, p -> Args.key());
        return t;
    }

    /** Registers (or replaces) the argument type for {@code type}. */
    public <T> ParameterTypes register(Class<T> type, Supplier<Arg<T>> factory) {
        factories.put(type, p -> factory.get());
        return this;
    }

    /** Registers a type whose argument depends on the parameter's annotations. */
    public <T> ParameterTypes register(Class<T> type, Function<Parameter, Arg<T>> factory) {
        factories.put(type, factory::apply);
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    Arg<?> resolve(Parameter parameter) {
        Class<?> type = box(parameter.getType());
        Function<Parameter, Arg<?>> factory = factories.get(type);
        if (factory != null) {
            return factory.apply(parameter);
        }
        if (type.isEnum()) {
            return Args.enumArg((Class<? extends Enum>) type);
        }
        throw new IllegalArgumentException("No command argument type for " + type.getName()
                + "; register one with CommandManager#parameterTypes().register(...)");
    }

    static Class<?> box(Class<?> c) {
        if (!c.isPrimitive()) return c;
        if (c == int.class) return Integer.class;
        if (c == long.class) return Long.class;
        if (c == double.class) return Double.class;
        if (c == float.class) return Float.class;
        if (c == boolean.class) return Boolean.class;
        if (c == short.class) return Short.class;
        if (c == byte.class) return Byte.class;
        return Character.class;
    }

    private static int clampInt(double d) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, d));
    }

    private static long clampLong(double d) {
        return (long) Math.max(Long.MIN_VALUE, Math.min(Long.MAX_VALUE, d));
    }
}
