package com.enderbyte.enderlib.menu.template;

import com.enderbyte.enderlib.item.ItemBuilder;
import com.enderbyte.enderlib.menu.Button;
import com.enderbyte.enderlib.menu.ClickContext;
import com.enderbyte.enderlib.menu.Menu;
import com.enderbyte.enderlib.menu.layout.Slots;
import com.enderbyte.enderlib.menu.pagination.PageButtons;
import com.enderbyte.enderlib.menu.pagination.PageSource;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A paginated "pick one" menu.
 *
 * <pre>{@code
 * SelectorMenu.players("<gold>Teleport to...", (ctx, target) -> ctx.player().teleport(target))
 *         .build();
 *
 * SelectorMenu.of("<gold>Pick a kit", kits::all, kit -> kit.icon(), (ctx, kit) -> kit.give(ctx.player()))
 *         .rows(4)
 *         .build();
 * }</pre>
 */
public final class SelectorMenu<T> {

    private final String title;
    private final Supplier<? extends Collection<? extends T>> options;
    private final Function<T, ItemStack> icon;
    private final BiConsumer<ClickContext, T> onSelect;
    private int rows = 6;
    private ItemStack previous = ItemBuilder.of(Material.ARROW).name("<yellow>Previous page").build();
    private ItemStack next = ItemBuilder.of(Material.ARROW).name("<yellow>Next page").build();
    private ItemStack back = ItemBuilder.of(Material.BARRIER).name("<red>Back").build();
    private Button background = Button.filler(Material.BLACK_STAINED_GLASS_PANE);

    private SelectorMenu(String title, Supplier<? extends Collection<? extends T>> options,
                         Function<T, ItemStack> icon, BiConsumer<ClickContext, T> onSelect) {
        this.title = Objects.requireNonNull(title, "title");
        this.options = Objects.requireNonNull(options, "options");
        this.icon = Objects.requireNonNull(icon, "icon");
        this.onSelect = Objects.requireNonNull(onSelect, "onSelect");
    }

    /** Options are re-read every time the menu opens. */
    public static <T> SelectorMenu<T> of(String title, Supplier<? extends Collection<? extends T>> options,
                                         Function<T, ItemStack> icon, BiConsumer<ClickContext, T> onSelect) {
        return new SelectorMenu<>(title, options, icon, onSelect);
    }

    /** Online players (excluding the viewer is up to {@code onSelect}), sorted by name, shown as heads. */
    public static SelectorMenu<Player> players(String title, BiConsumer<ClickContext, Player> onSelect) {
        return new SelectorMenu<Player>(title, () -> {
            List<Player> sorted = new ArrayList<>(Bukkit.getOnlinePlayers());
            sorted.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
            return sorted;
        }, p -> ItemBuilder.of(Material.PLAYER_HEAD).name("<yellow>" + p.getName()).skull(p).build(), onSelect);
    }

    /** Constants of an enum, displayed with {@code icon}. */
    public static <E extends Enum<E>> SelectorMenu<E> enumValues(String title, Class<E> type, Function<E, ItemStack> icon,
                                                                 BiConsumer<ClickContext, E> onSelect) {
        List<E> values = List.of(type.getEnumConstants());
        return new SelectorMenu<>(title, () -> values, icon, onSelect);
    }

    public SelectorMenu<T> rows(int rows) {
        this.rows = Math.max(3, Slots.clampRows(rows));
        return this;
    }

    public SelectorMenu<T> navigationIcons(ItemStack previous, ItemStack next, ItemStack back) {
        this.previous = previous.clone();
        this.next = next.clone();
        this.back = back.clone();
        return this;
    }

    public SelectorMenu<T> background(Button background) {
        this.background = background;
        return this;
    }

    public Menu build() {
        String[] pattern = new String[rows];
        pattern[0] = "#########";
        for (int r = 1; r < rows - 1; r++) {
            pattern[r] = "#.......#";
        }
        pattern[rows - 1] = "#<##B##>#";
        return Menu.chest(rows)
                .id("selector")
                .title(title + " <dark_gray>(<page>/<pages>)")
                .pattern(pattern)
                .ingredient('#', background)
                .ingredient('<', PageButtons.previous(previous, null))
                .ingredient('>', PageButtons.next(next, null))
                .ingredient('B', Button.back(back))
                .paginate('.', PageSource.of(options), (option, view) -> Button.of(icon.apply(option))
                        .onClick(ctx -> onSelect.accept(ctx, option)))
                .build();
    }
}
