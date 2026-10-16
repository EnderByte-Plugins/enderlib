package com.enderbyte.enderlib.item;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import com.enderbyte.enderlib.core.EnderLib;
import com.enderbyte.enderlib.core.Terminable;
import com.enderbyte.enderlib.scheduler.Scheduler;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Registry and event dispatcher for {@link CustomItem}s of one plugin.
 *
 * <pre>{@code
 * CustomItems items = CustomItems.of(lib);
 * items.register(wand);
 * items.find(player.getInventory().getItemInMainHand()).ifPresent(...);
 * }</pre>
 *
 * Listeners are registered once, on first use, and each dispatch is a single persistent-data read plus
 * a map lookup, so the cost is the same for one custom item or five hundred.
 */
public final class CustomItems implements Terminable {

    private final EnderLib lib;
    private final ItemTag<String> idTag;
    private final Map<String, CustomItem> items = new ConcurrentHashMap<>();
    private final List<CustomItem> heldItems = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<Function<ItemStack, @Nullable String>> resolvers = new java.util.concurrent.CopyOnWriteArrayList<>();
    private @Nullable Terminable heldTicker;

    private CustomItems(EnderLib lib) {
        this.lib = lib;
        this.idTag = ItemTag.of(lib.key("custom_item"), PersistentDataType.STRING);
        registerListeners();
    }

    public static CustomItems of(EnderLib lib) {
        return lib.service(CustomItems.class, CustomItems::new);
    }

    /** The persistent tag holding a custom item's id. */
    public ItemTag<String> idTag() {
        return idTag;
    }

    public CustomItem register(CustomItem item) {
        item.bind(this);
        CustomItem previous = items.putIfAbsent(item.id(), item);
        if (previous != null && previous != item) {
            throw new IllegalArgumentException("Duplicate custom item id '" + item.id() + "'");
        }
        if (item.held() != null) {
            heldItems.add(item);
            startHeldTicker();
        }
        return item;
    }

    public void registerAll(CustomItem... all) {
        for (CustomItem item : all) {
            register(item);
        }
    }

    public Optional<CustomItem> get(String id) {
        return Optional.ofNullable(items.get(id));
    }

    /** The custom item {@code stack} is an instance of. */
    public Optional<CustomItem> find(@Nullable ItemStack stack) {
        String id = idTag.get(stack);
        if (id == null && stack != null && !stack.isEmpty() && !resolvers.isEmpty()) {
            for (Function<ItemStack, @Nullable String> resolver : resolvers) {
                id = resolver.apply(stack);
                if (id != null) {
                    break;
                }
            }
        }
        return id == null ? Optional.empty() : Optional.ofNullable(items.get(id));
    }

    /**
     * Adds a fallback that identifies items without EnderLib's id tag, for example items created by
     * another plugin. It returns a custom item id, or {@code null}. Used by the ItemsAdder addon.
     */
    public void addResolver(Function<ItemStack, @Nullable String> resolver) {
        resolvers.add(resolver);
    }

    public boolean isCustom(@Nullable ItemStack stack) {
        return find(stack).isPresent();
    }

    public Collection<CustomItem> all() {
        return Collections.unmodifiableCollection(items.values());
    }

    /** Gives {@code amount} of the item, dropping overflow at the player's feet. */
    public void give(Player player, String id, int amount) {
        CustomItem item = get(id).orElseThrow(() -> new IllegalArgumentException("Unknown custom item '" + id + "'"));
        Inventories.giveOrDrop(player, item.create(amount));
    }

    /** Remaining cooldown of {@code item} for {@code player} in ticks (0 if none). */
    public int cooldownTicks(Player player, CustomItem item) {
        return player.getCooldown(item.cooldownGroup());
    }

    Key cooldownGroup(CustomItem item) {
        return lib.key("item/" + item.id().replace('.', '_').replace(':', '/'));
    }

    @Override
    public void close() {
        if (heldTicker != null) {
            heldTicker.close();
        }
        items.clear();
        heldItems.clear();
    }

    // ================================================================== dispatch

