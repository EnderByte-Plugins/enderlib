package com.enderbyte.enderlib.menu.template;

import com.enderbyte.enderlib.menu.ClickContext;
import com.enderbyte.enderlib.menu.Menu;
import com.enderbyte.enderlib.menu.pagination.Pagination;
import com.enderbyte.enderlib.text.Text;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Named click actions for config-driven menus.
 *
 * <p>Built in: {@code close}, {@code back}, {@code refresh}, {@code next-page}, {@code previous-page},
 * {@code open:<menu id>}, {@code command:<command>} (run by the player), {@code console:<command>},
 * {@code message:<minimessage>}, {@code sound:<key>}. In commands and messages {@code <player>} is
 * replaced by the clicker's name.</p>
 *
 * <pre>{@code
 * MenuActions actions = MenuActions.create()
 *         .menu("kits", kitsMenu)
 *         .register("buy", (ctx, arg) -> shop.buy(ctx.player(), arg));   // "buy:diamond_sword"
 * }</pre>
 */
public final class MenuActions {

    private final Map<String, BiConsumer<ClickContext, String>> actions = new ConcurrentHashMap<>();
    private final Map<String, Menu> menus = new ConcurrentHashMap<>();

    private MenuActions() {
        register("close", (ctx, arg) -> ctx.close());
        register("back", (ctx, arg) -> ctx.back());
        register("refresh", (ctx, arg) -> ctx.refresh());
        register("next-page", (ctx, arg) -> {
            Pagination<?> p = ctx.view().pagination();
            if (p != null) {
                p.next();
            }
        });
        register("previous-page", (ctx, arg) -> {
            Pagination<?> p = ctx.view().pagination();
            if (p != null) {
                p.previous();
            }
        });
        register("open", (ctx, arg) -> {
            Menu menu = menus.get(arg);
            if (menu == null) {
                throw new IllegalArgumentException("No menu registered as '" + arg + "'");
            }
            ctx.open(menu);
        });
        register("command", (ctx, arg) -> ctx.player().performCommand(stripSlash(withPlayer(ctx, arg))));
        register("console", (ctx, arg) -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), stripSlash(withPlayer(ctx, arg))));
        register("message", (ctx, arg) -> ctx.player().sendMessage(Text.mm(withPlayer(ctx, arg))));
        register("sound", (ctx, arg) -> ctx.player().playSound(
                Sound.sound(Key.key(arg.toLowerCase(Locale.ROOT)), Sound.Source.MASTER, 1f, 1f)));
    }

    public static MenuActions create() {
        return new MenuActions();
    }

    /** Registers (or replaces) an action. {@code arg} is the text after the first colon, or empty. */
    public MenuActions register(String name, BiConsumer<ClickContext, String> action) {
        actions.put(name.toLowerCase(Locale.ROOT), Objects.requireNonNull(action, "action"));
        return this;
    }

    /** Makes {@code menu} reachable through {@code open:<id>}. */
    public MenuActions menu(String id, Menu menu) {
        menus.put(id, menu);
        return this;
    }

    /** Compiles action strings into one click handler, validating names up front. */
    public Consumer<ClickContext> compile(List<String> specs) {
        List<Consumer<ClickContext>> steps = new ArrayList<>(specs.size());
        for (String spec : specs) {
            int colon = spec.indexOf(':');
            String name = (colon < 0 ? spec : spec.substring(0, colon)).trim().toLowerCase(Locale.ROOT);
            String arg = colon < 0 ? "" : spec.substring(colon + 1).trim();
            BiConsumer<ClickContext, String> action = actions.get(name);
            if (action == null) {
                throw new IllegalArgumentException("Unknown menu action '" + name + "' in \"" + spec + "\"");
            }
            steps.add(ctx -> action.accept(ctx, arg));
        }
        return ctx -> steps.forEach(step -> step.accept(ctx));
    }

    private static String withPlayer(ClickContext ctx, String s) {
        return s.replace("<player>", ctx.player().getName());
    }

    private static String stripSlash(String s) {
        return s.startsWith("/") ? s.substring(1) : s;
    }
}
