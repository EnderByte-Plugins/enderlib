package com.enderbyte.enderlib.item;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Everything a {@link CustomItem} handler needs about one interaction.
 *
 * @param item   the live stack involved (mutations apply to the player's item, where the event allows it)
 * @param slot   the hand or armour slot, if known
 * @param block  the clicked or broken block, if any
 * @param target the clicked or hit entity, if any
 */
public record ItemContext(
        CustomItem customItem,
        ItemTrigger trigger,
        Player player,
        ItemStack item,
        @Nullable EquipmentSlot slot,
        @Nullable Block block,
        @Nullable Entity target,
        Event event) {

    /** Cancels the underlying event (prevents vanilla behaviour such as placing or eating). */
    public void cancel() {
        if (event instanceof Cancellable c) {
            c.setCancelled(true);
        }
    }

    public boolean isCancelled() {
        return event instanceof Cancellable c && c.isCancelled();
    }

    public Optional<Block> blockIfPresent() {
        return Optional.ofNullable(block);
    }

    public Optional<Entity> targetIfPresent() {
        return Optional.ofNullable(target);
    }

    /** Typed access to the underlying event. */
    public <E extends Event> Optional<E> event(Class<E> type) {
        return type.isInstance(event) ? Optional.of(type.cast(event)) : Optional.empty();
    }

    /**
     * Removes {@code amount} from the stack in the hand that triggered this, for consumables such as
     * spell scrolls. Creative players keep their items.
     */
    public void consume(int amount) {
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        EquipmentSlot hand = slot == EquipmentSlot.OFF_HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
        ItemStack held = player.getInventory().getItem(hand);
        if (held.isEmpty()) {
            return;
        }
        held.setAmount(held.getAmount() - amount);
        player.getInventory().setItem(hand, held.getAmount() <= 0 ? null : held);
    }
}
