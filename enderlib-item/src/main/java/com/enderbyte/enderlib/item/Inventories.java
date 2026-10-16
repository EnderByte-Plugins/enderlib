package com.enderbyte.enderlib.item;

import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Inventory helpers that get the edge cases right (stack limits, armour and off-hand slots,
 * custom max stack sizes).
 */
public final class Inventories {

    private Inventories() {
    }

    /**
     * Gives items to a player, dropping whatever does not fit at their feet.
     *
     * @return the items that had to be dropped (empty if everything fit)
     */
    public static List<ItemStack> giveOrDrop(Player player, ItemStack... items) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(clones(items));
        if (leftover.isEmpty()) {
            return List.of();
        }
        Location at = player.getLocation();
        List<ItemStack> dropped = new ArrayList<>(leftover.values());
        for (ItemStack stack : dropped) {
            Item item = player.getWorld().dropItem(at, stack);
            item.setOwner(player.getUniqueId());
            item.setPickupDelay(0);
        }
        return dropped;
    }

    /** Gives items only if all of them fit. Returns {@code false} (and changes nothing) otherwise. */
    public static boolean giveAll(Inventory inventory, ItemStack... items) {
        if (!fits(inventory, items)) {
            return false;
        }
        inventory.addItem(clones(items));
        return true;
    }

    /** Whether every item fits into the storage slots of {@code inventory}. */
    public static boolean fits(Inventory inventory, ItemStack... items) {
        ItemStack[] simulation = storage(inventory);
        for (int i = 0; i < simulation.length; i++) {
            simulation[i] = simulation[i] == null ? null : simulation[i].clone();
        }
        for (ItemStack item : items) {
            if (item == null || item.isEmpty()) {
                continue;
            }
            int remaining = item.getAmount();
            int max = item.getMaxStackSize();
            for (int i = 0; i < simulation.length && remaining > 0; i++) {
                ItemStack slot = simulation[i];
                if (slot != null && !slot.isEmpty() && slot.isSimilar(item) && slot.getAmount() < max) {
                    int move = Math.min(max - slot.getAmount(), remaining);
                    slot.setAmount(slot.getAmount() + move);
                    remaining -= move;
                }
            }
            for (int i = 0; i < simulation.length && remaining > 0; i++) {
                if (simulation[i] == null || simulation[i].isEmpty()) {
                    int move = Math.min(max, remaining);
                    simulation[i] = item.asQuantity(move);
                    remaining -= move;
                }
            }
            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    /** Number of items matching {@code filter} in the storage slots. */
    public static int count(Inventory inventory, Predicate<? super ItemStack> filter) {
        int total = 0;
        for (ItemStack item : storage(inventory)) {
            if (item != null && !item.isEmpty() && filter.test(item)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    /** Number of items similar to {@code template} (same type and data, any amount). */
    public static int count(Inventory inventory, ItemStack template) {
        return count(inventory, template::isSimilar);
    }

    /**
     * Removes up to {@code amount} items matching {@code filter}.
     *
     * @return how many were removed
     */
    public static int remove(Inventory inventory, Predicate<? super ItemStack> filter, int amount) {
        int remaining = amount;
        ItemStack[] contents = storage(inventory);
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || item.isEmpty() || !filter.test(item)) {
                continue;
            }
            int take = Math.min(item.getAmount(), remaining);
            remaining -= take;
            if (take == item.getAmount()) {
                inventory.setItem(i, null);
            } else {
                item.setAmount(item.getAmount() - take);
                inventory.setItem(i, item);
            }
        }
        return amount - remaining;
    }

    /** Removes exactly {@code amount} matching items, or nothing at all if there are fewer. */
    public static boolean removeExactly(Inventory inventory, Predicate<? super ItemStack> filter, int amount) {
        if (count(inventory, filter) < amount) {
            return false;
        }
        remove(inventory, filter, amount);
        return true;
    }

    /** Number of empty storage slots. */
    public static int freeSlots(Inventory inventory) {
        int free = 0;
        for (ItemStack item : storage(inventory)) {
            if (item == null || item.isEmpty()) {
                free++;
            }
        }
        return free;
    }

    /** First storage slot holding an item matching {@code filter}, or -1. */
    public static int firstMatching(Inventory inventory, Predicate<? super ItemStack> filter) {
        ItemStack[] contents = storage(inventory);
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null && !contents[i].isEmpty() && filter.test(contents[i])) {
                return i;
            }
        }
        return -1;
    }

    /** Whether the stack is null or air. */
    public static boolean isEmpty(@Nullable ItemStack stack) {
        return stack == null || stack.isEmpty();
    }

    /** Drops all items naturally at {@code location}. */
    public static void drop(Location location, Collection<? extends ItemStack> items) {
        for (ItemStack item : items) {
            if (!isEmpty(item)) {
                location.getWorld().dropItemNaturally(location, item);
            }
        }
    }

    private static ItemStack[] storage(Inventory inventory) {
        // Player inventories: only the 36 storage slots, never armour or off-hand
        return inventory instanceof PlayerInventory p ? p.getStorageContents() : inventory.getContents();
    }

    private static ItemStack[] clones(ItemStack... items) {
        List<ItemStack> out = new ArrayList<>(items.length);
        for (ItemStack item : items) {
            if (!isEmpty(item)) {
                out.add(item.clone());
            }
        }
        return out.toArray(ItemStack[]::new);
    }
}
