package com.enderbyte.enderlib.menu.pagination;

/**
 * Pure pagination arithmetic, shared by {@link Pagination} and tests.
 */
public final class PageMath {

    private PageMath() {
    }

    /** 1-based page (or scroll position) for an item offset. */
    public static int page(int offset, int step) {
        return offset / Math.max(1, step) + 1;
    }

    /** Number of pages, or -1 when the total is unknown. Never less than 1 for a known total. */
    public static int pages(int total, int visible, int step) {
        if (total < 0) {
            return -1;
        }
        if (total <= visible) {
            return 1;
        }
        int s = Math.max(1, step);
        return (total - visible + s - 1) / s + 1;
    }

    /** Offset of a 1-based page, clamped into range when the total is known. */
    public static int offsetOf(int page, int total, int visible, int step) {
        int p = Math.max(1, page);
        int pages = pages(total, visible, step);
        if (pages > 0) {
            p = Math.min(p, pages);
        }
        return (p - 1) * Math.max(1, step);
    }

    public static boolean hasNext(int offset, int total, int visible, boolean moreWhenUnknown) {
        return total < 0 ? moreWhenUnknown : offset + visible < total;
    }
}
