package com.enderbyte.enderlib.menu.pagination;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Supplies items to a {@link Pagination}, synchronously or asynchronously.
 *
 * <pre>{@code
 * PageSource.of(List.of(...));                       // fixed list
 * PageSource.of(() -> shop.products());               // re-read on every load / reload()
 * PageSource.async((offset, limit) -> database        // straight from the database
 *         .queryAsync("SELECT * FROM auctions ORDER BY id LIMIT ? OFFSET ?", limit + 1, offset)
 *         .thenApply(r -> {
 *             List<Auction> rows = r.map(Auction::from);
 *             boolean more = rows.size() > limit;
 *             return Page.unknownTotal(more ? rows.subList(0, limit) : rows, more);
 *         }));
 * }</pre>
 *
 * Futures may complete on any thread; the pagination renders on the viewer's thread.
 */
@FunctionalInterface
public interface PageSource<T> {

    CompletableFuture<Page<T>> load(int offset, int limit);

    static <T> PageSource<T> of(Collection<? extends T> items) {
        List<T> copy = List.copyOf(items);
        return (offset, limit) -> CompletableFuture.completedFuture(slice(copy, offset, limit));
    }

    /** Calls {@code items} on every load, so {@link Pagination#reload()} picks up changes. */
    static <T> PageSource<T> of(Supplier<? extends Collection<? extends T>> items) {
        Objects.requireNonNull(items, "items");
        return (offset, limit) -> CompletableFuture.completedFuture(slice(new ArrayList<>(items.get()), offset, limit));
    }

    static <T> PageSource<T> async(PageSource<T> loader) {
        return loader;
    }

    private static <T> Page<T> slice(List<? extends T> all, int offset, int limit) {
        int from = Math.min(Math.max(0, offset), all.size());
        int to = Math.min(all.size(), from + limit);
        return Page.of(new ArrayList<T>(all.subList(from, to)), all.size());
    }
}
