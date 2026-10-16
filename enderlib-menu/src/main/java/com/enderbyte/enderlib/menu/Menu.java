package com.enderbyte.enderlib.menu;

import com.enderbyte.enderlib.core.EnderLib;
import com.enderbyte.enderlib.menu.layout.SlotPattern;
import com.enderbyte.enderlib.menu.layout.Slots;
import com.enderbyte.enderlib.menu.pagination.PageLayout;
import com.enderbyte.enderlib.menu.pagination.PageSource;
import com.enderbyte.enderlib.menu.pagination.Pagination;
import com.enderbyte.enderlib.text.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * An immutable menu definition. One {@code Menu} can be opened by any number of players at once;
 * each gets their own {@link MenuView} with its own state.
 *
 * <pre>{@code
 * Menu shop = Menu.chest(6)
 *         .title("<gold>Shop <gray>(<page>/<pages>)")
 *         .pattern(
 *                 "#########",
 *                 "#.......#",
 *                 "#.......#",
 *                 "#.......#",
 *                 "#.......#",
 *                 "#<##X##>#")
 *         .ingredient('#', Button.filler(Material.BLACK_STAINED_GLASS_PANE))
 *         .ingredient('X', Button.close(closeIcon))
 *         .paginate('.', PageSource.of(products), (product, view) -> Button.of(product.icon())
 *                 .onClick(ctx -> buy(ctx.player(), product)))
 *         .ingredient('<', PageButtons.previous(prevIcon, null))
 *         .ingredient('>', PageButtons.next(nextIcon, null))
 *         .build();
 *
 * Menus.of(lib).open(player, shop);
 * }</pre>
 */
public final class Menu {

    final @Nullable String id;
    final @Nullable InventoryType type;
    final int size;
    final int width;
    final Function<MenuView, Component> title;
    final @Nullable UnaryOperator<Component> titleDecorator;
    final @Nullable SlotPattern pattern;
    final Map<Character, Button> ingredients;
    final Map<Integer, Button> slots;
    final @Nullable Button fill;
    final @Nullable Button border;
    final List<Consumer<MenuView>> setups;
    final List<Consumer<MenuView>> onOpen;
    final List<Consumer<MenuView>> onClose;
    final @Nullable Predicate<MenuView> closeGuard;
    final @Nullable Consumer<MenuView> outsideClick;
    final int refreshEvery;
    final int clickDelayTicks;
    final boolean lockPlayerInventory;
    final boolean closeOnOutsideClick;
    final boolean returnInputs;

    private Menu(Builder b) {
        this.id = b.id;
        this.type = b.type;
        this.size = b.size;
        this.width = b.width;
        this.title = b.title;
        this.titleDecorator = b.titleDecorator;
        this.pattern = b.pattern;
        this.ingredients = Map.copyOf(b.ingredients);
        this.slots = Map.copyOf(b.slots);
        this.fill = b.fill;
        this.border = b.border;
        this.setups = List.copyOf(b.setups);
        this.onOpen = List.copyOf(b.onOpen);
        this.onClose = List.copyOf(b.onClose);
        this.closeGuard = b.closeGuard;
        this.outsideClick = b.outsideClick;
        this.refreshEvery = b.refreshEvery;
        this.clickDelayTicks = b.clickDelayTicks;
        this.lockPlayerInventory = b.lockPlayerInventory;
        this.closeOnOutsideClick = b.closeOnOutsideClick;
        this.returnInputs = b.returnInputs;
    }

    /** A chest menu with {@code rows} rows (1-6). */
    public static Builder chest(int rows) {
        int r = Slots.clampRows(rows);
        return new Builder(null, r * Slots.COLUMNS, Slots.COLUMNS);
    }

