package com.enderbyte.enderlib.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marks an inventory as an EnderLib menu and links it to its {@link MenuView}.
 *
 * <p>Because every plugin shades and relocates EnderLib, this class is distinct per plugin: one
 * plugin's listeners never claim another plugin's menus.</p>
 */
public final class MenuHolder implements InventoryHolder {

    private final MenuView view;

    MenuHolder(MenuView view) {
        this.view = view;
    }

    public MenuView view() {
        return view;
    }

    @Override
    public Inventory getInventory() {
        return view.inventory();
    }
}
