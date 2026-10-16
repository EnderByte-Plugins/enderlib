package com.enderbyte.enderlib.menu;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A click on a {@link Button}.
 */
public final class ClickContext {

    private final MenuView view;
    private final InventoryClickEvent event;
    private final int slot;

    ClickContext(MenuView view, InventoryClickEvent event, int slot) {
        this.view = view;
        this.event = event;
        this.slot = slot;
    }

    public MenuView view() {
        return view;
    }

    public Player player() {
        return view.viewer();
    }

    public int slot() {
        return slot;
    }

    public ClickType type() {
        return event.getClick();
    }

    public InventoryAction action() {
        return event.getAction();
    }

    public InventoryClickEvent event() {
        return event;
    }

    /** The item shown in the clicked slot. */
    public @Nullable ItemStack item() {
        return event.getCurrentItem();
    }

    public ItemStack cursor() {
        return event.getCursor();
    }

    /** Number key pressed for {@link ClickType#NUMBER_KEY} (0-8), otherwise -1. */
    public int hotbarButton() {
        return event.getHotbarButton();
    }

    public boolean isLeft() {
        return type().isLeftClick();
    }

    public boolean isRight() {
        return type().isRightClick();
    }

    public boolean isShift() {
        return type().isShiftClick();
    }

    public boolean isMiddle() {
        return type() == ClickType.MIDDLE;
    }

    public boolean isDrop() {
        return type() == ClickType.DROP || type() == ClickType.CONTROL_DROP;
    }

    // ------------------------------------------------------------------ shortcuts

    public void close() {
        view.close();
    }

    /** Opens {@code menu}, remembering this one for {@link #back()}. */
    public void open(Menu menu) {
        view.open(menu);
    }

    /** Returns to the previous menu, or closes if there is none. */
    public void back() {
        view.back();
    }

    public void refresh() {
        view.refresh();
    }

    public void refreshSlot() {
        view.refresh(slot);
    }

    public void sound(Sound sound) {
        player().playSound(player(), sound, 1f, 1f);
    }

    public void sound(Sound sound, float volume, float pitch) {
        player().playSound(player(), sound, volume, pitch);
    }
}
