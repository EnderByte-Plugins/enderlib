package com.enderbyte.enderlib.menu.pagination;

import com.enderbyte.enderlib.menu.layout.Slots;

/**
 * How a {@link Pagination} lays items out and how far one step moves.
 */
public enum PageLayout {

    /** Fill row by row; next/previous move a whole page. */
    PAGES,
    /** Fill row by row; next/previous scroll one row (the area must be rectangular for best results). */
    SCROLL_VERTICAL,
    /** Fill column by column; next/previous scroll one column. */
    SCROLL_HORIZONTAL;

    /** {@code slots} in the order items are placed. */
    public int[] order(int[] slots, int width) {
        return this == SCROLL_HORIZONTAL ? Slots.columnMajor(slots, width) : slots.clone();
    }

    /** Items skipped per next/previous. */
    public int step(int[] slots, int width) {
        int[] bounds = Slots.bounds(slots, width);
        return switch (this) {
            case PAGES -> Math.max(1, slots.length);
            case SCROLL_VERTICAL -> Math.max(1, bounds[0]);
            case SCROLL_HORIZONTAL -> Math.max(1, bounds[1]);
        };
    }
}
