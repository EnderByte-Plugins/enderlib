package com.enderbyte.enderlib.itemsadder;

import com.enderbyte.enderlib.text.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * Composes glyphs, pixel shifts and text into one component: HUD overlays, GUI titles, badges.
 *
 * <pre>{@code
 * Component hud = GlyphBuilder.create()
 *         .glyph(Glyph.require("hud:panel"), -91)
 *         .shift(-170)
 *         .text("<white>Level 12")
 *         .build();
 * }</pre>
 */
public final class GlyphBuilder {

    private final TextComponent.Builder out = Component.text();
    private int width;

    private GlyphBuilder() {
    }

    public static GlyphBuilder create() {
        return new GlyphBuilder();
    }

    public GlyphBuilder glyph(Glyph glyph) {
        out.append(glyph.component());
        width += glyph.width() + 1;
        return this;
    }

    /** A glyph drawn {@code pixels} from the current position, without moving the cursor afterwards. */
    public GlyphBuilder glyph(Glyph glyph, int pixels) {
        out.append(glyph.component(pixels));
        out.append(Component.text(Glyph.space(-(pixels + glyph.width() + 1))));
        return this;
    }

    /** Moves the cursor; negative values move left. */
    public GlyphBuilder shift(int pixels) {
        out.append(Component.text(Glyph.space(pixels)));
        width += pixels;
        return this;
    }

    /** MiniMessage text. */
    public GlyphBuilder text(String miniMessage) {
        out.append(Text.mm(miniMessage));
        return this;
    }

    public GlyphBuilder text(Component text) {
        out.append(text);
        return this;
    }

    /** Approximate width in pixels of glyphs and shifts (text is not measured). */
    public int width() {
        return width;
    }

    public Component build() {
        return out.build();
    }

    public TagResolver tag(String name) {
        Component built = build();
        return TagResolver.resolver(name, Tag.selfClosingInserting(built));
    }
}
