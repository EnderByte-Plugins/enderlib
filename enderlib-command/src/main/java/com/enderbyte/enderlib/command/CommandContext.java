package com.enderbyte.enderlib.command;

import com.enderbyte.enderlib.core.EnderLib;
import com.enderbyte.enderlib.text.Text;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Everything a {@link CommandHandler} gets: the sender, typed arguments and flags.
 */
public final class CommandContext {

    private final EnderLib lib;
    private final CommandManager manager;
    private final com.mojang.brigadier.context.CommandContext<CommandSourceStack> raw;
    private final Map<String, ArgumentNode<?>> arguments;
    private final Map<String, Object> defaults;
    private final Map<String, Object> flags;
    private final Map<String, Object> resolved = new HashMap<>();

    CommandContext(EnderLib lib, CommandManager manager, com.mojang.brigadier.context.CommandContext<CommandSourceStack> raw,
                   Map<String, ArgumentNode<?>> arguments, Map<String, Object> defaults, Map<String, Object> flags) {
        this.lib = lib;
        this.manager = manager;
        this.raw = raw;
        this.arguments = arguments;
        this.defaults = defaults;
        this.flags = flags;
    }

    public EnderLib lib() {
        return lib;
    }

    public CommandSender sender() {
        return raw.getSource().getSender();
    }

    public boolean isPlayer() {
        return sender() instanceof Player;
    }

    /** The sender as a player, or a "players only" failure. */
    public Player player() {
        if (sender() instanceof Player p) {
            return p;
        }
        throw new CommandException(manager.messages().playerOnly(sender()));
    }

    /** Where the command runs (the sender's location, or as modified by {@code /execute}). */
    public Location location() {
        return raw.getSource().getLocation();
    }

    /** The full input without the leading slash. */
    public String input() {
        return raw.getInput();
    }

    /** The label used (the command name or the alias typed). */
    public String label() {
        String input = raw.getInput();
        int space = input.indexOf(' ');
        return space < 0 ? input : input.substring(0, space);
    }

    // ------------------------------------------------------------------ arguments

    /**
     * Value of argument {@code name} (or its default if it was optional and omitted).
     *
     * @throws IllegalArgumentException if the command has no such argument
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String name) {
        if (resolved.containsKey(name)) {
            return (T) resolved.get(name);
        }
        ArgumentNode<?> node = arguments.get(name);
        if (node == null) {
            if (defaults.containsKey(name)) {
                return (T) defaults.get(name);
            }
            throw new IllegalArgumentException("Command has no argument '" + name + "' on this path");
        }
        try {
            Object value = node.arg.resolve(raw, name);
            resolved.put(name, value);
            return (T) value;
        } catch (CommandSyntaxException e) {
            throw new CommandException(Component.text(e.getRawMessage().getString()));
        } catch (IllegalArgumentException missing) {
            // Brigadier throws IAE when the argument was not supplied (optional path)
            if (defaults.containsKey(name)) {
                return (T) defaults.get(name);
            }
            throw missing;
        }
    }

    /** Value of an optional argument, empty if omitted and without a default. */
    public <T> Optional<T> optional(String name) {
        if (!has(name)) {
            return Optional.empty();
        }
        return Optional.ofNullable(get(name));
    }

    public <T> T getOrDefault(String name, T fallback) {
        return this.<T>optional(name).orElse(fallback);
    }

    /** Whether the sender typed argument {@code name}. */
    public boolean has(String name) {
        if (!arguments.containsKey(name)) {
            return defaults.get(name) != null;
        }
        try {
            raw.getArgument(name, Object.class);
            return true;
        } catch (IllegalArgumentException e) {
            return defaults.get(name) != null;
        }
    }

    // ------------------------------------------------------------------ flags

    /** Whether switch {@code name} was given. */
    public boolean flag(String name) {
        return flags.containsKey(name);
    }

    /** Value of a value flag, empty if not given. */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> flagValue(String name) {
        return Optional.ofNullable((T) flags.get(name));
    }

    // ------------------------------------------------------------------ replies

    public void reply(Component message) {
        sender().sendMessage(message);
    }

    /** Sends MiniMessage to the sender. */
    public void reply(String miniMessage, TagResolver... resolvers) {
        sender().sendMessage(Text.mm(miniMessage, resolvers));
    }

    /** Builds a failure to {@code throw}: {@code throw ctx.fail("<red>Not enough money");}. */
    public CommandException fail(String miniMessage, TagResolver... resolvers) {
        return CommandException.of(miniMessage, resolvers);
    }

    public CommandException fail(Component message) {
        return new CommandException(message);
    }

    /** Fails unless {@code condition} holds. */
    public void check(boolean condition, String miniMessage, TagResolver... resolvers) {
        if (!condition) {
            throw fail(miniMessage, resolvers);
        }
    }

    /** The underlying Brigadier context. */
    public com.mojang.brigadier.context.CommandContext<CommandSourceStack> raw() {
        return raw;
    }

}
