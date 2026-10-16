package com.enderbyte.enderlib.menu;

import com.enderbyte.enderlib.core.Terminable;
import com.enderbyte.enderlib.item.Inventories;
import com.enderbyte.enderlib.menu.input.InputSlot;
import com.enderbyte.enderlib.menu.layout.Slots;
import com.enderbyte.enderlib.menu.pagination.PageLayout;
import com.enderbyte.enderlib.menu.pagination.PageSource;
import com.enderbyte.enderlib.menu.pagination.Pagination;
import com.enderbyte.enderlib.text.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * One player's open instance of a {@link Menu}: its live slots, states and paginations.
 *
 * <p>Every method must be called on the viewer's thread (the main thread on Paper). Changes are
 * rendered immediately unless inside {@link #batch}, and only slots whose item actually changed are
 * sent to the client.</p>
 */
public final class MenuView {

    static final int NONE = -1;
    static final int TITLE = -2;

    private final Menus menus;
    private final Menu menu;
    private final Player viewer;
    private final MenuHolder holder;
    private @Nullable Inventory inventory;

    private final Button[] buttons;
    private final @Nullable ItemStack[] painted;
    private final @Nullable ItemStack[] inputSnapshot;
    private final BitSet dirty = new BitSet();
    private final Map<Integer, Integer> periodic = new HashMap<>();
    private final List<State<?>> states = new ArrayList<>();
    private final List<Pagination<?>> paginations = new ArrayList<>();
    private final Map<String, Object> data = new LinkedHashMap<>();
    private final Map<Integer, Integer> slotCooldowns = new HashMap<>();

    private Component title = Component.empty();
    private boolean titleDirty;
    private int tracking = NONE;
    private int batchDepth;
    private long ticksOpen;
    private int lastClickTick = Integer.MIN_VALUE;
    private @Nullable Terminable tickSubscription;
    private boolean open;
    private boolean suspended;
    private boolean navigating;
    private boolean forceClose;
    private boolean finished;
    private boolean inputCheckScheduled;

    MenuView(Menus menus, Menu menu, Player viewer) {
        this.menus = menus;
        this.menu = menu;
        this.viewer = viewer;
        this.holder = new MenuHolder(this);
        this.buttons = new Button[menu.size];
        this.painted = new ItemStack[menu.size];
        this.inputSnapshot = new ItemStack[menu.size];
    }

    // ================================================================== accessors

    public Menu menu() {
        return menu;
    }

    public Menus menus() {
        return menus;
    }

    public Player viewer() {
        return viewer;
    }

    public Inventory inventory() {
        if (inventory == null) {
            throw new IllegalStateException("The menu has not been opened yet");
        }
        return inventory;
    }

    public int size() {
        return menu.size;
    }

    /** Whether the viewer currently has this menu on screen. */
    public boolean isOpen() {
        return open && !finished;
    }

    /**
     * Whether the view is still alive: opening, open or suspended. Asynchronous work that finishes
     * later should check this rather than {@link #isOpen()}.
     */
    public boolean isActive() {
        return !finished;
    }

    /** Ticks this view has been ticking (drives animations); 0 for menus without animations. */
    public long ticksOpen() {
        return ticksOpen;
    }

    public Component title() {
        return title;
    }

    // ================================================================== slots

    /** Places a button (or clears the slot with {@code null}). */
    public void set(int slot, @Nullable Button button) {
        checkSlot(slot);
        Button previous = buttons[slot];
        buttons[slot] = button;
        if (previous != null && previous.updateInterval() > 0) {
            periodic.remove(slot);
        }
        if (button != null && button.updateInterval() > 0) {
            periodic.put(slot, button.updateInterval());
            ensureTicking();
        }
        if (button instanceof InputSlot input && inventory != null && previous != button) {
            ItemStack initial = input.initial();
            inventory.setItem(slot, initial);
            inputSnapshot[slot] = initial;
        }
        dirty.set(slot);
        flushIfIdle();
    }

    /** Places a button on every pattern slot of {@code c}. */
    public void set(char c, @Nullable Button button) {
        if (menu.pattern == null) {
            throw new IllegalStateException("This menu has no pattern");
        }
        batch(() -> {
            for (int slot : menu.pattern.slots(c)) {
                set(slot, button);
            }
        });
    }

    public void set(int[] slots, @Nullable Button button) {
        batch(() -> {
            for (int slot : slots) {
                set(slot, button);
            }
        });
    }

    public @Nullable Button button(int slot) {
        checkSlot(slot);
        return buttons[slot];
    }

    /** Slots of pattern character {@code c}. */
    public int[] slots(char c) {
        return menu.pattern == null ? new int[0] : menu.pattern.slots(c);
    }

    /** Fills every empty slot with {@code button}. */
    public void fillEmpty(Button button) {
        batch(() -> {
            for (int i = 0; i < buttons.length; i++) {
                if (buttons[i] == null) {
                    set(i, button);
                }
            }
        });
    }

    // ================================================================== state

    /** A reactive value scoped to this view. */
    public <T> State<T> state(T initial) {
        State<T> s = new State<>(this, initial);
        states.add(s);
        return s;
    }

    /** Arbitrary per-view data (for example the entity a menu was opened for). */
    @SuppressWarnings("unchecked")
    public <T> @Nullable T data(String key) {
        return (T) data.get(key);
    }

    public MenuView data(String key, @Nullable Object value) {
        if (value == null) {
            data.remove(key);
        } else {
            data.put(key, value);
        }
        return this;
    }

    // ================================================================== pagination

    /** Paginates {@code source} across the pattern slots of {@code c}. */
    public <T> Pagination<T> paginate(char c, PageSource<T> source, BiFunction<T, MenuView, Button> renderer) {
        return paginate(slots(c), source, renderer, PageLayout.PAGES);
    }

    public <T> Pagination<T> paginate(char c, PageSource<T> source, BiFunction<T, MenuView, Button> renderer, PageLayout layout) {
        return paginate(slots(c), source, renderer, layout);
    }

    public <T> Pagination<T> paginate(int[] slots, PageSource<T> source, BiFunction<T, MenuView, Button> renderer, PageLayout layout) {
        if (slots.length == 0) {
            throw new IllegalArgumentException("Pagination needs at least one slot");
        }
        Pagination<T> p = Pagination.attach(this, slots, layout, source, renderer);
        paginations.add(p);
        p.load();
        return p;
    }

    /** The first pagination created on this view, used by {@link com.enderbyte.enderlib.menu.pagination.PageButtons}. */
    public @Nullable Pagination<?> pagination() {
        return paginations.isEmpty() ? null : paginations.getFirst();
    }

    public List<Pagination<?>> paginations() {
        return List.copyOf(paginations);
    }

    /** {@code <page>} and {@code <pages>} tags for titles; reactive when read during a render. */
    public TagResolver pageTags() {
        Pagination<?> p = pagination();
        int page = p == null ? 1 : p.page();
        int pages = p == null ? 1 : Math.max(p.pages(), page);
        return TagResolver.resolver(Placeholder.unparsed("page", String.valueOf(page)),
                Placeholder.unparsed("pages", String.valueOf(pages)));
    }

    // ================================================================== rendering

    /** Re-renders every slot and the title. */
    public void refresh() {
        dirty.set(0, buttons.length);
        titleDirty = true;
        flushIfIdle();
    }

    public void refresh(int slot) {
        checkSlot(slot);
        dirty.set(slot);
        flushIfIdle();
    }

    /** Re-evaluates the title. */
    public void refreshTitle() {
        titleDirty = true;
        flushIfIdle();
    }

    /** Groups changes into one render pass. */
    public void batch(Runnable changes) {
        batchDepth++;
        try {
            changes.run();
        } finally {
            if (--batchDepth == 0) {
                flush();
            }
        }
    }

    /** Runs {@code task} on the viewer's thread (inline if already there). */
    public void runOnViewer(Runnable task) {
        if (menus.lib().scheduler().owns(viewer)) {
            task.run();
        } else {
            menus.lib().scheduler().runAt(viewer, task);
        }
    }

    void track(State<?> state) {
        if (tracking != NONE) {
            state.addDependent(tracking);
        }
    }

    void invalidate(State<?> state) {
        BitSet deps = state.dependents();
        dirty.or(deps);
        if (state.titleDependent()) {
            titleDirty = true;
        }
        flushIfIdle();
    }

    private void flushIfIdle() {
        if (batchDepth == 0) {
            flush();
        }
    }

    private void flush() {
        if (inventory == null || finished) {
            return;
        }
        for (int slot = dirty.nextSetBit(0); slot >= 0; slot = dirty.nextSetBit(slot + 1)) {
            renderSlot(slot);
        }
        dirty.clear();
        if (titleDirty) {
            titleDirty = false;
            applyTitle(renderTitle());
        }
    }

    private void renderSlot(int slot) {
        Button button = buttons[slot];
        if (button != null && button.isInput()) {
            return;
        }
        for (State<?> s : states) {
            s.clearDependent(slot);
        }
        ItemStack item = null;
        if (button != null) {
            tracking = slot;
            try {
                item = button.render(this);
            } catch (Throwable t) {
                menus.lib().logger().error("Button in slot {} of {} failed to render", slot, menu, t);
            } finally {
                tracking = NONE;
            }
        }
        if (!Objects.equals(painted[slot], item)) {
            inventory.setItem(slot, item);
            painted[slot] = item;
        }
    }

    private Component renderTitle() {
        tracking = TITLE;
        Component t;
        try {
            t = menu.title.apply(this);
        } catch (Throwable e) {
            menus.lib().logger().error("Title of {} failed to render", menu, e);
            t = Component.empty();
        } finally {
            tracking = NONE;
        }
        return menu.titleDecorator == null ? t : menu.titleDecorator.apply(t);
    }

    private void applyTitle(Component next) {
        if (next.equals(title)) {
            return;
        }
        title = next;
        if (!isOpen() || suspended) {
            return;
        }
        if (usesCustomFont(next)) {
            // Legacy titles cannot carry fonts (resource-pack backgrounds): rebuild the inventory instead
            menus.runSafely(viewer, this::reopenWithNewInventory);
        } else {
            InventoryView open = viewer.getOpenInventory();
            if (open.getTopInventory() == inventory) {
                open.setTitle(Text.toLegacy(next));
            }
        }
    }

    private static boolean usesCustomFont(Component c) {
        if (c.style().font() != null) {
            return true;
        }
        for (Component child : c.children()) {
            if (usesCustomFont(child)) {
                return true;
            }
        }
        return false;
    }

    // ================================================================== lifecycle (package)

    /** Builds slots, runs setups and creates the inventory. Called once by {@link Menus}. */
    void initialize() {
        batchDepth++;
        try {
            if (menu.border != null && menu.type == null) {
                for (int s : Slots.border(menu.size / Slots.COLUMNS)) {
                    set(s, menu.border);
                }
            }
            if (menu.fill != null) {
                for (int s = 0; s < buttons.length; s++) {
                    if (buttons[s] == null) {
                        set(s, menu.fill);
                    }
                }
            }
            if (menu.pattern != null) {
                menu.ingredients.forEach((c, b) -> {
                    for (int s : menu.pattern.slots(c)) {
                        set(s, b);
                    }
                });
            }
            menu.slots.forEach(this::set);
            for (var setup : menu.setups) {
                setup.accept(this);
            }
        } finally {
            batchDepth--;
        }
        title = renderTitle();
        inventory = menu.type == null
                ? Bukkit.createInventory(holder, menu.size, title)
                : Bukkit.createInventory(holder, menu.type, title);
        for (int s = 0; s < buttons.length; s++) {
            if (buttons[s] instanceof InputSlot input) {
                ItemStack initial = input.initial();
                inventory.setItem(s, initial);
                inputSnapshot[s] = initial;
            }
        }
        dirty.set(0, buttons.length);
        flush();
        if (menu.refreshEvery > 0) {
            ensureTicking();
        }
    }

    void markOpened() {
        open = true;
        suspended = false;
    }

    boolean isNavigating() {
        return navigating;
    }

    /** Opens a new inventory for the viewer without treating the old one as closed. */
    void swapping(Runnable open) {
        navigating = true;
        try {
            open.run();
        } finally {
            navigating = false;
        }
    }

    // ================================================================== navigation (public)

    /** Closes the menu. Close guards do not apply. */
    public void close() {
        if (finished) {
            return;
        }
        if (menus.isDispatching(viewer)) {
            menus.runSafely(viewer, this::close);
            return;
        }
        forceClose = true;
        if (open && viewer.getOpenInventory().getTopInventory() == inventory) {
            viewer.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
        } else {
            finish(InventoryCloseEvent.Reason.PLUGIN);
        }
    }

    /** Opens {@code next}; {@link #back()} from there returns here (as a fresh view). */
    public MenuView open(Menu next) {
        return menus.open(viewer, next, true);
    }

    /** Returns to the previous menu, or closes if there is none. */
    public void back() {
        menus.back(viewer);
    }

    /**
     * Hides the menu but keeps this view (states, page, inputs) alive, for example while the player
     * types into a chat prompt or a dialog. Call {@link #resume()} to show it again.
     */
    public void suspend() {
        if (!isOpen()) {
            return;
        }
        if (menus.isDispatching(viewer)) {
            menus.runSafely(viewer, this::suspend);
            return;
        }
        suspended = true;
        swapping(() -> viewer.closeInventory(InventoryCloseEvent.Reason.PLUGIN));
        open = false;
    }

    /** Shows a suspended view again, exactly as it was. */
    public void resume() {
        if (finished || isOpen()) {
            return;
        }
        runOnViewer(() -> {
            if (!viewer.isOnline() || finished) {
                finish(InventoryCloseEvent.Reason.DISCONNECT);
                return;
            }
            menus.reopen(this);
        });
    }

    public boolean isSuspended() {
        return suspended && !finished;
    }

    // ================================================================== inputs

    /** Current contents of every input slot (empty slots omitted). */
    public Map<Integer, ItemStack> inputs() {
        Map<Integer, ItemStack> out = new LinkedHashMap<>();
        if (inventory == null) {
            return out;
        }
        for (int s = 0; s < buttons.length; s++) {
            if (buttons[s] instanceof InputSlot) {
                ItemStack item = inventory.getItem(s);
                if (item != null && !item.isEmpty()) {
                    out.put(s, item);
                }
            }
        }
        return out;
    }

    /** Empties every input slot (for example after consuming the items). */
    public void clearInputs() {
        if (inventory == null) {
            return;
        }
        for (int s = 0; s < buttons.length; s++) {
            if (buttons[s] instanceof InputSlot) {
                inventory.setItem(s, null);
            }
        }
        scheduleInputCheck();
    }

    void scheduleInputCheck() {
        if (inputCheckScheduled) {
            return;
        }
        inputCheckScheduled = true;
        menus.lib().scheduler().runAtLater(viewer, () -> {
            inputCheckScheduled = false;
            checkInputs();
        }, 1L);
    }

    private void checkInputs() {
        if (inventory == null || finished) {
            return;
        }
        for (int s = 0; s < buttons.length; s++) {
            if (buttons[s] instanceof InputSlot input) {
                ItemStack now = inventory.getItem(s);
                ItemStack normalised = now == null || now.isEmpty() ? null : now;
                if (!Objects.equals(inputSnapshot[s], normalised)) {
                    inputSnapshot[s] = normalised == null ? null : normalised.clone();
                    try {
                        input.changed(this, normalised);
                    } catch (Throwable t) {
                        menus.lib().logger().error("Input slot {} listener of {} failed", s, menu, t);
                    }
                }
            }
        }
    }

    // ================================================================== clicks

    /** Global anti-spam: whether a click at the current tick may be handled. */
    boolean acceptClick() {
        int now = Bukkit.getCurrentTick();
        if (!clickAllowed(now, lastClickTick, menu.clickDelayTicks)) {
            return false;
        }
        lastClickTick = now;
        return true;
    }

    /** Overflow-safe: {@code lastClick} starts at {@link Integer#MIN_VALUE} ("never clicked"). */
    static boolean clickAllowed(int now, int lastClick, int delayTicks) {
        return delayTicks <= 0 || (long) now - lastClick >= delayTicks;
    }

    /** Per-button cooldown, used by {@link ItemButton#cooldown}. */
    boolean tryClickCooldown(int slot, int ticks) {
        int now = Bukkit.getCurrentTick();
        Integer until = slotCooldowns.get(slot);
        if (until != null && now < until) {
            return false;
        }
        slotCooldowns.put(slot, now + ticks);
        return true;
    }

    // ================================================================== close handling

    /** Called by {@link Menus} on InventoryCloseEvent. */
    void handleClose(InventoryCloseEvent.Reason reason) {
        if (navigating || finished) {
            return;
        }
        boolean playerInitiated = reason == InventoryCloseEvent.Reason.PLAYER;
        if (playerInitiated && !forceClose && menu.closeGuard != null) {
            boolean allow;
            try {
                allow = menu.closeGuard.test(this);
            } catch (Throwable t) {
                menus.lib().logger().error("Close guard of {} failed; closing", menu, t);
                allow = true;
            }
            if (!allow) {
                open = false;
                menus.lib().scheduler().runAtLater(viewer, () -> {
                    if (!finished && viewer.isOnline()) {
                        menus.reopen(this);
                    }
                }, 1L);
                return;
            }
        }
        finish(reason);
    }

    void finish(InventoryCloseEvent.Reason reason) {
        if (finished) {
            return;
        }
        finished = true;
        open = false;
        if (tickSubscription != null) {
            tickSubscription.close();
        }
        if (menu.returnInputs && inventory != null && viewer.isOnline()) {
            List<ItemStack> leftovers = new ArrayList<>();
            for (int s = 0; s < buttons.length; s++) {
                if (buttons[s] instanceof InputSlot) {
                    ItemStack item = inventory.getItem(s);
                    if (item != null && !item.isEmpty()) {
                        leftovers.add(item);
                        inventory.setItem(s, null);
                    }
                }
            }
            if (!leftovers.isEmpty()) {
                Inventories.giveOrDrop(viewer, leftovers.toArray(ItemStack[]::new));
            }
        }
        for (var handler : menu.onClose) {
            try {
                handler.accept(this);
            } catch (Throwable t) {
                menus.lib().logger().error("onClose handler of {} failed", menu, t);
            }
        }
        menus.finished(this, reason);
    }

    boolean isFinished() {
        return finished;
    }

    // ================================================================== ticking

    private void ensureTicking() {
        if (tickSubscription != null || finished) {
            return;
        }
        tickSubscription = menus.lib().ticker().subscribe(viewer, tick -> {
            if (!isOpen()) {
                return;
            }
            ticksOpen++;
            if (menu.refreshEvery > 0 && ticksOpen % menu.refreshEvery == 0) {
                dirty.set(0, buttons.length);
                titleDirty = true;
            }
            periodic.forEach((slot, period) -> {
                if (ticksOpen % period == 0) {
                    dirty.set(slot);
                }
            });
            flushIfIdle();
        });
    }

    private void reopenWithNewInventory() {
        if (!isOpen() || suspended) {
            return;
        }
        Inventory old = inventory;
        Inventory fresh = menu.type == null
                ? Bukkit.createInventory(holder, menu.size, title)
                : Bukkit.createInventory(holder, menu.type, title);
        fresh.setContents(old.getContents());
        inventory = fresh;
        swapping(() -> viewer.openInventory(fresh));
    }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= buttons.length) {
            throw new IndexOutOfBoundsException("Slot " + slot + " is outside 0.." + (buttons.length - 1));
        }
    }

    @Override
    public String toString() {
        return "MenuView[" + menu + " for " + viewer.getName() + "]";
    }
}
