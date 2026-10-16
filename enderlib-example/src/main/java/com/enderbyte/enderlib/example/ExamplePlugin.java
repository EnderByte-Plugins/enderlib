package com.enderbyte.enderlib.example;

import com.enderbyte.enderlib.command.Args;
import com.enderbyte.enderlib.command.Cmd;
import com.enderbyte.enderlib.command.CommandManager;
import com.enderbyte.enderlib.command.Flag;
import com.enderbyte.enderlib.config.ConfigHandle;
import com.enderbyte.enderlib.config.Configs;
import com.enderbyte.enderlib.config.Messages;
import com.enderbyte.enderlib.core.EnderLib;
import com.enderbyte.enderlib.database.api.Database;
import com.enderbyte.enderlib.database.api.config.DatabaseType;
import com.enderbyte.enderlib.database.migration.MigrationSources;
import com.enderbyte.enderlib.display.ActionBars;
import com.enderbyte.enderlib.display.Holograms;
import com.enderbyte.enderlib.display.Sidebars;
import com.enderbyte.enderlib.item.CustomItem;
import com.enderbyte.enderlib.item.CustomItems;
import com.enderbyte.enderlib.item.ItemBuilder;
import com.enderbyte.enderlib.item.ItemConverters;
import com.enderbyte.enderlib.item.ItemTrigger;
import com.enderbyte.enderlib.menu.Button;
import com.enderbyte.enderlib.menu.Menu;
import com.enderbyte.enderlib.menu.Menus;
import com.enderbyte.enderlib.menu.State;
import com.enderbyte.enderlib.menu.input.TextInput;
import com.enderbyte.enderlib.menu.pagination.PageButtons;
import com.enderbyte.enderlib.menu.pagination.PageSource;
import com.enderbyte.enderlib.menu.template.ConfirmMenu;
import com.enderbyte.enderlib.text.Placeholders;
import com.enderbyte.enderlib.text.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Demonstrates every EnderLib module. Commands: {@code /ender menu|gem|holo|board|db}.
 */
public final class ExamplePlugin extends JavaPlugin {

    private EnderLib lib;
    private Database database;