    private void dispatch(CustomItem item, ItemTrigger trigger, Player player, ItemStack stack,
                          @Nullable EquipmentSlot slot, @Nullable Block block, @Nullable Entity target, Event event) {
        List<Consumer<ItemContext>> handlers = item.handlers(trigger);
        if (handlers.isEmpty()) {
            return;
        }
        boolean cooled = item.cooldown() != null && isCooledTrigger(trigger);
        if (cooled && player.getCooldown(item.cooldownGroup()) > 0) {
            if (item.cancelInteractions() && event instanceof org.bukkit.event.Cancellable c) {
                c.setCancelled(true);
            }
            if (item.cooldownMessage() != null) {
                player.sendActionBar(item.cooldownMessage());
            }
            return;
        }
        ItemContext ctx = new ItemContext(item, trigger, player, stack, slot, block, target, event);
        if (item.cancelInteractions()) {
            ctx.cancel();
        }
        for (Consumer<ItemContext> handler : handlers) {
            try {
                handler.accept(ctx);
            } catch (Throwable t) {
                lib.logger().error("Custom item '{}' {} handler failed", item.id(), trigger, t);
            }
        }
        if (cooled) {
            player.setCooldown(item.cooldownGroup(), (int) Math.max(1, item.cooldown().toMillis() / 50));
        }
    }

    private static boolean isCooledTrigger(ItemTrigger trigger) {
        return switch (trigger) {
            case RIGHT_CLICK, SHIFT_RIGHT_CLICK, LEFT_CLICK, SHIFT_LEFT_CLICK, INTERACT_ENTITY, CONSUME, HIT_ENTITY -> true;
            default -> false;
        };
    }

