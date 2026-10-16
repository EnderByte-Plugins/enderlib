package com.enderbyte.enderlib.menu.pagination;

import java.util.List;

/**
 * One slice of items.
 *
 * @param items   the items starting at the requested offset (at most the requested limit)
 * @param total   total number of items, or -1 if unknown (then {@code hasMore} drives "next")
 * @param hasMore whether items exist past this slice; ignored when {@code total} is known
 */
public record Page<T>(List<T> items, int total, boolean hasMore) {

    public Page {
        items = List.copyOf(items);
    }

    public static <T> Page<T> of(List<T> items, int total) {
        return new Page<>(items, total, false);
    }

    /** For sources that cannot count cheaply (for example a keyset-paginated SQL query). */
    public static <T> Page<T> unknownTotal(List<T> items, boolean hasMore) {
        return new Page<>(items, -1, hasMore);
    }
}