    /** A menu of another inventory type: {@code HOPPER}, {@code DISPENSER}, {@code DROPPER}, ... */
    public static Builder of(InventoryType type) {
        if (type == InventoryType.CHEST) {
            return chest(3);
        }
        if (!type.isCreatable()) {
            throw new IllegalArgumentException(type + " cannot be used as a menu");
        }
        int size = type.getDefaultSize();
        int width = switch (type) {
            case HOPPER -> 5;
            case DISPENSER, DROPPER, CRAFTER -> 3;
            default -> Math.min(size, Slots.COLUMNS);
        };
        return new Builder(type, size, width);
    }

    public @Nullable String id() {
        return id;
    }

    public int size() {
        return size;
    }

    /** Number of columns. */
    public int width() {
        return width;
    }

    public @Nullable SlotPattern pattern() {
        return pattern;
    }

    /** Opens this menu for {@code player} through {@code lib}'s {@link Menus}. */
    public MenuView open(EnderLib lib, Player player) {
        return Menus.of(lib).open(player, this);
    }

    /** A builder pre-filled with this menu's settings, for variations. */
    public Builder toBuilder() {
        Builder b = new Builder(type, size, width);
        b.id = id;
        b.title = title;
        b.titleDecorator = titleDecorator;
        b.pattern = pattern;
        b.ingredients.putAll(ingredients);
        b.slots.putAll(slots);
        b.fill = fill;
        b.border = border;
        b.setups.addAll(setups);
        b.onOpen.addAll(onOpen);
        b.onClose.addAll(onClose);
        b.closeGuard = closeGuard;
        b.outsideClick = outsideClick;
        b.refreshEvery = refreshEvery;
        b.clickDelayTicks = clickDelayTicks;
        b.lockPlayerInventory = lockPlayerInventory;
        b.closeOnOutsideClick = closeOnOutsideClick;
        b.returnInputs = returnInputs;
        return b;
    }

    @Override
    public String toString() {
        return "Menu[" + (id != null ? id : "anonymous") + ", " + size + " slots]";
    }

    public static final class Builder {
        private @Nullable String id;
        private final @Nullable InventoryType type;
        private final int size;
        private final int width;
        private Function<MenuView, Component> title = v -> Component.empty();
        private @Nullable UnaryOperator<Component> titleDecorator;
        private @Nullable SlotPattern pattern;
        private final Map<Character, Button> ingredients = new LinkedHashMap<>();
        private final Map<Integer, Button> slots = new LinkedHashMap<>();
        private @Nullable Button fill;
        private @Nullable Button border;
        private final List<Consumer<MenuView>> setups = new ArrayList<>();
        private final List<Consumer<MenuView>> onOpen = new ArrayList<>();
        private final List<Consumer<MenuView>> onClose = new ArrayList<>();
        private @Nullable Predicate<MenuView> closeGuard;
        private @Nullable Consumer<MenuView> outsideClick;
        private int refreshEvery;
        private int clickDelayTicks = 2;
        private boolean lockPlayerInventory;
        private boolean closeOnOutsideClick;
        private boolean returnInputs = true;

        private Builder(@Nullable InventoryType type, int size, int width) {
            this.type = type;
            this.size = size;
            this.width = width;
        }

        /** Optional identifier, used by config-driven menus and debugging. */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * MiniMessage title. The tags {@code <page>} and {@code <pages>} resolve to the primary
         * pagination's position and update live.
         */
        public Builder title(String miniMessage, TagResolver... resolvers) {
            this.title = v -> Text.mm(miniMessage, TagResolver.resolver(TagResolver.resolver(resolvers), v.pageTags()));
            return this;
        }

        public Builder title(Component title) {
            Objects.requireNonNull(title, "title");
            this.title = v -> title;
            return this;
        }

        /** Title computed per viewer; reading {@link State}s inside makes it update live. */
        public Builder title(Function<MenuView, Component> title) {
            this.title = Objects.requireNonNull(title, "title");
            return this;
        }

        /**
         * Transforms the final title (after placeholders), for example to prepend a resource-pack
         * background glyph. Used by the ItemsAdder addon for textured menus.
         */
        public Builder titleDecorator(UnaryOperator<Component> decorator) {
            this.titleDecorator = decorator;
            return this;
        }

