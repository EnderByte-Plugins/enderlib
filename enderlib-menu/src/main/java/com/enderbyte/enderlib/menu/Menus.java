package com.enderbyte.enderlib.menu;

import com.enderbyte.enderlib.core.EnderLib;
import com.enderbyte.enderlib.core.Terminable;
import com.enderbyte.enderlib.menu.input.InputSlot;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Opens menus and routes inventory events to them. One instance per {@link EnderLib}.
 *
 * <pre>{@code
 * Menus menus = Menus.of(lib);
 * menus.open(player, shopMenu);
 * }</pre>
 *
 * <p>Clicks in menu slots are always cancelled before handlers run, shift-clicks and double-click
 * collection can never pull items out of a menu, and only {@link InputSlot}s accept items. On
 * shutdown every open menu is closed and items in input slots are returned.</p>
 */
public final class Menus implements Terminable {

    private final EnderLib lib;
    private final Map<UUID, MenuView> views = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<Menu>> history = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> dispatching = ConcurrentHashMap.newKeySet();

    private Menus(EnderLib lib) {
        this.lib = lib;
        registerListeners();
    }

    public static Menus of(EnderLib lib) {
        return lib.service(Menus.class, Menus::new);
    }

    public EnderLib lib() {
        return lib;
    }

    // ================================================================== opening

    /** Opens {@code menu} for {@code player}, clearing any back-navigation history. */
    public MenuView open(Player player, Menu menu) {
        return open(player, menu, false);
    }

