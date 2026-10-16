package com.enderbyte.enderlib.command;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * A node of a command tree: a literal word or a typed argument, with optional behaviour.
 * Build trees with {@link Cmd}.
 */
public abstract sealed class CommandNode<N extends CommandNode<N>> permits LiteralNode, ArgumentNode {

    /** Who may run a node. */
    public enum SenderType { ANY, PLAYER, CONSOLE }

    final String name;
    final List<CommandNode<?>> children = new ArrayList<>();
    final List<Predicate<CommandSender>> requirements = new ArrayList<>();
    final List<Flag<?>> flags = new ArrayList<>();
    @Nullable CommandHandler handler;
    boolean async;
    String description = "";
    @Nullable String permission;
    SenderType senderType = SenderType.ANY;
    @Nullable Duration cooldown;
    @Nullable String cooldownBypass;
    boolean confirm;
    boolean hidden;

    CommandNode(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    @SuppressWarnings("unchecked")
    private N self() {
        return (N) this;
    }

    public String name() {
        return name;
    }

    /** Adds a child node. */
    public N then(CommandNode<?> child) {
        children.add(Objects.requireNonNull(child, "child"));
        return self();
    }

    /** Adds several child nodes. */
    public N then(CommandNode<?>... children) {
        for (CommandNode<?> c : children) {
            then(c);
        }
        return self();
    }

    /** Runs {@code handler} when the command ends at this node. Runs on the sender's thread. */
    public N executes(CommandHandler handler) {
        this.handler = Objects.requireNonNull(handler, "handler");
        this.async = false;
        return self();
    }

    /**
     * Runs {@code handler} on an async worker, for database or web calls. Do not touch the world or
     * players from it without hopping back via {@code lib.scheduler()}.
     */
    public N executesAsync(CommandHandler handler) {
        this.handler = Objects.requireNonNull(handler, "handler");
        this.async = true;
        return self();
    }

    /** One-line description for help listings. */
    public N description(String description) {
        this.description = Objects.requireNonNull(description, "description");
        return self();
    }

    /** Required permission. Senders without it do not see this node or anything below it. */
    public N permission(String permission) {
        this.permission = permission;
        return self();
    }

    /** Extra visibility condition, evaluated like a permission. */
    public N requires(Predicate<CommandSender> requirement) {
        requirements.add(Objects.requireNonNull(requirement, "requirement"));
        return self();
    }

    /** Only players may run this node; others get a message. */
    public N playerOnly() {
        this.senderType = SenderType.PLAYER;
        return self();
    }

    /** Only the console may run this node. */
    public N consoleOnly() {
        this.senderType = SenderType.CONSOLE;
        return self();
    }

    /** Per-player cooldown between successful runs. */
    public N cooldown(Duration cooldown) {
        this.cooldown = cooldown;
        return self();
    }

    /** Cooldown that players with {@code bypassPermission} skip. */
    public N cooldown(Duration cooldown, String bypassPermission) {
        this.cooldown = cooldown;
        this.cooldownBypass = bypassPermission;
        return self();
    }

    /** Requires running the exact same command twice within 10 seconds. */
    public N confirm() {
        this.confirm = true;
        return self();
    }

    /** Declares a flag accepted after this node's arguments. */
    public N flag(Flag<?> flag) {
        for (Flag<?> f : flags) {
            if (f.name().equals(flag.name()) || f.shortName() != null && f.shortName().equals(flag.shortName())) {
                throw new IllegalArgumentException("Duplicate flag " + flag + " on '" + name + "'");
            }
        }
        flags.add(flag);
        return self();
    }

    /** Leaves this node out of generated help. */
    public N hidden() {
        this.hidden = true;
        return self();
    }

    // ------------------------------------------------------------------ queries

    boolean visibleTo(CommandSender sender) {
        if (permission != null && !sender.hasPermission(permission)) {
            return false;
        }
        for (Predicate<CommandSender> r : requirements) {
            if (!r.test(sender)) {
                return false;
            }
        }
        return true;
    }

    boolean senderAllowed(CommandSender sender) {
        return switch (senderType) {
            case ANY -> true;
            case PLAYER -> sender instanceof Player;
            case CONSOLE -> sender instanceof ConsoleCommandSender;
        };
    }

    /** Usage token: {@code give}, {@code <player>} or {@code [amount]}. */
    abstract String usageToken();

    public List<CommandNode<?>> children() {
        return List.copyOf(children);
    }

    public String description() {
        return description;
    }

    public boolean isExecutable() {
        return handler != null;
    }
}
