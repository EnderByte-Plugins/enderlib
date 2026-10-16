package com.enderbyte.enderlib.item;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * A typed persistent-data key.
 *
 * <pre>{@code
 * static final ItemTag<Integer> LEVEL = ItemTag.of(lib.key("level"), PersistentDataType.INTEGER);
 *
 * LEVEL.set(stack, 3);
 * int level = LEVEL.getOrDefault(stack, 1);
 * LEVEL.set(player, 3);              // works on any PersistentDataHolder: entities, chunks, tile states
 * }</pre>
 */
public final class ItemTag<T> {

    private final NamespacedKey key;
    private final PersistentDataType<?, T> type;

    private ItemTag(NamespacedKey key, PersistentDataType<?, T> type) {
        this.key = Objects.requireNonNull(key, "key");
        this.type = Objects.requireNonNull(type, "type");
    }

    public static <T> ItemTag<T> of(NamespacedKey key, PersistentDataType<?, T> type) {
        return new ItemTag<>(key, type);
    }

    public NamespacedKey key() {
        return key;
    }

    public PersistentDataType<?, T> type() {
        return type;
    }

    // ------------------------------------------------------------------ items

    public @Nullable T get(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return stack.getPersistentDataContainer().get(key, type);
    }

    public Optional<T> find(@Nullable ItemStack stack) {
        return Optional.ofNullable(get(stack));
    }

    public T getOrDefault(@Nullable ItemStack stack, T fallback) {
        T value = get(stack);
        return value != null ? value : fallback;
    }

    public boolean has(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getPersistentDataContainer().has(key, type);
    }

    /** Sets the value in place. Air cannot hold data and is ignored. */
    public void set(ItemStack stack, T value) {
        if (!stack.isEmpty()) {
            stack.editPersistentDataContainer(pdc -> pdc.set(key, type, value));
        }
    }

    public void remove(ItemStack stack) {
        if (!stack.isEmpty()) {
            stack.editPersistentDataContainer(pdc -> pdc.remove(key));
        }
    }

    // ------------------------------------------------------------------ any holder

    public @Nullable T get(PersistentDataHolder holder) {
        return holder.getPersistentDataContainer().get(key, type);
    }

    public T getOrDefault(PersistentDataHolder holder, T fallback) {
        return holder.getPersistentDataContainer().getOrDefault(key, type, fallback);
    }

    public boolean has(PersistentDataHolder holder) {
        return holder.getPersistentDataContainer().has(key, type);
    }

    public void set(PersistentDataHolder holder, T value) {
        holder.getPersistentDataContainer().set(key, type, value);
    }

    public void remove(PersistentDataHolder holder) {
        holder.getPersistentDataContainer().remove(key);
    }

    @Override
    public String toString() {
        return "ItemTag[" + key + "]";
    }
}
