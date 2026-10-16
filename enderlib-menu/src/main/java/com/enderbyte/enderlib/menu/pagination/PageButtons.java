package com.enderbyte.enderlib.menu.pagination;

import com.enderbyte.enderlib.menu.Button;
import com.enderbyte.enderlib.menu.ItemButton;
import com.enderbyte.enderlib.menu.MenuView;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Navigation buttons for the view's primary {@link Pagination} (the first one created).
 * They hide or switch icon by themselves when there is nowhere to go.
 */
public final class PageButtons {

    private PageButtons() {
    }

    /**
     * @param icon     shown when a next page exists
     * @param disabled shown otherwise; {@code null} leaves the slot empty
     */
    public static ItemButton next(ItemStack icon, @Nullable ItemStack disabled) {
        ItemStack on = icon.clone();
        ItemStack off = disabled == null ? null : disabled.clone();
        return Button.dynamic(v -> primary(v) != null && primary(v).hasNext() ? on : off)
                .onClick(ctx -> {
                    Pagination<?> p = primary(ctx.view());
                    if (p != null) {
                        p.next();
                    }
                });
    }

    public static ItemButton previous(ItemStack icon, @Nullable ItemStack disabled) {
        ItemStack on = icon.clone();
        ItemStack off = disabled == null ? null : disabled.clone();
        return Button.dynamic(v -> primary(v) != null && primary(v).hasPrevious() ? on : off)
                .onClick(ctx -> {
                    Pagination<?> p = primary(ctx.view());
                    if (p != null) {
                        p.previous();
                    }
                });
    }

    /** Jumps to the first page on left click, the last page on right click. */
    public static ItemButton jump(ItemStack icon) {
        return Button.of(icon).onClick(ctx -> {
            Pagination<?> p = primary(ctx.view());
            if (p != null) {
                if (ctx.isRight()) {
                    p.last();
                } else {
                    p.first();
                }
            }
        });
    }

    private static @Nullable Pagination<?> primary(MenuView view) {
        return view.pagination();
    }
}
