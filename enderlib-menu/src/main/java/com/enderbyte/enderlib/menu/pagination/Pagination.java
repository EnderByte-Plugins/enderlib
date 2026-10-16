package com.enderbyte.enderlib.menu.pagination;

import com.enderbyte.enderlib.menu.Button;
import com.enderbyte.enderlib.menu.MenuView;
import com.enderbyte.enderlib.menu.State;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Pages or scrolls a list of items across a set of slots of one {@link MenuView}.
 *
 * <p>Position, total and loading flag are {@link State}s, so navigation buttons and titles that read
 * them ({@link #hasNext()}, {@link #page()}, ...) update by themselves. Asynchronous sources show
 * {@link #loadingButton} while a page is in flight; a slower, outdated response never overwrites a
 * newer one.</p>
 *
 * Obtain one with {@link MenuView#paginate} or {@link com.enderbyte.enderlib.menu.Menu.Builder#paginate}.
 */
public final class Pagination<T> {

    private final MenuView view;
    private final int[] slots;
    private final int step;
    private final PageSource<T> source;
    private final BiFunction<T, MenuView, Button> renderer;
    private final State<Integer> offset;
    private final State<Integer> total;
    private final State<Boolean> hasMore;
    private final State<Boolean> loading;
    private @Nullable Button loadingButton;
    private @Nullable Button emptyButton;
    private List<T> items = List.of();
    private long request;

    private Pagination(MenuView view, int[] slots, PageLayout layout, PageSource<T> source,
                       BiFunction<T, MenuView, Button> renderer) {
        this.view = view;
        this.slots = layout.order(slots, view.menu().width());
        this.step = layout.step(slots, view.menu().width());
        this.source = Objects.requireNonNull(source, "source");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.offset = view.state(0);
        this.total = view.state(-1);
        this.hasMore = view.state(false);
        this.loading = view.state(false);
    }

    /** Internal: use {@link MenuView#paginate}. */
    public static <T> Pagination<T> attach(MenuView view, int[] slots, PageLayout layout, PageSource<T> source,
                                           BiFunction<T, MenuView, Button> renderer) {
        return new Pagination<>(view, slots, layout, source, renderer);
    }

    // ------------------------------------------------------------------ configuration

    /** Shown in every slot while an asynchronous page loads. */
    public Pagination<T> loadingButton(@Nullable Button button) {
        this.loadingButton = button;
        return this;
    }

    /** Shown in slots past the last item (default: empty). */
    public Pagination<T> emptyButton(@Nullable Button button) {
        this.emptyButton = button;
        return this;
    }

    // ------------------------------------------------------------------ queries (reactive)

    /** 1-based page / scroll position. */
    public int page() {
        return PageMath.page(offset.get(), step);
    }

    /** Page count, or -1 while unknown. */
    public int pages() {
        return PageMath.pages(total.get(), slots.length, step);
    }

    public boolean hasNext() {
        return PageMath.hasNext(offset.get(), total.get(), slots.length, hasMore.get());
    }

    public boolean hasPrevious() {
        return offset.get() > 0;
    }

    public boolean isLoading() {
        return loading.get();
    }

    /** Total items, or -1 if the source does not report it. */
    public int total() {
        return total.get();
    }

    /** Items currently shown. */
    public List<T> items() {
        return items;
    }

    public int pageSize() {
        return slots.length;
    }

    public int[] slots() {
        return slots.clone();
    }

    // ------------------------------------------------------------------ navigation

    public void next() {
        if (PageMath.hasNext(offset.peek(), total.peek(), slots.length, hasMore.peek())) {
            offset.set(offset.peek() + step);
            load();
        }
    }

    public void previous() {
        if (offset.peek() > 0) {
            offset.set(Math.max(0, offset.peek() - step));
            load();
        }
    }

    public void first() {
        goTo(1);
    }

    /** Jumps to the last page (only when the total is known). */
    public void last() {
        int pages = PageMath.pages(total.peek(), slots.length, step);
        if (pages > 0) {
            goTo(pages);
        }
    }

    public void goTo(int page) {
        int target = PageMath.offsetOf(page, total.peek(), slots.length, step);
        if (target != offset.peek()) {
            offset.set(target);
            load();
        }
    }

    /** Re-fetches the current page (after the underlying data changed). */
    public void reload() {
        load();
    }

    // ------------------------------------------------------------------ loading

    /** Loads the page at the current offset. Called automatically when the menu opens. */
    public void load() {
        long id = ++request;
        int at = offset.peek();
        java.util.concurrent.CompletableFuture<Page<T>> future;
        try {
            future = source.load(at, slots.length);
        } catch (RuntimeException e) {
            future = java.util.concurrent.CompletableFuture.failedFuture(e);
        }
        if (!future.isDone()) {
            loading.set(true);
            if (loadingButton != null) {
                for (int slot : slots) {
                    view.set(slot, loadingButton);
                }
            }
        }
        future.whenComplete((page, error) -> view.runOnViewer(() -> {
            if (id != request || !view.isActive()) {
                return; // outdated or closed
            }
            loading.set(false);
            if (error != null) {
                view.menus().lib().logger().error("Pagination source failed for {}", view.menu(), error);
                apply(Page.of(List.of(), 0));
                return;
            }
            if (page.items().isEmpty() && at > 0 && page.total() >= 0 && at >= page.total()) {
                // The data shrank under us: step back to the last real page
                offset.set(PageMath.offsetOf(Integer.MAX_VALUE, page.total(), slots.length, step));
                load();
                return;
            }
            apply(page);
        }));
    }

    private void apply(Page<T> page) {
        this.items = page.items();
        total.set(page.total());
        hasMore.set(page.hasMore());
        view.batch(() -> {
            for (int i = 0; i < slots.length; i++) {
                view.set(slots[i], i < items.size() ? renderer.apply(items.get(i), view) : emptyButton);
            }
        });
    }
}
