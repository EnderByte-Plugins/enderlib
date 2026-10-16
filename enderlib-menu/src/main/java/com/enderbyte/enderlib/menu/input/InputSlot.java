package com.enderbyte.enderlib.menu.input;

import com.enderbyte.enderlib.menu.Button;
import com.enderbyte.enderlib.menu.MenuView;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * A slot players can put items into (sell menus, upgrade stations, trade windows).
 *
 * <pre>{@code
 * view.set('I', Button.input()
 *         .accept(item -> item.getType() == Material.DIAMOND)
 *         .maxAmount(64)
 *         .onChange((v, item) -> price.set(calculate(item))));
 * }</pre>
 *
 * The rendered contents are whatever the player placed; the menu never overwrites them. By default
 * items still in input slots are returned to the player when the menu closes.
 */
public final class InputSlot implements Button {

    private Predicate<ItemStack> filter = item -> true;
    private int maxAmount = 64;
    private @Nullable ItemStack initial;
    private @Nullable BiConsumer<MenuView, @Nullable ItemStack> onChange;

    /** Only items matching {@code filter} may be placed. */
    public InputSlot accept(Predicate<ItemStack> filter) {
        this.filter = Objects.requireNonNull(filter, "filter");
        return this;
    }

    /** Upper bound on the stack size in this slot. */
    public InputSlot maxAmount(int maxAmount) {
        this.maxAmount = Math.max(1, Math.min(99, maxAmount));
        return this;
    }

    /** Item placed in the slot when the menu opens. */
    public InputSlot initial(@Nullable ItemStack initial) {
        this.initial = initial == null ? null : initial.clone();
        return this;
    }

    /** Called (one tick later, once the inventory settled) whenever the slot's contents change. */
    public InputSlot onChange(BiConsumer<MenuView, @Nullable ItemStack> listener) {
        this.onChange = listener;
        return this;
    }

    public boolean accepts(@Nullable ItemStack item) {
        return item == null || item.isEmpty() || filter.test(item);
    }

    public int maxAmount() {
        return maxAmount;
    }

    public @Nullable ItemStack initial() {
        return initial == null ? null : initial.clone();
    }

    public void changed(MenuView view, @Nullable ItemStack now) {
        if (onChange != null) {
            onChange.accept(view, now);
        }
    }

    @Override
    public @Nullable ItemStack render(MenuView view) {
        return null; // never painted: contents belong to the player
    }

    @Override
    public boolean isInput() {
        return true;
    }
}