    private void dispatch(@Nullable ItemStack stack, ItemTrigger trigger, Player player, @Nullable EquipmentSlot slot,
                          @Nullable Block block, @Nullable Entity target, Event event) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        CustomItem item = find(stack).orElse(null);
        if (item != null) {
            dispatch(item, trigger, player, stack, slot, block, target, event);
        }
    }

    private void registerListeners() {
        var events = lib.events();

        events.listen(PlayerInteractEvent.class).priority(EventPriority.HIGH).handler(e -> {
            // Right-click air arrives "cancelled" by design; the item-use result is the real signal
            if (e.getAction() == Action.PHYSICAL || e.getItem() == null || items.isEmpty()
                    || e.useItemInHand() == Event.Result.DENY) {
                return;
            }
            CustomItem item = find(e.getItem()).orElse(null);
            if (item == null) {
                return;
            }
            boolean right = e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK;
            boolean sneaking = e.getPlayer().isSneaking();
            ItemTrigger trigger = right
                    ? (sneaking && item.handles(ItemTrigger.SHIFT_RIGHT_CLICK) ? ItemTrigger.SHIFT_RIGHT_CLICK : ItemTrigger.RIGHT_CLICK)
                    : (sneaking && item.handles(ItemTrigger.SHIFT_LEFT_CLICK) ? ItemTrigger.SHIFT_LEFT_CLICK : ItemTrigger.LEFT_CLICK);
            if (right && !item.placeable() && e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getItem().getType().isBlock()) {
                e.setUseItemInHand(Event.Result.DENY);
            }
            dispatch(item, trigger, e.getPlayer(), e.getItem(), e.getHand(), e.getClickedBlock(), null, e);
        });

        events.listen(PlayerInteractEntityEvent.class).priority(EventPriority.HIGH).handler(e -> {
            ItemStack stack = e.getPlayer().getInventory().getItem(e.getHand());
            dispatch(stack, ItemTrigger.INTERACT_ENTITY, e.getPlayer(), e.getHand(), null, e.getRightClicked(), e);
        });

        events.listen(EntityDamageByEntityEvent.class).priority(EventPriority.HIGH).ignoreCancelled().handler(e -> {
            if (e.getDamager() instanceof Player p) {
                dispatch(p.getInventory().getItemInMainHand(), ItemTrigger.HIT_ENTITY, p, EquipmentSlot.HAND, null, e.getEntity(), e);
            }
        });

        events.listen(BlockBreakEvent.class).priority(EventPriority.HIGH).ignoreCancelled().handler(e ->
                dispatch(e.getPlayer().getInventory().getItemInMainHand(), ItemTrigger.BREAK_BLOCK, e.getPlayer(),
                        EquipmentSlot.HAND, e.getBlock(), null, e));

        events.listen(BlockPlaceEvent.class).priority(EventPriority.LOW).ignoreCancelled().handler(e -> {
            CustomItem item = find(e.getItemInHand()).orElse(null);
            if (item != null && !item.placeable()) {
                e.setCancelled(true);
            }
        });

        events.listen(PlayerItemConsumeEvent.class).priority(EventPriority.HIGH).ignoreCancelled().handler(e ->
                dispatch(e.getItem(), ItemTrigger.CONSUME, e.getPlayer(), e.getHand(), null, null, e));

        events.listen(PlayerDropItemEvent.class).priority(EventPriority.HIGH).ignoreCancelled().handler(e -> {
            ItemStack stack = e.getItemDrop().getItemStack();
            CustomItem item = find(stack).orElse(null);
            if (item == null) {
                return;
            }
            if (!item.droppable()) {
                e.setCancelled(true);
                return;
            }
            dispatch(item, ItemTrigger.DROP, e.getPlayer(), stack, null, null, null, e);
        });

        events.listen(PlayerSwapHandItemsEvent.class).priority(EventPriority.HIGH).ignoreCancelled().handler(e -> {
            dispatch(e.getMainHandItem(), ItemTrigger.SWAP_HANDS, e.getPlayer(), EquipmentSlot.HAND, null, null, e);
            dispatch(e.getOffHandItem(), ItemTrigger.SWAP_HANDS, e.getPlayer(), EquipmentSlot.OFF_HAND, null, null, e);
        });

        events.listen(PlayerItemHeldEvent.class).priority(EventPriority.MONITOR).ignoreCancelled().handler(e -> {
            Player p = e.getPlayer();
            dispatch(p.getInventory().getItem(e.getPreviousSlot()), ItemTrigger.DESELECT, p, EquipmentSlot.HAND, null, null, e);
            dispatch(p.getInventory().getItem(e.getNewSlot()), ItemTrigger.SELECT, p, EquipmentSlot.HAND, null, null, e);
        });

        events.listen(PlayerArmorChangeEvent.class).priority(EventPriority.MONITOR).handler(e -> {
            dispatch(e.getOldItem(), ItemTrigger.UNEQUIP, e.getPlayer(), e.getSlot(), null, null, e);
            dispatch(e.getNewItem(), ItemTrigger.EQUIP, e.getPlayer(), e.getSlot(), null, null, e);
        });

        events.listen(PrepareItemCraftEvent.class).priority(EventPriority.HIGH).handler(e -> {
            for (ItemStack ingredient : e.getInventory().getMatrix()) {
                CustomItem item = ingredient == null ? null : find(ingredient).orElse(null);
                if (item != null && !item.craftable()) {
                    e.getInventory().setResult(null);
                    return;
                }
            }
        });
    }

    // ================================================================== held ticking

    private synchronized void startHeldTicker() {
        if (heldTicker != null) {
            return;
        }
        heldTicker = lib.ticker().subscribe(tick -> {
            List<CustomItem> due = new ArrayList<>(2);
            for (CustomItem item : heldItems) {
                if (tick % item.held().intervalTicks() == 0) {
                    due.add(item);
                }
            }
            if (due.isEmpty()) {
                return;
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (Scheduler.isFolia()) {
                    lib.scheduler().runAt(player, () -> runHeld(player, due));
                } else {
                    runHeld(player, due);
                }
            }
        });
    }

    private void runHeld(Player player, List<CustomItem> due) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        String id = find(hand).map(CustomItem::id).orElse(null);
        if (id == null) {
            return;
        }
        for (CustomItem item : due) {
            if (item.id().equals(id)) {
                try {
                    item.held().action().accept(player, hand);
                } catch (Throwable t) {
                    lib.logger().error("Custom item '{}' whileHeld task failed", id, t);
                }
            }
        }
    }
}
