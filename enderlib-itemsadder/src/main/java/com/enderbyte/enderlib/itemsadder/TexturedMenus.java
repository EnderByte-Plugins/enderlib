package com.enderbyte.enderlib.itemsadder;

import net.kyori.adventure.text.Component;

import java.util.function.UnaryOperator;

/**
 * Resource-pack backgrounds for EnderLib menus.
 *
 * <pre>{@code
 * Menu shop = Menu.chest(6)
 *         .title("<dark_gray>Shop")
 *         .titleDecorator(TexturedMenus.background("mygui:shop_bg"))
 *         ...
 *         .build();
 * }</pre>
 *
 * The decorator draws the background glyph behind the menu and shifts the title text back to
 * where it belongs, so the rest of the menu API (live titles, pagination tags) keeps working.
 * Without ItemsAdder, or if the image does not exist, the title is left untouched.
 */
public final class TexturedMenus {

    /** Default offsets matching ItemsAdder's {@code TexturedInventoryWrapper}. */
    public static final int DEFAULT_TEXTURE_OFFSET = -8;
    public static final int DEFAULT_TITLE_OFFSET = 16;

    private TexturedMenus() {
    }

    public static UnaryOperator<Component> background(String fontImageId) {
        return background(fontImageId, DEFAULT_TEXTURE_OFFSET, DEFAULT_TITLE_OFFSET);
    }

    /**
     * @param textureOffset where the background starts, relative to the normal title position
     * @param titleOffset   where the title text starts, relative to the normal title position
     */
    public static UnaryOperator<Component> background(String fontImageId, int textureOffset, int titleOffset) {
        return title -> Glyph.of(fontImageId).<Component>map(glyph -> {
            int afterGlyph = textureOffset + glyph.width() + 1;
            return Component.text()
                    .append(glyph.component(textureOffset))
                    .append(Component.text(Glyph.space(titleOffset - afterGlyph)))
                    .append(title)
                    .build();
        }).orElse(title);
    }
}
