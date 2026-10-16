package com.enderbyte.enderlib.command;

import com.enderbyte.enderlib.command.annotation.AnnotatedCommands;
import com.enderbyte.enderlib.command.annotation.ParameterTypes;
import com.enderbyte.enderlib.core.EnderLib;
import com.enderbyte.enderlib.core.Terminable;
import com.enderbyte.enderlib.util.Cooldowns;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registers command trees with Paper's Brigadier dispatcher and runs them.
 *
 * <pre>{@code
 * public void onEnable() {
 *     lib = EnderLib.create(this);
 *     CommandManager commands = CommandManager.of(lib);
 *     commands.register(Cmd.command("spawn").playerOnly().executes(ctx -> ctx.player().teleport(spawn)));
 *     commands.register(new EconomyCommand(economy));   // annotated class
 * }
 * }</pre>
 *
 * <p>Create the manager and register commands during {@code onEnable}: Paper collects plugin
 * commands through a lifecycle event that only accepts handlers while the plugin enables.</p>
 */
public final class CommandManager implements Terminable {

    private static final String FLAGS = "flags";
    private static final Duration CONFIRM_WINDOW = Duration.ofSeconds(10);
    private static final int HELP_PER_PAGE = 8;

    private final EnderLib lib;
    private final List<LiteralNode> roots = new CopyOnWriteArrayList<>();
    private final Map<CommandNode<?>, Cooldowns<UUID>> cooldowns = new ConcurrentHashMap<>();
    private final Map<String, Long> pendingConfirm = new ConcurrentHashMap<>();
    private final ParameterTypes parameterTypes = ParameterTypes.standard();
    private volatile CommandMessages messages = CommandMessages.DEFAULT;
    private volatile boolean published;

