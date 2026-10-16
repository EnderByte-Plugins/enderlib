package com.enderbyte.enderlib.command;

/**
 * Entry point for building command trees.
 *
 * <pre>{@code
 * LiteralNode eco = Cmd.command("eco")
 *         .aliases("money")
 *         .description("Manage balances")
 *         .withHelp()
 *         .executes(ctx -> ctx.reply("<gray>Balance: <gold>" + economy.balance(ctx.player())))
 *         .then(Cmd.literal("pay")
 *                 .playerOnly()
 *                 .cooldown(Duration.ofSeconds(3))
 *                 .then(Cmd.arg("target", Args.player())
 *                         .then(Cmd.arg("amount", Args.decimal(0.01, 1_000_000))
 *                                 .flag(Flag.of("silent", 's'))
 *                                 .executes(ctx -> pay(ctx.player(), ctx.get("target"), ctx.get("amount"), ctx.flag("silent"))))));
 *
 * CommandManager.of(lib).register(eco);
 * }</pre>
 */
public final class Cmd {

    private Cmd() {
    }

    /** A root command (the label typed after the slash). */
    public static LiteralNode command(String label) {
        return new LiteralNode(label);
    }

    /** A subcommand word. */
    public static LiteralNode literal(String word) {
        return new LiteralNode(word);
    }

    /** A typed argument. */
    public static <T> ArgumentNode<T> arg(String name, Arg<T> type) {
        return new ArgumentNode<>(name, type);
    }
}