        public Builder pattern(String... rows) {
            SlotPattern p = SlotPattern.of(width, rows);
            if (p.rows() * width > size) {
                throw new IllegalArgumentException("Pattern has " + p.rows() + " rows; the menu has " + (size / width));
            }
            this.pattern = p;
            return this;
        }

        /** Places {@code button} on every slot of {@code c} in the pattern. */
        public Builder ingredient(char c, Button button) {
            ingredients.put(c, Objects.requireNonNull(button, "button"));
            return this;
        }

        public Builder slot(int slot, Button button) {
            if (slot < 0 || slot >= size) {
                throw new IllegalArgumentException("Slot " + slot + " is outside 0.." + (size - 1));
            }
            slots.put(slot, Objects.requireNonNull(button, "button"));
            return this;
        }

        public Builder slots(int[] slotList, Button button) {
            for (int s : slotList) {
                slot(s, button);
            }
            return this;
        }

        /** Places {@code button} in every slot nothing else claims. */
        public Builder fill(Button button) {
            this.fill = button;
            return this;
        }

        /** Places {@code button} around the edge (chest menus only), under anything else placed there. */
        public Builder border(Button button) {
            this.border = button;
            return this;
        }

        /**
         * Per-viewer setup, run when the menu opens and before the first render. Use it for content
         * that depends on the player: states, database-backed buttons, paginations.
         */
        public Builder setup(Consumer<MenuView> setup) {
            setups.add(Objects.requireNonNull(setup, "setup"));
            return this;
        }

        /** Paginates {@code items} across the pattern slots of {@code c}. */
        public <T> Builder paginate(char c, PageSource<T> source, BiFunction<T, MenuView, Button> renderer) {
            return paginate(c, source, renderer, PageLayout.PAGES, p -> { });
        }

        public <T> Builder paginate(char c, PageSource<T> source, BiFunction<T, MenuView, Button> renderer,
                                    PageLayout layout, Consumer<Pagination<T>> configure) {
            return setup(view -> configure.accept(view.paginate(c, source, renderer, layout)));
        }

        public Builder onOpen(Consumer<MenuView> handler) {
            onOpen.add(handler);
            return this;
        }

        public Builder onClose(Consumer<MenuView> handler) {
            onClose.add(handler);
            return this;
        }

        /**
         * Called when the player tries to close the menu (Escape). Return {@code false} to keep it
         * open. Navigation to another menu and plugin-initiated closes are never blocked.
         */
        public Builder closeGuard(Predicate<MenuView> guard) {
            this.closeGuard = guard;
            return this;
        }

        /** Re-renders every slot every {@code ticks} ticks. Prefer states or per-button intervals. */
        public Builder refreshEvery(int ticks) {
            this.refreshEvery = Math.max(0, ticks);
            return this;
        }

        /** Minimum ticks between two handled clicks of the same viewer (anti auto-clicker). Default 2. */
        public Builder clickDelay(int ticks) {
            this.clickDelayTicks = Math.max(0, ticks);
            return this;
        }

        /** Blocks every click in the player's own inventory while the menu is open. */
        public Builder lockPlayerInventory() {
            this.lockPlayerInventory = true;
            return this;
        }

        public Builder closeOnOutsideClick() {
            this.closeOnOutsideClick = true;
            return this;
        }

        public Builder onOutsideClick(Consumer<MenuView> handler) {
            this.outsideClick = handler;
            return this;
        }

        /** Whether items left in input slots go back to the player on close (default true). */
        public Builder returnInputs(boolean returnInputs) {
            this.returnInputs = returnInputs;
            return this;
        }

        public Menu build() {
            if (pattern != null) {
                for (char c : ingredients.keySet()) {
                    if (!pattern.contains(c)) {
                        throw new IllegalStateException("Ingredient '" + c + "' does not appear in the pattern " + pattern);
                    }
                }
            } else if (!ingredients.isEmpty()) {
                throw new IllegalStateException("Ingredients need a pattern(...)");
            }
            return new Menu(this);
        }
    }
}
