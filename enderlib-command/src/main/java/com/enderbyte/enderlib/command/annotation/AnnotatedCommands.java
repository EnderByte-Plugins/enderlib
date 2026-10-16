package com.enderbyte.enderlib.command.annotation;

import com.enderbyte.enderlib.command.ArgumentNode;
import com.enderbyte.enderlib.command.Cmd;
import com.enderbyte.enderlib.command.CommandContext;
import com.enderbyte.enderlib.command.CommandException;
import com.enderbyte.enderlib.command.CommandHandler;
import com.enderbyte.enderlib.command.CommandNode;
import com.enderbyte.enderlib.command.LiteralNode;
import com.enderbyte.enderlib.text.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Turns an {@link Command}-annotated object into command trees.
 *
 * <pre>{@code
 * @Command(value = "eco", aliases = "money", help = true)
 * @Permission("eco.use")
 * public final class EconomyCommand {
 *
 *     @Default
 *     @Description("Shows your balance")
 *     public String balance(Player sender) {
 *         return "<gray>Balance: <gold>" + economy.balance(sender);   // returned text is sent to the sender
 *     }
 *
 *     @Subcommand({"pay", "send"})
 *     @Cooldown(3)
 *     public void pay(Player sender, Player target, @Range(min = 0.01) double amount,
 *                     @Flag(value = "silent", shortName = 's') boolean silent) { ... }
 *
 *     @Subcommand("admin set")
 *     @Permission("eco.admin")
 *     @Async
 *     public void set(CommandSender sender, OfflinePlayer target, double amount, @Opt("Manual edit") @Greedy String reason) { ... }
 * }
 * }</pre>
 *
 * <p>Parameter rules: an optional first {@code CommandSender}/{@code Player}/{@code ConsoleCommandSender}
 * receives the sender (a {@code Player} parameter makes the path player-only); a
 * {@link CommandContext} parameter receives the context; {@link Flag} parameters become flags; every
 * other parameter is an argument, in order. Returning a {@code String} (MiniMessage) or
 * {@code Component} replies with it.</p>
 */
public final class AnnotatedCommands {

    private AnnotatedCommands() {
    }

    public static List<LiteralNode> parse(Object handler, ParameterTypes types) {
        Class<?> type = handler.getClass();
        Command command = type.getAnnotation(Command.class);
        if (command == null) {
            throw new IllegalArgumentException(type.getName() + " is not annotated with @Command");
        }
        LiteralNode root = Cmd.command(command.value()).aliases(command.aliases()).description(command.description());
        if (command.help()) {
            root.withHelp();
        }
        Permission typePermission = type.getAnnotation(Permission.class);
        if (typePermission != null) {
            root.permission(typePermission.value());
        }
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        int found = 0;
        for (Method method : type.getDeclaredMethods()) {
            boolean isDefault = method.isAnnotationPresent(Default.class);
            Subcommand sub = method.getAnnotation(Subcommand.class);
            if (!isDefault && sub == null) {
                continue;
            }
            found++;
            try {
                addMethod(root, handler, method, isDefault ? new String[0] : sub.value(), types, lookup);
            } catch (IllegalArgumentException | IllegalStateException e) {
                throw new IllegalArgumentException("Invalid command method " + type.getSimpleName() + "#" + method.getName()
                        + ": " + e.getMessage(), e);
            }
        }
        if (found == 0) {
            throw new IllegalArgumentException(type.getName() + " has no @Default or @Subcommand methods");
        }
        return List.of(root);
    }

    // ================================================================== one method

    private enum Kind { SENDER, PLAYER, CONSOLE, CONTEXT, FLAG, ARGUMENT }

    private record Param(Kind kind, String name, Class<?> type) {
    }

