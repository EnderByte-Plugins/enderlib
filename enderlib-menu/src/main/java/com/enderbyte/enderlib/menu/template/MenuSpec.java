package com.enderbyte.enderlib.menu.template;

import com.enderbyte.enderlib.item.ItemBuilder;
import com.enderbyte.enderlib.menu.Button;
import com.enderbyte.enderlib.menu.ItemButton;
import com.enderbyte.enderlib.menu.Menu;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A menu described in a config file. Bind it with the config module and turn it into a {@link Menu}:
 *
 * <pre>{@code
 * MenuSpec spec = Configs.of(lib).load("menus/main.yml", MenuSpec.class).get();
 * Menu main = spec.toMenu("main", actions);
 * }</pre>
 *
 * <pre>{@code
 * # menus/main.yml
 * title: "<dark_gray>Main menu"
 * rows: 3
 * pattern:
 *   - "#########"
 *   - "#  S W  #"
 *   - "####X####"
 * items:
 *   "#": { material: BLACK_STAINED_GLASS_PANE, hide-tooltip: true }
 *   S:
 *     material: DIAMOND_SWORD
 *     name: "<aqua>Kits"
 *     lore: ["<gray>Pick a kit"]
 *     actions: ["open:kits", "sound:ui.button.click"]
 *   W:
 *     material: COMPASS
 *     name: "<green>Warps"
 *     actions: ["command:warps"]
 *   X:
 *     material: BARRIER
 *     name: "<red>Close"
 *     actions: ["close"]
 * }</pre>
 *
 * Action strings are resolved by {@link MenuActions}.
 */
public final class MenuSpec {

    public String title = "<dark_gray>Menu";
    public int rows = 3;
    public List<String> pattern = new ArrayList<>();
    public Map<String, ItemSpec> items = new LinkedHashMap<>();

    /** One ingredient of the pattern. */
    public static final class ItemSpec {
        public String material = "STONE";
        public String name = "";
        public List<String> lore = new ArrayList<>();
        public int amount = 1;
        public boolean glow;
        public String model = "";
        public boolean hideTooltip;
        public String permission = "";
        public List<String> actions = new ArrayList<>();

        public ItemBuilder toItem() {
            Material type = Material.matchMaterial(material.trim());
            if (type == null || !type.isItem()) {
                throw new IllegalArgumentException("Unknown item material '" + material + "'");
            }
            ItemBuilder b = ItemBuilder.of(type, amount);
            if (!name.isEmpty()) {
                b.name(name);
            }
            if (!lore.isEmpty()) {
                b.lore(lore);
            }
            if (glow) {
                b.glow();
            }
            if (!model.isEmpty()) {
                b.model(Key.key(model.toLowerCase(Locale.ROOT)));
            }
            if (hideTooltip) {
                b.hideTooltip();
            }
            return b;
        }
    }

    /** Builds the menu. Unknown materials or actions fail fast with the offending key in the message. */
    public Menu toMenu(String id, MenuActions actions) {
        Menu.Builder builder = Menu.chest(rows).id(id).title(title);
        if (!pattern.isEmpty()) {
            builder.pattern(pattern.toArray(String[]::new));
        }
        for (Map.Entry<String, ItemSpec> e : items.entrySet()) {
            if (e.getKey().length() != 1) {
                throw new IllegalArgumentException("Menu '" + id + "': item key '" + e.getKey() + "' must be one character");
            }
            ItemSpec spec = e.getValue();
            ItemButton button;
            try {
                button = Button.of(spec.toItem());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Menu '" + id + "', item '" + e.getKey() + "': " + ex.getMessage(), ex);
            }
            if (!spec.permission.isEmpty()) {
                button.permission(spec.permission, null);
            }
            if (!spec.actions.isEmpty()) {
                var handler = actions.compile(spec.actions);
                button.onClick(handler);
            }
            builder.ingredient(e.getKey().charAt(0), button);
        }
        return builder.build();
    }
}
