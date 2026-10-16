package com.enderbyte.enderlib.itemsadder;

import com.enderbyte.enderlib.core.EnderLib;
import com.enderbyte.enderlib.core.Terminable;
import com.enderbyte.enderlib.item.CustomItems;
import com.enderbyte.enderlib.item.ItemBuilder;
import dev.lone.itemsadder.api.CustomStack;
import dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.EventPriority;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * ItemsAdder bridge for one plugin.
 *
 * <pre>{@code
 * ItemsAdder ia = ItemsAdder.of(lib);
 *
 * ItemStack ruby = ia.item("myitems:ruby").orElseThrow();
 * ItemBuilder sword = ia.builder("myitems:ruby_sword").orElseThrow().name("<red>Ruby Sword");
 *
 * // Behaviour for an ItemsAdder item, even when it was given by /iagive:
 * ia.bindCustomItems(CustomItems.of(lib));
 * CustomItems.of(lib).register(CustomItem.builder("myitems:ruby_sword")
 *         .item(ia.supplier("myitems:ruby_sword"))
 *         .on(ItemTrigger.HIT_ENTITY, ctx -> ...)
 *         .build());
 * }</pre>
 *
 * Item templates and glyphs are cached and flushed automatically when ItemsAdder reloads
 * ({@code /iazip}, {@code /iareload}). Run code after the first load with {@link #onLoad}.
 */
public final class ItemsAdder implements Terminable {

    private static volatile @Nullable Boolean installed;

    private final EnderLib lib;
    private final Map<String, ItemStack> templates = new ConcurrentHashMap<>();
    private final Map<String, String> glyphAliases = new ConcurrentHashMap<>();
    private final List<Runnable> loadListeners = new CopyOnWriteArrayList<>();
    private volatile boolean loaded;

    private ItemsAdder(EnderLib lib) {
        this.lib = lib;
        if (isInstalled()) {
            lib.events().listen(ItemsAdderLoadDataEvent.class).priority(EventPriority.MONITOR).handler(e -> {
                templates.clear();
                Glyph.invalidateCache();
                loaded = true;
                for (Runnable r : loadListeners) {
                    try {
                        r.run();
                    } catch (Throwable t) {
                        lib.logger().error("ItemsAdder load listener failed", t);
                    }
                }
            });
        }
    }

    public static ItemsAdder of(EnderLib lib) {
        return lib.service(ItemsAdder.class, ItemsAdder::new);
    }

    /** Whether the ItemsAdder plugin is installed and enabled. */
    public static boolean isInstalled() {
        Boolean cached = installed;
        if (cached == null) {
            var plugin = Bukkit.getPluginManager().getPlugin("ItemsAdder");
            cached = plugin != null && plugin.isEnabled();
            installed = cached;
        }
        return cached;
    }

    /** Whether ItemsAdder finished loading its data since this plugin started. */
    public boolean isLoaded() {
        return loaded;
    }

    /** Runs {@code listener} after every ItemsAdder data load (including the first). */
    public void onLoad(Runnable listener) {
        loadListeners.add(listener);
        if (loaded) {
            listener.run();
        }
    }

    // ------------------------------------------------------------------ items

    /** A fresh copy of ItemsAdder item {@code namespacedId}. */
    public Optional<ItemStack> item(String namespacedId) {
        return item(namespacedId, 1);
    }

    public Optional<ItemStack> item(String namespacedId, int amount) {
        if (!isInstalled()) {
            return Optional.empty();
        }
        ItemStack template = templates.get(namespacedId);
        if (template == null) {
            CustomStack stack = CustomStack.getInstance(namespacedId);
            if (stack == null) {
                return Optional.empty();
            }
            template = stack.getItemStack();
            templates.put(namespacedId, template);
        }
        ItemStack copy = template.clone();
        copy.setAmount(Math.max(1, amount));
        return Optional.of(copy);
    }

    /** An {@link ItemBuilder} starting from the ItemsAdder item. */
    public Optional<ItemBuilder> builder(String namespacedId) {
        return item(namespacedId).map(ItemBuilder::from);
    }

    /**
     * A supplier for {@link com.enderbyte.enderlib.item.CustomItem.Builder#item(Supplier)} that
     * resolves the ItemsAdder item lazily, falling back to a named barrier if it does not exist.
     */
    public Supplier<ItemStack> supplier(String namespacedId) {
        return () -> item(namespacedId).orElseGet(() ->
                ItemBuilder.of(Material.BARRIER).name("<red>Missing ItemsAdder item " + namespacedId).build());
    }

    /** The ItemsAdder id of {@code stack}, if it is an ItemsAdder item. */
    public Optional<String> id(@Nullable ItemStack stack) {
        if (!isInstalled() || stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        CustomStack custom = CustomStack.byItemStack(stack);
        return custom == null ? Optional.empty() : Optional.of(custom.getNamespacedID());
    }

    public boolean is(@Nullable ItemStack stack, String namespacedId) {
        return id(stack).filter(namespacedId::equals).isPresent();
    }

    /**
     * Lets {@code items} recognise ItemsAdder items by their ItemsAdder id: register a
     * {@link com.enderbyte.enderlib.item.CustomItem} whose id is the ItemsAdder id, and it reacts to
     * every copy of that item, however it was obtained.
     */
    public void bindCustomItems(CustomItems items) {
        items.addResolver(stack -> id(stack).orElse(null));
    }

    // ------------------------------------------------------------------ glyphs

    /** Registers a short alias for a font image, for configs: {@code glyph("coin")}. */
    public ItemsAdder alias(String alias, String fontImageId) {
        glyphAliases.put(alias, fontImageId);
        return this;
    }

    /** A glyph by alias or full id. */
    public Optional<Glyph> glyph(String aliasOrId) {
        return Glyph.of(glyphAliases.getOrDefault(aliasOrId, aliasOrId));
    }

    @Override
    public void close() {
        templates.clear();
        loadListeners.clear();
    }
}