    private static void addMethod(LiteralNode root, Object handler, Method method, String[] path, ParameterTypes types,
                                  MethodHandles.Lookup lookup) {
        Class<?> owner = handler.getClass();
        // ---- literal path
        CommandNode<?> current = root;
        String[] words = path.length == 0 || path[0].isBlank() ? new String[0] : path[0].trim().split("\\s+");
        for (String word : words) {
            current = literalChild(current, word.toLowerCase(Locale.ROOT));
        }
        if (current instanceof LiteralNode literal && path.length > 1) {
            literal.aliases(Arrays.copyOfRange(path, 1, path.length));
        }
        Permission permission = method.getAnnotation(Permission.class);
        boolean isRootDefault = current == root;
        if (permission != null && !isRootDefault) {
            ((CommandNode<?>) current).permission(permission.value());
        }

        // ---- parameters
        Parameter[] parameters = method.getParameters();
        List<Param> params = new ArrayList<>();
        List<CommandNode<?>> argNodes = new ArrayList<>();
        List<com.enderbyte.enderlib.command.Flag<?>> flags = new ArrayList<>();
        boolean sawOptional = false;
        for (int i = 0; i < parameters.length; i++) {
            Parameter p = parameters[i];
            Class<?> t = p.getType();
            if (i == 0 && (t == CommandSender.class || t == Player.class || t == ConsoleCommandSender.class)) {
                params.add(new Param(t == Player.class ? Kind.PLAYER : t == ConsoleCommandSender.class ? Kind.CONSOLE : Kind.SENDER, "", t));
                continue;
            }
            if (t == CommandContext.class) {
                params.add(new Param(Kind.CONTEXT, "", t));
                continue;
            }
            Flag flag = p.getAnnotation(Flag.class);
            if (flag != null) {
                flags.add(toFlag(flag, p, types));
                params.add(new Param(Kind.FLAG, flag.value(), t));
                continue;
            }
            Arg named = p.getAnnotation(Arg.class);
            String name = named != null ? named.value() : p.getName();
            com.enderbyte.enderlib.command.Arg<?> arg = types.resolve(p);
            applySuggest(arg, p, handler);
            @SuppressWarnings({"unchecked", "rawtypes"})
            ArgumentNode<Object> node = Cmd.arg(name, (com.enderbyte.enderlib.command.Arg) arg);
            Opt opt = p.getAnnotation(Opt.class);
            if (opt != null) {
                sawOptional = true;
                Object def = opt.value().isEmpty() ? zero(t) : parseDefault(arg, opt.value(), name);
                node.optional(def);
            } else if (sawOptional) {
                throw new IllegalArgumentException("required argument '" + name + "' follows an optional one");
            }
            if (arg.isGreedy() && i != parameters.length - 1 && !onlyFlagsAfter(parameters, i)) {
                throw new IllegalArgumentException("greedy argument '" + name + "' must be the last argument");
            }
            argNodes.add(node);
            params.add(new Param(Kind.ARGUMENT, name, t));
        }

        // ---- chain arguments under the literal path
        CommandNode<?> last = current;
        for (CommandNode<?> node : argNodes) {
            last.then(node);
            last = node;
        }

        // ---- behaviour on the executing node
        MethodHandle mh;
        try {
            method.setAccessible(true);
            mh = lookup.unreflect(method).bindTo(handler);
        } catch (IllegalAccessException | RuntimeException e) {
            throw new IllegalStateException("cannot access method: " + e.getMessage(), e);
        }
        String requiredPermission = isRootDefault && permission != null ? permission.value() : null;
        CommandHandler body = ctx -> {
            if (requiredPermission != null && !ctx.sender().hasPermission(requiredPermission)) {
                throw new CommandException(Text.mm("<red>You don't have permission to do that."));
            }
            Object result = invoke(mh, args(params, ctx));
            if (result instanceof Component c) {
                ctx.reply(c);
            } else if (result instanceof String s && !s.isEmpty()) {
                ctx.reply(s);
            }
        };
        boolean async = method.isAnnotationPresent(Async.class) || owner.isAnnotationPresent(Async.class);
        if (async) {
            last.executesAsync(body);
        } else {
            last.executes(body);
        }
        for (var f : flags) {
            last.flag(f);
        }
        Description description = method.getAnnotation(Description.class);
        if (description != null) {
            (words.length > 0 ? current : last).description(description.value());
        }
        Cooldown cooldown = method.isAnnotationPresent(Cooldown.class) ? method.getAnnotation(Cooldown.class) : owner.getAnnotation(Cooldown.class);
        if (cooldown != null) {
            if (cooldown.bypass().isEmpty()) {
                last.cooldown(Duration.ofSeconds(cooldown.value()));
            } else {
                last.cooldown(Duration.ofSeconds(cooldown.value()), cooldown.bypass());
            }
        }
        boolean playerParam = params.stream().anyMatch(p -> p.kind() == Kind.PLAYER);
        if (playerParam || method.isAnnotationPresent(PlayerOnly.class) || owner.isAnnotationPresent(PlayerOnly.class)) {
            last.playerOnly();
        }
        boolean consoleParam = params.stream().anyMatch(p -> p.kind() == Kind.CONSOLE);
        if (consoleParam || method.isAnnotationPresent(ConsoleOnly.class) || owner.isAnnotationPresent(ConsoleOnly.class)) {
            last.consoleOnly();
        }
        if (method.isAnnotationPresent(Confirm.class)) {
            last.confirm();
        }
        if (method.isAnnotationPresent(Hidden.class)) {
            last.hidden();
        }
    }