    /**
     * Opens {@code menu}. With {@code remember}, the menu the player currently has open is pushed onto
     * the history so {@link MenuView#back()} returns to it.
     *
     * <p>Must be called on the player's thread; use {@link #openLater} from async code.</p>
     */
    public MenuView open(Player player, Menu menu, boolean remember) {
        if (!lib.scheduler().owns(player)) {
            throw new IllegalStateException("Menus must be opened on the player's thread; use openLater(...)");
        }
        MenuView current = views.get(player.getUniqueId());
        Deque<Menu> stack = history.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>());
        if (remember && current != null && !current.isFinished()) {
            stack.push(current.menu());
        } else if (!remember) {
            stack.clear();
        }
        MenuView view = new MenuView(this, menu, player);
        view.initialize();
        // Bukkit forbids opening inventories inside InventoryClickEvent: show it next tick instead
        runSafely(player, () -> show(player, view));
        return view;
    }

    private void show(Player player, MenuView view) {
        if (!player.isOnline() || view.isFinished()) {
            return;
        }
        MenuView current = views.get(player.getUniqueId());
        if (current != null && current != view && !current.isFinished()) {
            // The old view closes with OPEN_NEW, which must not wipe the history
            current.finish(InventoryCloseEvent.Reason.OPEN_NEW);
        }
        views.put(player.getUniqueId(), view);
        InventoryView opened = player.openInventory(view.inventory());
        if (opened == null) {
            views.remove(player.getUniqueId(), view);
            view.finish(InventoryCloseEvent.Reason.CANT_USE);
            return;
        }
        view.markOpened();
        for (var handler : view.menu().onOpen) {
            try {
                handler.accept(view);
            } catch (Throwable t) {
                lib.logger().error("onOpen handler of {} failed", view.menu(), t);
            }
        }
    }

    /** Whether a click handler of {@code player} is running right now. */
    boolean isDispatching(Player player) {
        return dispatching.contains(player.getUniqueId());
    }

    /** Runs {@code task} now, or next tick when called from inside a click handler. */
    void runSafely(Player player, Runnable task) {
        if (isDispatching(player)) {
            lib.scheduler().runAt(player, task);
        } else {
            task.run();
        }
    }

    /** Opens {@code menu} on the player's thread, from any thread. */
    public void openLater(Player player, Menu menu) {
        lib.scheduler().runAt(player, () -> {
            if (player.isOnline()) {
                open(player, menu);
            }
        });
    }

    /** Goes back one step in the player's history, or closes their menu. */
    public void back(Player player) {
        Deque<Menu> stack = history.get(player.getUniqueId());
        Menu previous = stack == null ? null : stack.poll();
        if (previous == null) {
            current(player).ifPresent(MenuView::close);
            return;
        }
        Deque<Menu> keep = new ArrayDeque<>(stack);
        open(player, previous, false);
        history.put(player.getUniqueId(), keep);
    }

    /** The menu view the player has open (or suspended), if any. */
    public Optional<MenuView> current(Player player) {
        MenuView view = views.get(player.getUniqueId());
        return view == null || view.isFinished() ? Optional.empty() : Optional.of(view);
    }

    public List<MenuView> openViews() {
        return List.copyOf(views.values());
    }

    /** Closes every open menu whose definition is {@code menu} (for example after its data changed). */
    public void closeAll(Menu menu) {
        for (MenuView view : openViews()) {
            if (view.menu() == menu) {
                view.close();
            }
        }
    }

    /** Re-renders every open view of {@code menu}. */
    public void refreshAll(Menu menu) {
        for (MenuView view : openViews()) {
            if (view.menu() == menu && view.isOpen()) {
                view.runOnViewer(view::refresh);
            }
        }
    }

    void reopen(MenuView view) {
        Player player = view.viewer();
        MenuView current = views.get(player.getUniqueId());
        if (current != null && current != view && !current.isFinished()) {
            current.finish(InventoryCloseEvent.Reason.OPEN_NEW);
        }
        views.put(player.getUniqueId(), view);
        runSafely(player, () -> {
            view.swapping(() -> player.openInventory(view.inventory()));
            view.markOpened();
        });
    }

    void finished(MenuView view, InventoryCloseEvent.Reason reason) {
        views.remove(view.viewer().getUniqueId(), view);
        if (reason != InventoryCloseEvent.Reason.OPEN_NEW) {
            history.remove(view.viewer().getUniqueId());
        }
    }

    @Override
    public void close() {
        for (MenuView view : openViews()) {
            try {
                view.close();
            } catch (Throwable t) {
                lib.logger().warn("Could not close {}", view, t);
            }
        }
        views.clear();
        history.clear();
    }

    // ================================================================== events

    private @Nullable MenuView viewOf(InventoryView view) {
        Inventory top = view.getTopInventory();
        if (top.getHolder(false) instanceof MenuHolder holder && holder.view().menus() == this) {
            return holder.view();
        }
        return null;
    }

    private void registerListeners() {
        lib.events().listen(InventoryClickEvent.class).priority(EventPriority.LOW).handler(this::onClick);
        lib.events().listen(InventoryDragEvent.class).priority(EventPriority.LOW).handler(this::onDrag);
        lib.events().listen(InventoryCloseEvent.class).priority(EventPriority.MONITOR).handler(e -> {
            if (e.getInventory().getHolder(false) instanceof MenuHolder holder && holder.view().menus() == this) {
                holder.view().handleClose(e.getReason());
            }
        });
        lib.events().listen(PlayerQuitEvent.class).priority(EventPriority.MONITOR).handler(e -> {
            MenuView view = views.remove(e.getPlayer().getUniqueId());
            if (view != null) {
                view.finish(InventoryCloseEvent.Reason.DISCONNECT);
            }
            history.remove(e.getPlayer().getUniqueId());
        });
    }

    private void onClick(InventoryClickEvent e) {
        MenuView view = viewOf(e.getView());
        if (view == null) {
            return;
        }
        Menu menu = view.menu();
        InventoryAction action = e.getAction();

        // Double-click collection scans every slot of the view, menu slots included: never allow it
        if (action == InventoryAction.COLLECT_TO_CURSOR) {
            e.setCancelled(true);
            return;
        }
        Inventory clicked = e.getClickedInventory();
        if (clicked == null) {
            e.setCancelled(true);
            if (menu.outsideClick != null) {
                menu.outsideClick.accept(view);
            }
            if (menu.closeOnOutsideClick) {
                view.close();
            }
            return;
        }
        Inventory top = e.getView().getTopInventory();
        if (clicked != top) {
            if (menu.lockPlayerInventory) {
                e.setCancelled(true);
            } else if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                e.setCancelled(true);
                routeShiftClick(view, e);
            }
            return;
        }

        int slot = e.getSlot();
        Button button = view.button(slot);
        if (button instanceof InputSlot input) {
            if (inputClickAllowed(view, input, e)) {
                view.scheduleInputCheck();
            } else {
                e.setCancelled(true);
            }
            return;
        }
        e.setCancelled(true);
        if (button == null || !view.acceptClick()) {
            return;
        }
        ClickContext ctx = new ClickContext(view, e, slot);
        UUID id = view.viewer().getUniqueId();
        dispatching.add(id);
        try {
            view.batch(() -> {
                try {
                    button.click(ctx);
                } catch (Throwable t) {
                    lib.logger().error("Click handler in slot {} of {} failed", slot, menu, t);
                }
            });
        } finally {
            dispatching.remove(id);
        }
    }

    private void onDrag(InventoryDragEvent e) {
        MenuView view = viewOf(e.getView());
        if (view == null) {
            return;
        }
        int topSize = e.getView().getTopInventory().getSize();
        boolean touchesTop = false;
        for (int raw : e.getRawSlots()) {
            if (raw >= topSize) {
                continue;
            }
            touchesTop = true;
            if (!(view.button(raw) instanceof InputSlot input) || !input.accepts(e.getOldCursor())) {
                e.setCancelled(true);
                return;
            }
            ItemStack result = e.getNewItems().get(raw);
            if (result != null && result.getAmount() > input.maxAmount()) {
                e.setCancelled(true);
                return;
            }
        }
        if (touchesTop) {
            view.scheduleInputCheck();
        } else if (view.menu().lockPlayerInventory) {
            e.setCancelled(true);
        }
    }

    private static boolean inputClickAllowed(MenuView view, InputSlot input, InventoryClickEvent e) {
        ItemStack inSlot = e.getCurrentItem();
        int present = inSlot == null || inSlot.isEmpty() ? 0 : inSlot.getAmount();
        ItemStack cursor = e.getCursor();
        return switch (e.getAction()) {
            case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME, DROP_ALL_SLOT, DROP_ONE_SLOT,
                 MOVE_TO_OTHER_INVENTORY, NOTHING -> true;
            case PLACE_ALL -> input.accepts(cursor) && present + cursor.getAmount() <= input.maxAmount();
            case PLACE_SOME, PLACE_ONE -> input.accepts(cursor) && present + 1 <= input.maxAmount();
            case SWAP_WITH_CURSOR -> input.accepts(cursor) && cursor.getAmount() <= input.maxAmount();
            case HOTBAR_SWAP -> {
                int button = e.getHotbarButton();
                ItemStack hotbar = button >= 0
                        ? e.getWhoClicked().getInventory().getItem(button)
                        : e.getWhoClicked().getInventory().getItemInOffHand();
                yield hotbar == null || hotbar.isEmpty()
                        || (input.accepts(hotbar) && hotbar.getAmount() <= input.maxAmount());
            }
            default -> false;
        };
    }

    /** Shift-click from the player's inventory: move into accepting input slots only. */
    private static void routeShiftClick(MenuView view, InventoryClickEvent e) {
        ItemStack moving = e.getCurrentItem();
        if (moving == null || moving.isEmpty()) {
            return;
        }
        Inventory top = view.inventory();
        List<Integer> inputs = new ArrayList<>();
        for (int s = 0; s < view.size(); s++) {
            if (view.button(s) instanceof InputSlot input && input.accepts(moving)) {
                inputs.add(s);
            }
        }
        if (inputs.isEmpty()) {
            return;
        }
        int remaining = moving.getAmount();
        // First top up similar stacks, then fill empty slots
        for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            for (int s : inputs) {
                InputSlot input = (InputSlot) view.button(s);
                int max = Math.min(input.maxAmount(), moving.getMaxStackSize());
                ItemStack there = top.getItem(s);
                boolean empty = there == null || there.isEmpty();
                if (pass == 0 && !empty && there.isSimilar(moving) && there.getAmount() < max) {
                    int add = Math.min(max - there.getAmount(), remaining);
                    there.setAmount(there.getAmount() + add);
                    top.setItem(s, there);
                    remaining -= add;
                } else if (pass == 1 && empty) {
                    int add = Math.min(max, remaining);
                    top.setItem(s, moving.asQuantity(add));
                    remaining -= add;
                }
                if (remaining == 0) {
                    break;
                }
            }
        }
        if (remaining != moving.getAmount()) {
            e.setCurrentItem(remaining == 0 ? null : moving.asQuantity(remaining));
            view.scheduleInputCheck();
        }
    }

    /** Players currently viewing any menu of this plugin. */
    public List<Player> viewers() {
        List<Player> out = new ArrayList<>();
        for (MenuView view : views.values()) {
            Player p = Bukkit.getPlayer(view.viewer().getUniqueId());
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }
}
