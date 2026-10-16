# Commands

`enderlib-command`. Commands are real Brigadier commands: the client validates, colours and
completes them, selectors like `@p` work, and permissions hide what a player cannot use.

Create the `CommandManager` and register commands **during `onEnable`**; Paper only accepts
command registration handlers while a plugin enables.

## Builder

```java
CommandManager.of(lib).register(Cmd.command("eco")
        .aliases("money")
        .description("Manage balances")
        .withHelp()                                       // /eco help [page]
        .executes(ctx -> ctx.reply("<gray>Balance: <gold>" + economy.balance(ctx.player())))
        .then(Cmd.literal("pay")
                .playerOnly()
                .cooldown(Duration.ofSeconds(3), "eco.bypass")
                .then(Cmd.arg("target", Args.player())
                        .then(Cmd.arg("amount", Args.decimal(0.01, 1_000_000))
                                .flag(Flag.of("silent", 's'))
                                .flag(Flag.value("note", 'n', s -> s))
                                .executes(ctx -> {
                                    Player target = ctx.get("target");
                                    double amount = ctx.get("amount");
                                    ctx.check(economy.has(ctx.player(), amount), "<red>Not enough money");
                                    economy.pay(ctx.player(), target, amount, ctx.flag("silent"));
                                }))))
        .then(Cmd.literal("reset")
                .permission("eco.admin")
                .confirm()                                // run twice within 10s
                .then(Cmd.arg("target", Args.offlinePlayer())
                        .executesAsync(ctx -> economy.reset(ctx.get("target"))))));
```

Node options: `executes`, `executesAsync`, `permission`, `requires(predicate)`, `playerOnly`,
`consoleOnly`, `cooldown`, `confirm`, `flag`, `description`, `hidden`, and on literals `aliases`.
Arguments: `.optional(default)` makes an argument (and everything after it) optional.

Context: `sender()`, `player()`, `get(name)`, `optional(name)`, `has(name)`, `flag(name)`,
`flagValue(name)`, `reply(...)`, `fail(...)`, `check(condition, message)`, `location()`, `raw()`.
Throwing `CommandException` (or `throw ctx.fail("...")`) shows the message without logging.

## Argument types

`Args.word`, `string`, `greedy`, `choice(...)`, `integer`, `longArg`, `decimal`, `floatArg`, `bool`,
`player`, `players`, `entity`, `entities`, `offlinePlayer`, `world`, `blockPosition`, `position`,
`gameMode`, `uuid`, `itemStack`, `component`, `color`, `key`, `registry(RegistryKey.X)`,
`enumArg(Type.class)`, `duration`, `custom(display, parser, suggestions)`, `map(arg, fn)`.

```java
Args.word().suggest(sender -> warps.names())
Args.custom("warp", warps::byName, warps::names)
Args.word().suggestAsync(sender -> database.queryAsync(...).thenApply(...))
```

## Flags

Flags follow the last argument: `--silent`, `-s`, `-sf`, `--reason "spam bot"`, `--reason=afk`,
`-d 1h`. They are completed in chat. `Flag.permission(...)` restricts a flag.

## Annotations

```java
@Command(value = "kit", aliases = "kits", help = true)
@Permission("kits.use")
public final class KitCommand {

    @Default
    @Description("List kits")
    public String list(CommandSender sender) {
        return "<gray>Kits: " + kits.names();            // returned text is sent to the sender
    }

    @Subcommand({"give", "g"})
    @Permission("kits.give")
    @Cooldown(5)
    public void give(Player sender, Player target, Kit kit,
                     @Opt("1") @Range(min = 1, max = 64) int amount,
                     @Flag(value = "silent", shortName = 's') boolean silent) { ... }

    @Subcommand("admin reload")
    @Permission("kits.admin")
    @Async
    public void reload(CommandSender sender) { ... }

    @Subcommand("preview")
    public void preview(Player sender, @Suggest(method = "kitNames") String name) { ... }

    Collection<String> kitNames() { return kits.names(); }
}

CommandManager commands = CommandManager.of(lib);
commands.parameterTypes().register(Kit.class, () -> Args.custom("kit", kits::byName, kits::names));
commands.register(new KitCommand());
```

A first `Player` parameter makes the path player-only; `CommandContext` parameters are injected;
other annotations: `@Arg("name")`, `@Greedy`, `@Quoted`, `@PlayerOnly`, `@ConsoleOnly`, `@Confirm`,
`@Hidden`, `@Suggest({"a", "b"})`.

## Messages

Override any framework text with `commands.messages(new CommandMessages() { ... })`, for example
to route through `Messages` for translations.
