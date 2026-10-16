package com.enderbyte.enderlib.menu;

import com.enderbyte.enderlib.item.ItemBuilder;
import com.enderbyte.enderlib.menu.input.InputSlot;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Something that occupies a menu slot: it renders an item and reacts to clicks.
 *
 * <p>Buttons can be shared between menus and viewers; per-viewer data lives in the {@link MenuView}
 * (see {@link MenuView#state}).</p>
 *
 * <pre>{@code
 * Button.of(ItemBuilder.of(Material.EMERALD).name("<green>Buy"))
 *         .onLeft(ctx -> buy(ctx.player(), 1))
 *         .onShift(ctx -> buy(ctx.player(), 64))
 *         .sound(Sound.UI_BUTTON_CLICK)
 *         .cooldown(10);
 * }</pre>
 */
public interface Button {

    /** The item to show, or {@code null} for an empty slot. Called on the viewer's thread. */
    @Nullable ItemStack render(MenuView view);

    /** Handles a click. The event is already cancelled. */
    default void click(ClickContext ctx) {
    }

    /** Re-render every {@code n} ticks; 0 for never (static or state-driven). */
    default int updateInterval() {
        return 0;
    }

    /** Input slots let players put items in and take them out. */
    default boolean isInput() {
        return false;
    }

    // ================================================================== factories

    static ItemButton of(ItemStack item) {
        ItemStack copy = item.clone();
        return new ItemButton(v -> copy);
    }

    static ItemButton of(ItemBuilder item) {
        return of(item.build());
    }

    static ItemButton of(Material material) {
        return of(ItemStack.of(material));
    }

    /** An item computed on every render; reads of {@link State}s inside make it reactive. */
    static ItemButton dynamic(Function<MenuView, @Nullable ItemStack> renderer) {
        return new ItemButton(Objects.requireNonNull(renderer, "renderer"));
    }

    /** A clickable empty slot. */
    static ItemButton empty() {
        return new ItemButton(v -> null);
    }

    /** A decorative item with no tooltip (the classic glass-pane background). */
    static ItemButton filler(Material material) {
        return of(ItemBuilder.of(material).hideTooltip());
    }

    /** Flips {@code state} on click. */
    static ItemButton toggle(State<Boolean> state, ItemStack on, ItemStack off) {
        ItemStack onCopy = on.clone();
        ItemStack offCopy = off.clone();
        return new ItemButton(v -> state.get() ? onCopy : offCopy)
                .onClick(ctx -> state.set(!state.peek()));
    }

    /** Cycles {@code state} through {@code values}: left-click forward, right-click backward. */
    static <T> ItemButton cycle(State<T> state, List<T> values, Function<T, ItemStack> renderer) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        List<T> copy = List.copyOf(values);
        return new ItemButton(v -> renderer.apply(state.get())).onClick(ctx -> {
            int i = Math.max(0, copy.indexOf(state.peek()));
            int next = ctx.isRight() ? (i - 1 + copy.size()) % copy.size() : (i + 1) % copy.size();
            state.set(copy.get(next));
        });
    }

    /** Loops through {@code frames}, one every {@code ticksPerFrame} ticks. */
    static ItemButton animated(int ticksPerFrame, ItemStack... frames) {
        if (frames.length == 0) {
            throw new IllegalArgumentException("frames must not be empty");
        }
        int period = Math.max(1, ticksPerFrame);
        ItemStack[] copy = new ItemStack[frames.length];
        for (int i = 0; i < frames.length; i++) {
            copy[i] = frames[i].clone();
        }
        return new ItemButton(v -> copy[(int) ((v.ticksOpen() / period) % copy.length)]).updateEvery(period);
    }

    /** Closes the menu. */
    static ItemButton close(ItemStack icon) {
        return of(icon).onClick(ClickContext::close);
    }

    /** Goes back to the previous menu (or closes). */
    static ItemButton back(ItemStack icon) {
        return of(icon).onClick(ClickContext::back);
    }

    /** Opens another menu, remembering this one for back navigation. */
    static ItemButton opens(ItemStack icon, Menu menu) {
        return of(icon).onClick(ctx -> ctx.open(menu));
    }

    /** A slot players can place items into. */
    static InputSlot input() {
        return new InputSlot();
    }
}