    @Override
    public void onEnable() {
        lib = EnderLib.create(this);

        // ---------------------------------------------------------------- config & messages
        ConfigHandle<Settings> settings = Configs.of(lib).load("config.yml", Settings.class);
        Messages messages = Messages.load(lib);

        // ---------------------------------------------------------------- database
        database = Database.builder()
                .type(DatabaseType.H2)
                .name("example")
                .database(new File(getDataFolder(), "example").getAbsolutePath())
                .poolSize(settings.get().database.poolSize)
                .build()
                .connect();
        database.migrations()
                .source(MigrationSources.classpath("migrations", getClassLoader()))
                .migrateAndWait();
        ItemConverters.register(database.converters());

        lib.events().listen(PlayerJoinEvent.class).handler(e -> {
            Player player = e.getPlayer();
            database.executeAsync("MERGE INTO visits (uuid, name, joins) KEY (uuid) VALUES (?, ?, "
                            + "COALESCE((SELECT joins FROM visits WHERE uuid = ?), 0) + 1)",
                    player.getUniqueId(), player.getName(), player.getUniqueId())
                    .thenCompose(n -> database.queryAsync("SELECT joins FROM visits WHERE uuid = ?", player.getUniqueId()))
                    .thenAcceptAsync(result -> messages.send(player, "welcome", Placeholders.of()
                            .unparsed("player", player.getName())
                            .unparsed("joins", result.first().map(r -> r.getInt("joins")).orElse(1))
                            .build()), lib.scheduler().entity(player));
        });

        // ---------------------------------------------------------------- custom item
        CustomItem gem = CustomItem.builder("ender_gem")
                .item(ItemBuilder.of(Material.AMETHYST_SHARD).itemName("<light_purple>Ender Gem")
                        .lore("<gray>Right-click to zap.").glow())
                .on(ItemTrigger.RIGHT_CLICK, ctx -> {
                    ctx.player().getWorld().spawnParticle(Particle.PORTAL, ctx.player().getLocation(), 40);
                    messages.send(ctx.player(), "gem.zap");
                })
                .cooldown(Duration.ofSeconds(2))
                .unplaceable()
                .build();
        CustomItems.of(lib).register(gem);

        // ---------------------------------------------------------------- menu
        Menu numbers = Menu.chest(6)
                .title("<dark_purple>Numbers <gray>(<page>/<pages>)")
                .pattern(
                        "#########",
                        "#.......#",
                        "#.......#",
                        "#.......#",
                        "#.......#",
                        "#<#-C+#>#")
                .ingredient('#', Button.filler(Material.BLACK_STAINED_GLASS_PANE))
                .ingredient('<', PageButtons.previous(ItemBuilder.of(Material.ARROW).name("<yellow>Previous").build(), null))
                .ingredient('>', PageButtons.next(ItemBuilder.of(Material.ARROW).name("<yellow>Next").build(), null))
                .paginate('.', PageSource.of(IntStream.rangeClosed(1, 200).boxed().toList()),
                        (n, view) -> Button.of(ItemBuilder.of(Material.PAPER).name("<white>#" + n))
                                .onClick(ctx -> ctx.player().sendMessage(Text.mm("<gray>You picked <white>" + n))))
                .setup(view -> {
                    State<Integer> counter = view.state(1);
                    view.set('C', Button.dynamic(v -> ItemBuilder.of(Material.EXPERIENCE_BOTTLE, counter.get())
                                    .name("<green>Counter: " + counter.get()).build())
                            .onClick(ctx -> TextInput.builder(Component.text("Set the counter"))
                                    .initial(String.valueOf(counter.peek()))
                                    .validate(s -> s.matches("\\d{1,2}") && Integer.parseInt(s) >= 1, Text.mm("<red>1-64 please"))
                                    .prompt(view)
                                    .thenAccept(r -> r.ifPresent(s -> counter.set(Math.min(64, Integer.parseInt(s)))))));
                    view.set('-', Button.of(ItemBuilder.of(Material.RED_DYE).name("<red>-1"))
                            .onClick(ctx -> counter.update(c -> Math.max(1, c - 1))).sound(Sound.UI_BUTTON_CLICK));
                    view.set('+', Button.of(ItemBuilder.of(Material.LIME_DYE).name("<green>+1"))
                            .onClick(ctx -> counter.update(c -> Math.min(64, c + 1))).sound(Sound.UI_BUTTON_CLICK));
                })
                .build();

        // ---------------------------------------------------------------- commands
        CommandManager.of(lib).register(Cmd.command("ender")
                .description("EnderLib showcase")
                .withHelp()
                .then(Cmd.literal("menu").description("Open the demo menu").playerOnly()
                        .executes(ctx -> Menus.of(lib).open(ctx.player(), numbers)))
                .then(Cmd.literal("gem").description("Get Ender Gems").playerOnly()
                        .then(Cmd.arg("amount", Args.integer(1, 64)).optional(1)
                                .flag(Flag.of("silent", 's'))
                                .executes(ctx -> {
                                    int amount = ctx.get("amount");
                                    CustomItems.of(lib).give(ctx.player(), "ender_gem", amount);
                                    if (!ctx.flag("silent")) {
                                        messages.send(ctx.player(), "gem.given", Placeholders.of().unparsed("amount", amount).build());
                                    }
                                })))
                .then(Cmd.literal("holo").description("Spawn a personal hologram").playerOnly()
                        .executes(ctx -> Holograms.of(lib).create(ctx.player().getLocation().add(0, 2, 0))
                                .perPlayer(p -> List.of(Text.mm("<light_purple>Hello " + p.getName()),
                                        Text.mm("<gray>Ping: <white>" + p.getPing() + "ms")), 20)
                                .spawn()))
                .then(Cmd.literal("board").description("Toggle the sidebar").playerOnly()
                        .executes(ctx -> {
                            Player p = ctx.player();
                            if (Sidebars.of(lib).get(p).isPresent()) {
                                Sidebars.of(lib).remove(p);
                                return;
                            }
                            Sidebars.of(lib).create(p)
                                    .title("<gradient:light_purple:aqua><bold>ENDERLIB")
                                    .dynamicLines(pl -> List.of(
                                            Text.mm("<gray>Name: <white>" + pl.getName()),
                                            Component.empty(),
                                            Text.mm("<gray>Health: <red>" + Math.round(pl.getHealth())),
                                            Text.mm("<gray>Ping: <white>" + pl.getPing() + "ms")), 20)
                                    .show();
                            ActionBars.of(lib).show(p, "board", Text.mm("<aqua>Sidebar on"), 0, Duration.ofSeconds(3));
                        }))
                .then(Cmd.literal("reset").description("Forget all visits").permission("enderlib.example.admin")
                        .executes(ctx -> Menus.of(lib).open(ctx.player(), ConfirmMenu.builder(Text.mm("<red>Delete all visits?"))
                                .onConfirm(c -> {
                                    database.executeAsync("DELETE FROM visits");
                                    c.close();
                                })
                                .build()))));
    }

    @Override
    public void onDisable() {
        if (lib != null) {
            lib.shutdown();
        }
        if (database != null) {
            database.close();
        }
    }
}
