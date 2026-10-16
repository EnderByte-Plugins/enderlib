package com.enderbyte.enderlib.itemsadder;

import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;

/**
 * A progress bar made of glyph segments.
 *
 * <pre>{@code
 * GlyphProgressBar mana = GlyphProgressBar.create()
 *         .filled(Glyph.require("hud:mana_full"))
 *         .empty(Glyph.require("hud:mana_empty"))
 *         .segments(10)
 *         .spacing(-1);
 * player.sendActionBar(mana.render(current / max));
 * }</pre>
 */
public final class GlyphProgressBar {

    private @Nullable Glyph filled;
    private @Nullable Glyph half;
    private @Nullable Glyph empty;
    private @Nullable Glyph leftEdge;
    private @Nullable Glyph rightEdge;
    private int segments = 10;
    private int spacing;

    private GlyphProgressBar() {
    }

    public static GlyphProgressBar create() {
        return new GlyphProgressBar();
    }

    public GlyphProgressBar filled(Glyph glyph) {
        this.filled = glyph;
        return this;
    }

    /** Optional half-filled segment, giving twice the resolution. */
    public GlyphProgressBar half(Glyph glyph) {
        this.half = glyph;
        return this;
    }

    public GlyphProgressBar empty(Glyph glyph) {
        this.empty = glyph;
        return this;
    }

    public GlyphProgressBar edges(@Nullable Glyph left, @Nullable Glyph right) {
        this.leftEdge = left;
        this.rightEdge = right;
        return this;
    }

    public GlyphProgressBar segments(int segments) {
        this.segments = Math.max(1, segments);
        return this;
    }

    /** Pixels between segments (negative to overlap). */
    public GlyphProgressBar spacing(int pixels) {
        this.spacing = pixels;
        return this;
    }

    /** Renders {@code progress} in [0, 1]. */
    public Component render(double progress) {
        double clamped = Math.max(0, Math.min(1, progress));
        int halves = (int) Math.round(clamped * segments * 2);
        GlyphBuilder b = GlyphBuilder.create();
        if (leftEdge != null) {
            b.glyph(leftEdge);
            b.shift(spacing);
        }
        for (int i = 0; i < segments; i++) {
            int segmentHalves = Math.max(0, Math.min(2, halves - i * 2));
            // Without a half glyph, half segments round down to empty
            Glyph g = switch (segmentHalves) {
                case 2 -> filled;
                case 1 -> half != null ? half : empty;
                default -> empty;
            };
            if (g != null) {
                b.glyph(g);
            }
            if (i < segments - 1) {
                b.shift(spacing);
            }
        }
        if (rightEdge != null) {
            b.shift(spacing);
            b.glyph(rightEdge);
        }
        return b.build();
    }
}