    private CommandManager(EnderLib lib) {
        this.lib = lib;
        lib.plugin().getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            for (LiteralNode root : roots) {
                registrar.register(compile(root), root.description.isEmpty() ? null : root.description, root.aliases);
            }
            published = true;
        });
    }

    /** The command manager of {@code lib}. Call first during {@code onEnable}. */
    public static CommandManager of(EnderLib lib) {
        return lib.service(CommandManager.class, CommandManager::new);
    }

    /** Registers a command tree built with {@link Cmd}. */
    public CommandManager register(LiteralNode root) {
        Objects.requireNonNull(root, "root");
        if (published) {
            lib.logger().warn("Command /{} was registered after commands were published; it appears after the next reload."
                    + " Register commands in onEnable.", root.name);
        }
        roots.add(root);
        return this;
    }

    /** Registers every {@code @Command} declared on {@code handler}'s class. */
    public CommandManager register(Object handler) {
        if (handler instanceof LiteralNode node) {
            return register(node);
        }
        for (LiteralNode root : AnnotatedCommands.parse(handler, parameterTypes)) {
            register(root);
        }
        return this;
    }

    /** Argument types available to annotated command parameters; register your own here. */
    public ParameterTypes parameterTypes() {
        return parameterTypes;
    }

    public CommandMessages messages() {
        return messages;
    }

    public CommandManager messages(CommandMessages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
        return this;
    }

    public List<LiteralNode> roots() {
        return List.copyOf(roots);
    }

    @Override
    public void close() {
        cooldowns.clear();
        pendingConfirm.clear();
    }

    // ================================================================== compilation

    private record Exec(CommandNode<?> node, Map<String, ArgumentNode<?>> args, Map<String, Object> defaults) {
    }

    LiteralCommandNode<CommandSourceStack> compile(LiteralNode root) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(root.name);
        configure(builder, root, new LinkedHashMap<>());
        if (root.help) {
            builder.then(helpNode(root));
        }
        return builder.build();
    }

    private void configure(ArgumentBuilder<CommandSourceStack, ?> builder, CommandNode<?> node, Map<String, ArgumentNode<?>> path) {
        if (node.permission != null || !node.requirements.isEmpty()) {
            builder.requires(source -> node.visibleTo(source.getSender()));
        }
        Exec exec = effective(node, path);
        if (exec != null) {
            builder.executes(ctx -> execute(exec, ctx, ""));
            if (exec.node() == node && !node.flags.isEmpty() && !endsGreedy(path)) {
                builder.then(Commands.argument(FLAGS, StringArgumentType.greedyString())
                        .suggests((ctx, sb) -> {
                            String remaining = sb.getRemaining();
                            int tokenStart = remaining.lastIndexOf(' ') + 1;
                            SuggestionsBuilder offset = sb.createOffset(sb.getStart() + tokenStart);
                            CommandSender sender = ctx.getSource().getSender();
                            for (String s : FlagParser.suggest(remaining, node.flags, f -> flagAllowed(f, sender))) {
                                offset.suggest(s);
                            }
                            return offset.buildFuture();
                        })
                        .executes(ctx -> execute(exec, ctx, ctx.getArgument(FLAGS, String.class))));
            }
        }
        for (CommandNode<?> child : node.children) {
            if (child instanceof LiteralNode literal) {
                List<String> names = new ArrayList<>();
                names.add(literal.name);
                names.addAll(literal.aliases);
                for (String name : names) {
                    LiteralArgumentBuilder<CommandSourceStack> lb = Commands.literal(name);
                    configure(lb, literal, path);
                    builder.then(lb);
                }
            } else if (child instanceof ArgumentNode<?> argument) {
                RequiredArgumentBuilder<CommandSourceStack, ?> ab = Commands.argument(argument.name, argument.arg.type());
                if (argument.arg.suggestions() != null) {
                    ab.suggests(argument.arg.suggestions());
                }
                Map<String, ArgumentNode<?>> childPath = new LinkedHashMap<>(path);
                childPath.put(argument.name, argument);
                configure(ab, argument, childPath);
                builder.then(ab);
            }
        }
    }

    /** The node whose handler runs when input ends at {@code node} (its own, or an optional child's). */
    private static @Nullable Exec effective(CommandNode<?> node, Map<String, ArgumentNode<?>> path) {
        if (node.handler != null) {
            return new Exec(node, Map.copyOf(path), Map.of());
        }
        for (CommandNode<?> child : node.children) {
            if (child instanceof ArgumentNode<?> argument && argument.optional) {
                Map<String, ArgumentNode<?>> childPath = new LinkedHashMap<>(path);
                childPath.put(argument.name, argument);
                Exec inner = effective(argument, childPath);
                if (inner != null) {
                    Map<String, Object> defaults = new HashMap<>(inner.defaults());
                    defaults.put(argument.name, argument.defaultValue);
                    return new Exec(inner.node(), inner.args(), defaults);
                }
            }
        }
        return null;
    }

    private static boolean endsGreedy(Map<String, ArgumentNode<?>> path) {
        ArgumentNode<?> last = null;
        for (ArgumentNode<?> a : path.values()) {
            last = a;
        }
        return last != null && last.arg.isGreedy();
    }

    // ================================================================== execution

    private int execute(Exec exec, com.mojang.brigadier.context.CommandContext<CommandSourceStack> raw, String flagInput) {
        CommandSender sender = raw.getSource().getSender();
        CommandNode<?> node = exec.node();
        if (!node.senderAllowed(sender)) {
            sender.sendMessage(node.senderType == CommandNode.SenderType.PLAYER
                    ? messages.playerOnly(sender) : messages.consoleOnly(sender));
            return 0;
        }
        Map<String, Object> flags;
        try {
            flags = flagInput.isBlank() ? Map.of() : FlagParser.parse(flagInput, node.flags, f -> flagAllowed(f, sender));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(messages.flagError(sender, e.getMessage()));
            return 0;
        }
        UUID id = sender instanceof Player p ? p.getUniqueId() : null;
        Cooldowns<UUID> cooldown = null;
        if (node.cooldown != null && id != null && (node.cooldownBypass == null || !sender.hasPermission(node.cooldownBypass))) {
            cooldown = cooldowns.computeIfAbsent(node, n -> Cooldowns.of(n.cooldown));
            if (cooldown.isActive(id)) {
                sender.sendMessage(messages.cooldown(sender, cooldown.remaining(id)));
                return 0;
            }
        }
        if (node.confirm && !confirmed(sender, raw.getInput())) {
            sender.sendMessage(messages.confirm(sender, raw.getInput(), CONFIRM_WINDOW));
            return 0;
        }
        CommandContext ctx = new CommandContext(lib, this, raw, exec.args(), exec.defaults(), flags);
        Cooldowns<UUID> startCooldown = cooldown;
        Runnable run = () -> {
            if (invoke(node, ctx, sender) && startCooldown != null) {
                startCooldown.put(id);
            }
        };
        if (node.async) {
            lib.scheduler().runAsync(run);
        } else {
            run.run();
        }
        return Command.SINGLE_SUCCESS;
    }

    private boolean invoke(CommandNode<?> node, CommandContext ctx, CommandSender sender) {
        try {
            node.handler.handle(ctx);
            return true;
        } catch (CommandException e) {
            sender.sendMessage(e.message());
        } catch (Throwable t) {
            lib.logger().error("Command '/{}' failed for {}", ctx.input(), sender.getName(), t);
            sender.sendMessage(messages.internalError(sender));
        }
        return false;
    }

    private boolean confirmed(CommandSender sender, String input) {
        String key = (sender instanceof Player p ? p.getUniqueId().toString() : sender.getName()) + '\0' + input;
        long now = System.nanoTime();
        pendingConfirm.values().removeIf(until -> until < now);
        Long until = pendingConfirm.get(key);
        if (until != null && until >= now) {
            pendingConfirm.remove(key);
            return true;
        }
        pendingConfirm.put(key, now + CONFIRM_WINDOW.toNanos());
        return false;
    }

    private static boolean flagAllowed(Flag<?> flag, CommandSender sender) {
        return flag.permission() == null || sender.hasPermission(flag.permission());
    }

    // ================================================================== help

    private LiteralArgumentBuilder<CommandSourceStack> helpNode(LiteralNode root) {
        return Commands.literal("help")
                .executes(ctx -> help(root, ctx.getSource().getSender(), 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> help(root, ctx.getSource().getSender(), IntegerArgumentType.getInteger(ctx, "page"))));
    }

    private int help(LiteralNode root, CommandSender sender, int page) {
        List<Usage.Line> lines = Usage.lines(root, n -> n.visibleTo(sender));
        int pages = Usage.pages(lines.size(), HELP_PER_PAGE);
        int p = Math.min(page, pages);
        sender.sendMessage(messages.helpHeader(sender, root.name, p, pages));
        for (Usage.Line line : Usage.page(lines, p, HELP_PER_PAGE)) {
            String runnable = line.usage();
            int firstArg = indexOfAny(runnable, '<', '[');
            String suggestion = "/" + (firstArg < 0 ? runnable : runnable.substring(0, firstArg));
            Component c = messages.helpLine(sender, line.usage(), line.description())
                    .clickEvent(ClickEvent.suggestCommand(suggestion));
            sender.sendMessage(c);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int indexOfAny(String s, char a, char b) {
        int ia = s.indexOf(a);
        int ib = s.indexOf(b);
        if (ia < 0) {
            return ib;
        }
        return ib < 0 ? ia : Math.min(ia, ib);
    }
}
