package com.enderbyte.enderlib.item;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.UseCooldown;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * An item with an identity and behaviour.
 *
 * <pre>{@code
 * CustomItem wand = CustomItem.builder("fire_wand")
 *         .item(() -> ItemBuilder.of(Material.BLAZE_ROD).itemName("<gold>Fire Wand").glow().build())
 *         .on(ItemTrigger.RIGHT_CLICK, ctx -> ctx.player().launchProjectile(SmallFireball.class))
 *         .cooldown(Duration.ofSeconds(2))
 *         .cooldownMessage(Component.text("Recharging..."))
 *         .unplaceable()
 *         .build();
 *
 * CustomItems.of(lib).register(wand);
 * player.give(wand.create());
 * }</pre>
 *
 * <p>The id is stored in the item's persistent data, so renaming, enchanting or re-stacking never
 * breaks recognition, and lookup is a single map access.</p>
 */
public final class CustomItem {

    private static final Pattern ID = Pattern.compile("[a-z0-9_./:-]+");

    private final String id;
    private final Supplier<ItemStack> template;
    private final Map<ItemTrigger, List<Consumer<ItemContext>>> handlers;
    private final @Nullable Duration cooldown;
    private final @Nullable Component cooldownMessage;
    private final boolean placeable;
    private final boolean droppable;
    private final boolean craftable;
    private final boolean cancelInteractions;
    private final @Nullable HeldTask held;
    private @Nullable CustomItems owner;

    record HeldTask(long intervalTicks, BiConsumer<Player, ItemStack> action) {
    }

    private CustomItem(Builder b) {
        this.id = b.id;
        this.template = Objects.requireNonNull(b.template, "item(...) is required");
        Map<ItemTrigger, List<Consumer<ItemContext>>> map = new EnumMap<>(ItemTrigger.class);
        b.handlers.forEach((k, v) -> map.put(k, List.copyOf(v)));
        this.handlers = Collections.unmodifiableMap(map);
        this.cooldown = b.cooldown;
        this.cooldownMessage = b.cooldownMessage;
        this.placeable = b.placeable;
        this.droppable = b.droppable;
        this.craftable = b.craftable;
        this.cancelInteractions = b.cancelInteractions;
        this.held = b.held;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public String id() {
        return id;
    }

    /** A new stack of this item, tagged with its id. Requires the item to be registered. */
    public ItemStack create() {
        return create(1);
    }

    public ItemStack create(int amount) {
        if (owner == null) {
            throw new IllegalStateException("Custom item '" + id + "' is not registered with CustomItems");
        }
        ItemStack stack = template.get().clone();
        stack.setAmount(Math.max(1, amount));
        owner.idTag().set(stack, id);
        if (cooldown != null && !stack.hasData(DataComponentTypes.USE_COOLDOWN)) {
            // Lets the client draw the cooldown sweep for this item's group
            stack.setData(DataComponentTypes.USE_COOLDOWN, UseCooldown.useCooldown(cooldown.toMillis() / 1000f)
                    .cooldownGroup(cooldownGroup()));
        }
        return stack;
    }

    /** Whether {@code stack} is an instance of this item. */
    public boolean matches(@Nullable ItemStack stack) {
        return owner != null && owner.find(stack).filter(item -> item == this).isPresent();
    }

    public boolean handles(ItemTrigger trigger) {
        return handlers.containsKey(trigger);
    }

    List<Consumer<ItemContext>> handlers(ItemTrigger trigger) {
        return handlers.getOrDefault(trigger, List.of());
    }

    public @Nullable Duration cooldown() {
        return cooldown;
    }

    @Nullable Component cooldownMessage() {
        return cooldownMessage;
    }

    /** Cooldown group key, shared with the vanilla client overlay. Namespaced by the owning plugin. */
    Key cooldownGroup() {
        if (owner == null) {
            throw new IllegalStateException("Custom item '" + id + "' is not registered with CustomItems");
        }
        return owner.cooldownGroup(this);
    }

    public boolean placeable() {
        return placeable;
    }

    public boolean droppable() {
        return droppable;
    }

    public boolean craftable() {
        return craftable;
    }

    boolean cancelInteractions() {
        return cancelInteractions;
    }

    @Nullable HeldTask held() {
        return held;
    }

    void bind(CustomItems items) {
        if (owner != null && owner != items) {
            throw new IllegalStateException("Custom item '" + id + "' is already registered elsewhere");
        }
        this.owner = items;
    }

    @Override
    public String toString() {
        return "CustomItem[" + id + "]";
    }

    public static final class Builder {
        private final String id;
        private @Nullable Supplier<ItemStack> template;
        private final Map<ItemTrigger, List<Consumer<ItemContext>>> handlers = new EnumMap<>(ItemTrigger.class);
        private @Nullable Duration cooldown;
        private @Nullable Component cooldownMessage;
        private boolean placeable = true;
        private boolean droppable = true;
        private boolean craftable = true;
        private boolean cancelInteractions;
        private @Nullable HeldTask held;

        private Builder(String id) {
            if (!ID.matcher(id).matches()) {
                throw new IllegalArgumentException("Invalid custom item id '" + id + "' (use a-z 0-9 _ . / - :)");
            }
            this.id = id;
        }

        /** The look of the item; called for every {@link CustomItem#create()}, so it may vary. */
        public Builder item(Supplier<ItemStack> template) {
            this.template = Objects.requireNonNull(template, "template");
            return this;
        }

        public Builder item(ItemStack template) {
            ItemStack copy = template.clone();
            return item(() -> copy);
        }

        public Builder item(ItemBuilder template) {
            return item(template.build());
        }

        /** Adds a handler. Several handlers per trigger run in registration order. */
        public Builder on(ItemTrigger trigger, Consumer<ItemContext> handler) {
            handlers.computeIfAbsent(trigger, k -> new ArrayList<>()).add(Objects.requireNonNull(handler, "handler"));
            return this;
        }

        /** Same handler for several triggers. */
        public Builder on(Consumer<ItemContext> handler, ItemTrigger... triggers) {
            for (ItemTrigger t : triggers) {
                on(t, handler);
            }
            return this;
        }

        /**
         * Cooldown between handler runs, per player. Shown on the client as the vanilla cooldown sweep.
         * Applies to click, interact and consume triggers.
         */
        public Builder cooldown(Duration cooldown) {
            this.cooldown = cooldown;
            return this;
        }

        public Builder cooldownMessage(Component message) {
            this.cooldownMessage = message;
            return this;
        }

        /** Prevents placing the item as a block (heads, blocks used as tokens, ...). */
        public Builder unplaceable() {
            this.placeable = false;
            return this;
        }

        /** Prevents dropping the item with Q. */
        public Builder undroppable() {
            this.droppable = false;
            return this;
        }

        /** Prevents using the item as a crafting ingredient. */
        public Builder uncraftable() {
            this.craftable = false;
            return this;
        }

        /** Cancels the vanilla action for every click this item handles (no eating, shooting, tilling...). */
        public Builder cancelVanilla() {
            this.cancelInteractions = true;
            return this;
        }

        /** Runs {@code action} every {@code intervalTicks} for each player holding the item in the main hand. */
        public Builder whileHeld(long intervalTicks, BiConsumer<Player, ItemStack> action) {
            this.held = new HeldTask(Math.max(1, intervalTicks), action);
            return this;
        }

        public CustomItem build() {
            return new CustomItem(this);
        }
    }
}