    private static Object[] args(List<Param> params, CommandContext ctx) {
        Object[] out = new Object[params.size()];
        for (int i = 0; i < out.length; i++) {
            Param p = params.get(i);
            out[i] = switch (p.kind()) {
                case SENDER -> ctx.sender();
                case PLAYER -> ctx.player();
                case CONSOLE -> ctx.sender();
                case CONTEXT -> ctx;
                case FLAG -> p.type() == boolean.class || p.type() == Boolean.class
                        ? ctx.flag(p.name())
                        : ctx.flagValue(p.name()).orElse(zero(p.type()));
                case ARGUMENT -> {
                    Object v = ctx.get(p.name());
                    yield v == null ? zero(p.type()) : v;
                }
            };
        }
        return out;
    }

    private static @Nullable Object invoke(MethodHandle mh, Object[] args) throws Exception {
        try {
            return mh.invokeWithArguments(args);
        } catch (Exception | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    // ================================================================== helpers

    private static CommandNode<?> literalChild(CommandNode<?> parent, String word) {
        for (CommandNode<?> child : parent.children()) {
            if (child instanceof LiteralNode literal && literal.name().equals(word)) {
                return literal;
            }
        }
        LiteralNode created = Cmd.literal(word);
        parent.then(created);
        return created;
    }

    private static com.enderbyte.enderlib.command.Flag<?> toFlag(Flag flag, Parameter p, ParameterTypes types) {
        boolean isSwitch = p.getType() == boolean.class || p.getType() == Boolean.class;
        com.enderbyte.enderlib.command.Flag<?> out;
        char shortName = flag.shortName();
        if (isSwitch) {
            out = shortName == '\0' ? com.enderbyte.enderlib.command.Flag.of(flag.value())
                    : com.enderbyte.enderlib.command.Flag.of(flag.value(), shortName);
        } else {
            com.enderbyte.enderlib.command.Arg<?> arg = types.resolve(p);
            if (!arg.parsesStrings()) {
                throw new IllegalArgumentException("flag --" + flag.value() + " has a type that cannot be parsed from text");
            }
            out = shortName == '\0' ? com.enderbyte.enderlib.command.Flag.value(flag.value(), arg::parse)
                    : com.enderbyte.enderlib.command.Flag.value(flag.value(), shortName, arg::parse);
        }
        if (!flag.permission().isEmpty()) {
            out.permission(flag.permission());
        }
        return out;
    }

    private static void applySuggest(com.enderbyte.enderlib.command.Arg<?> arg, Parameter p, Object handler) {
        Suggest suggest = p.getAnnotation(Suggest.class);
        if (suggest == null) {
            return;
        }
        if (!suggest.method().isEmpty()) {
            Method m = findSuggestMethod(handler.getClass(), suggest.method());
            m.setAccessible(true);
            boolean takesSender = m.getParameterCount() == 1;
            arg.suggest(sender -> {
                try {
                    @SuppressWarnings("unchecked")
                    Collection<String> values = (Collection<String>) (takesSender ? m.invoke(handler, sender) : m.invoke(handler));
                    return values;
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Suggestion method " + m.getName() + " failed", e);
                }
            });
        } else {
            List<String> values = List.of(suggest.value());
            arg.suggest(sender -> values);
        }
    }

    private static Method findSuggestMethod(Class<?> type, String name) {
        for (Method m : type.getDeclaredMethods()) {
            if (m.getName().equals(name) && Collection.class.isAssignableFrom(m.getReturnType())
                    && (m.getParameterCount() == 0 || m.getParameterCount() == 1 && m.getParameterTypes()[0] == CommandSender.class)) {
                return m;
            }
        }
        throw new IllegalArgumentException("@Suggest method '" + name + "' not found (it must return a Collection<String>"
                + " and take no parameters or a CommandSender)");
    }

    private static boolean onlyFlagsAfter(Parameter[] parameters, int index) {
        for (int j = index + 1; j < parameters.length; j++) {
            if (!parameters[j].isAnnotationPresent(Flag.class)) {
                return false;
            }
        }
        return true;
    }

    private static Object parseDefault(com.enderbyte.enderlib.command.Arg<?> arg, String value, String name) {
        if (!arg.parsesStrings()) {
            throw new IllegalArgumentException("argument '" + name + "' has a type whose default cannot be given as text");
        }
        try {
            return arg.parse(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("default '" + value + "' of '" + name + "' is invalid: " + e.getMessage(), e);
        }
    }

    private static @Nullable Object zero(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0d;
        if (type == float.class) return 0f;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        return '\0';
    }
}
