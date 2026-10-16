package com.enderbyte.enderlib.item;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.enderbyte.enderlib.text.Text;
import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.DyedItemColor;
import io.papermc.paper.datacomponent.item.FoodProperties;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import io.papermc.paper.datacomponent.item.ItemEnchantments;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.PotionContents;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import io.papermc.paper.datacomponent.item.UseCooldown;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Fluent builder over Paper's item data components.
 *
 * <pre>{@code
 * ItemStack sword = ItemBuilder.of(Material.NETHERITE_SWORD)
 *         .name("<gradient:gold:yellow>Kingslayer")
 *         .lore("<gray>Forged in the end.", "", "<yellow>Right-click: <white>dash")
 *         .enchant(Enchantment.SHARPNESS, 7)
 *         .hideEnchants()
 *         .glint(true)
 *         .unbreakable()
 *         .model(NamespacedKey.fromString("myplugin:kingslayer"))
 *         .tag(LEVEL, 3)
 *         .build();
 * }</pre>
 *
 * <p>Names and lore given as strings are MiniMessage with italics switched off. Every call returns
 * the same builder; {@link #build()} returns a fresh copy each time, so a builder can serve as a
 * template.</p>
 */
public final class ItemBuilder {

    private final ItemStack stack;
    private final Map<Enchantment, Integer> enchants = new LinkedHashMap<>();
    private final Set<DataComponentType> hidden = new LinkedHashSet<>();
    private final List<AttributeEntry> attributes = new ArrayList<>();
    private boolean hideTooltip;
    private boolean touchedTooltip;

    private record AttributeEntry(Attribute attribute, AttributeModifier modifier) {
    }

    private ItemBuilder(ItemStack stack) {
        this.stack = stack;
        ItemEnchantments existing = stack.getData(DataComponentTypes.ENCHANTMENTS);
        if (existing != null) {
            enchants.putAll(existing.enchantments());
        }
    }

    public static ItemBuilder of(Material material) {
        return of(material, 1);
    }

    public static ItemBuilder of(Material material, int amount) {
        if (!material.isItem()) {
            throw new IllegalArgumentException(material + " is not an item");
        }
        return new ItemBuilder(ItemStack.of(material, amount));
    }

    /** Starts from a copy of {@code stack}; the original is not modified. */
    public static ItemBuilder from(ItemStack stack) {
        return new ItemBuilder(stack.clone());
    }

    /** Starts from a player head showing {@code owner}'s skin. */
    public static ItemBuilder head(PlayerProfile owner) {
        return of(Material.PLAYER_HEAD).profile(owner);
    }

    // ------------------------------------------------------------------ basics

    public ItemBuilder type(Material material) {
        stack.setType(material);
        return this;
    }

    public ItemBuilder amount(int amount) {
        stack.setAmount(Math.max(1, amount));
        return this;
    }

    /** Custom name (shown instead of the item's own name). MiniMessage, no italics. */
    public ItemBuilder name(String miniMessage, TagResolver... resolvers) {
        return name(Text.item(miniMessage, resolvers));
    }

    public ItemBuilder name(Component name) {
        stack.setData(DataComponentTypes.CUSTOM_NAME, Text.noItalic(name));
        return this;
    }

    /**
     * Base item name: unlike {@link #name}, it cannot be renamed away in an anvil and is not italic by
     * default. Prefer this for custom items.
     */
    public ItemBuilder itemName(Component name) {
        stack.setData(DataComponentTypes.ITEM_NAME, name);
        return this;
    }

    public ItemBuilder itemName(String miniMessage, TagResolver... resolvers) {
        return itemName(Text.mm(miniMessage, resolvers));
    }

    /** Replaces the lore. Each string is one MiniMessage line; {@code "\n"} also splits lines. */
    public ItemBuilder lore(String... lines) {
        return lore(List.of(lines));
    }

    public ItemBuilder lore(Collection<String> lines, TagResolver... resolvers) {
        List<Component> out = new ArrayList<>();
        for (String line : lines) {
            for (String part : line.split("\n", -1)) {
                out.add(Text.item(part, resolvers));
            }
        }
        return loreComponents(out);
    }

    public ItemBuilder loreComponents(List<? extends Component> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        lines.forEach(c -> out.add(Text.noItalic(c)));
        stack.setData(DataComponentTypes.LORE, ItemLore.lore(out));
        return this;
    }

    /** Appends lines to the existing lore. */
    public ItemBuilder addLore(String... lines) {
        List<Component> current = new ArrayList<>(currentLore());
        for (String line : lines) {
            current.add(Text.item(line));
        }
        return loreComponents(current);
    }

    public ItemBuilder addLore(Component... lines) {
        List<Component> current = new ArrayList<>(currentLore());
        current.addAll(List.of(lines));
        return loreComponents(current);
    }

    public ItemBuilder clearLore() {
        stack.unsetData(DataComponentTypes.LORE);
        return this;
    }

    // ------------------------------------------------------------------ looks

    public ItemBuilder enchant(Enchantment enchantment, int level) {
        enchants.put(enchantment, level);
        return this;
    }

    public ItemBuilder enchants(Map<Enchantment, Integer> map) {
        enchants.putAll(map);
        return this;
    }

    public ItemBuilder removeEnchant(Enchantment enchantment) {
        enchants.remove(enchantment);
        return this;
    }

    /** Forces the enchantment glint on or off regardless of enchantments. */
    public ItemBuilder glint(boolean glint) {
        stack.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, glint);
        return this;
    }

    /** Shorthand for {@code glint(true)}. */
    public ItemBuilder glow() {
        return glint(true);
    }

    /** Item model (resource pack {@code items/} definition), e.g. {@code myplugin:wand}. */
    public ItemBuilder model(@Nullable Key model) {
        if (model == null) {
            stack.resetData(DataComponentTypes.ITEM_MODEL);
        } else {
            stack.setData(DataComponentTypes.ITEM_MODEL, model);
        }
        return this;
    }

    /** Legacy-style numeric custom model data (first float). */
    public ItemBuilder customModelData(float value) {
        stack.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addFloat(value));
        return this;
    }

    /** Full custom model data (floats, flags, strings, colors) for item model selectors. */
    public ItemBuilder customModelData(Consumer<CustomModelData.Builder> builder) {
        CustomModelData.Builder b = CustomModelData.customModelData();
        builder.accept(b);
        stack.setData(DataComponentTypes.CUSTOM_MODEL_DATA, b);
        return this;
    }

    public ItemBuilder tooltipStyle(Key style) {
        stack.setData(DataComponentTypes.TOOLTIP_STYLE, style);
        return this;
    }

    public ItemBuilder rarity(ItemRarity rarity) {
        stack.setData(DataComponentTypes.RARITY, rarity);
        return this;
    }

    public ItemBuilder color(Color color) {
        stack.setData(DataComponentTypes.DYED_COLOR, DyedItemColor.dyedItemColor(color));
        return this;
    }

    public ItemBuilder color(int rgb) {
        return color(Color.fromRGB(rgb & 0xFFFFFF));
    }

    // ------------------------------------------------------------------ tooltip

    /** Hides the given components from the tooltip (they still apply). */
    public ItemBuilder hide(DataComponentType... types) {
        touchedTooltip = true;
        hidden.addAll(List.of(types));
        return this;
    }

    public ItemBuilder hideEnchants() {
        return hide(DataComponentTypes.ENCHANTMENTS, DataComponentTypes.STORED_ENCHANTMENTS);
    }

    public ItemBuilder hideAttributes() {
        return hide(DataComponentTypes.ATTRIBUTE_MODIFIERS);
    }

    /** Hides every "technical" line: enchants, attributes, unbreakable, dye, trims, potion effects. */
    public ItemBuilder hideAll() {
        return hide(DataComponentTypes.ENCHANTMENTS, DataComponentTypes.STORED_ENCHANTMENTS,
                DataComponentTypes.ATTRIBUTE_MODIFIERS, DataComponentTypes.UNBREAKABLE,
                DataComponentTypes.DYED_COLOR, DataComponentTypes.TRIM, DataComponentTypes.POTION_CONTENTS,
                DataComponentTypes.JUKEBOX_PLAYABLE, DataComponentTypes.FIREWORKS, DataComponentTypes.BANNER_PATTERNS,
                DataComponentTypes.CAN_BREAK, DataComponentTypes.CAN_PLACE_ON);
    }

    /** Hides the whole tooltip, name included. Ideal for GUI filler panes. */
    public ItemBuilder hideTooltip() {
        touchedTooltip = true;
        hideTooltip = true;
        return this;
    }

    // ------------------------------------------------------------------ behaviour

    public ItemBuilder unbreakable() {
        stack.setData(DataComponentTypes.UNBREAKABLE);
        return this;
    }

    public ItemBuilder maxStackSize(int size) {
        stack.setData(DataComponentTypes.MAX_STACK_SIZE, Math.max(1, Math.min(99, size)));
        return this;
    }

    public ItemBuilder maxDamage(int durability) {
        stack.setData(DataComponentTypes.MAX_DAMAGE, durability);
        return this;
    }

    public ItemBuilder damage(int damage) {
        stack.setData(DataComponentTypes.DAMAGE, damage);
        return this;
    }

    public ItemBuilder attribute(Attribute attribute, AttributeModifier modifier) {
        attributes.add(new AttributeEntry(attribute, modifier));
        return this;
    }

    /** Makes the item edible. */
    public ItemBuilder food(int nutrition, float saturation, boolean canAlwaysEat) {
        stack.setData(DataComponentTypes.FOOD, FoodProperties.food()
                .nutrition(nutrition).saturation(saturation).canAlwaysEat(canAlwaysEat));
        if (!stack.hasData(DataComponentTypes.CONSUMABLE)) {
            stack.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable());
        }
        return this;
    }

    /** Makes the item consumable (eat/drink animation) with full control over the component. */
    public ItemBuilder consumable(Consumer<Consumable.Builder> builder) {
        Consumable.Builder b = Consumable.consumable();
        builder.accept(b);
        stack.setData(DataComponentTypes.CONSUMABLE, b);
        return this;
    }

    /**
     * Vanilla use cooldown: the client shows the sweep overlay. Items sharing {@code group} share the
     * cooldown.
     */
    public ItemBuilder useCooldown(float seconds, @Nullable Key group) {
        UseCooldown.Builder b = UseCooldown.useCooldown(seconds);
        if (group != null) {
            b.cooldownGroup(group);
        }
        stack.setData(DataComponentTypes.USE_COOLDOWN, b);
        return this;
    }

    public ItemBuilder potion(PotionType type) {
        stack.setData(DataComponentTypes.POTION_CONTENTS, PotionContents.potionContents().potion(type));
        return this;
    }

    public ItemBuilder potion(@Nullable Color color, PotionEffect... effects) {
        PotionContents.Builder b = PotionContents.potionContents().addCustomEffects(List.of(effects));
        if (color != null) {
            b.customColor(color);
        }
        stack.setData(DataComponentTypes.POTION_CONTENTS, b);
        return this;
    }

    public ItemBuilder firework(int flightDuration, FireworkEffect... effects) {
        stack.setData(DataComponentTypes.FIREWORKS, io.papermc.paper.datacomponent.item.Fireworks.fireworks()
                .flightDuration(flightDuration).addEffects(List.of(effects)));
        return this;
    }

    // ------------------------------------------------------------------ heads

    public ItemBuilder profile(PlayerProfile profile) {
        stack.setData(DataComponentTypes.PROFILE, ResolvableProfile.resolvableProfile(profile));
        return this;
    }

    /** Head of an online player. */
    public ItemBuilder skull(Player owner) {
        return profile(owner.getPlayerProfile());
    }

    /** Head by UUID (the client resolves the skin). */
    public ItemBuilder skull(UUID owner) {
        stack.setData(DataComponentTypes.PROFILE, ResolvableProfile.resolvableProfile().uuid(owner));
        return this;
    }

    /**
     * Head with a fixed texture, given as the base64 {@code textures} property value (what head
     * databases call "value").
     */
    public ItemBuilder skullTexture(String base64) {
        stack.setData(DataComponentTypes.PROFILE, ResolvableProfile.resolvableProfile()
                .uuid(UUID.nameUUIDFromBytes(base64.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .addProperty(new ProfileProperty("textures", base64)));
        return this;
    }

    /** Head with a texture from {@code textures.minecraft.net/texture/<hash>}. */
    public ItemBuilder skullTextureHash(String hash) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + hash + "\"}}}";
        return skullTexture(java.util.Base64.getEncoder().encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    // ------------------------------------------------------------------ data

    /** Stores a persistent value. */
    public <T> ItemBuilder tag(ItemTag<T> tag, T value) {
        tag.set(stack, value);
        return this;
    }

    public <P, C> ItemBuilder data(NamespacedKey key, PersistentDataType<P, C> type, C value) {
        stack.editPersistentDataContainer(pdc -> pdc.set(key, type, value));
        return this;
    }

    /** Sets any data component directly, for components this builder has no shortcut for. */
    public <T> ItemBuilder component(DataComponentType.Valued<T> type, T value) {
        stack.setData(type, value);
        return this;
    }

    public ItemBuilder component(DataComponentType.NonValued type) {
        stack.setData(type);
        return this;
    }

    public ItemBuilder removeComponent(DataComponentType type) {
        stack.unsetData(type);
        return this;
    }

    /** Escape hatch to the legacy {@link ItemMeta} API. */
    public ItemBuilder meta(Consumer<? super ItemMeta> editor) {
        flush();
        stack.editMeta(editor);
        return this;
    }

    public <M extends ItemMeta> ItemBuilder meta(Class<M> type, Consumer<? super M> editor) {
        flush();
        stack.editMeta(type, editor);
        return this;
    }

    /** Arbitrary edit of the underlying stack. */
    public ItemBuilder apply(Consumer<ItemStack> editor) {
        flush();
        editor.accept(stack);
        return this;
    }

    // ------------------------------------------------------------------ build

    /** Returns a new stack; the builder stays usable. */
    public ItemStack build() {
        flush();
        return stack.clone();
    }

    /** A builder for a copy of the current state. */
    public ItemBuilder copy() {
        return from(build());
    }

    private void flush() {
        if (enchants.isEmpty()) {
            stack.resetData(DataComponentTypes.ENCHANTMENTS);
        } else {
            stack.setData(DataComponentTypes.ENCHANTMENTS, ItemEnchantments.itemEnchantments(enchants));
        }
        if (!attributes.isEmpty()) {
            ItemAttributeModifiers.Builder b = ItemAttributeModifiers.itemAttributes();
            for (AttributeEntry e : attributes) {
                b.addModifier(e.attribute(), e.modifier());
            }
            stack.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, b);
        }
        if (touchedTooltip) {
            stack.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay()
                    .hideTooltip(hideTooltip).hiddenComponents(hidden));
        }
    }

    private List<Component> currentLore() {
        ItemLore lore = stack.getData(DataComponentTypes.LORE);
        return lore == null ? List.of() : lore.lines();
    }

    /** The material this builder currently produces. */
    public Material material() {
        return stack.getType();
    }

    @Override
    public String toString() {
        return "ItemBuilder[" + Objects.toString(stack) + "]";
    }
}
