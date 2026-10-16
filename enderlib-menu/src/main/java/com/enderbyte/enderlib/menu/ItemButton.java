package com.enderbyte.enderlib.menu;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The standard {@link Button}: an item plus click handlers. Configure it fluently, then place it.
 *
 * <p>Handler resolution per click: an exact {@link ClickType} handler wins; otherwise shift, then
 * left/right handlers; otherwise every {@link #onClick} handler runs.</p>
 */
public final class ItemButton implements Button {

    private final Function<MenuView, @Nullable ItemStack> renderer;
    private final List<Consumer<ClickContext>> handlers = new ArrayList<>();
    private final Map<ClickType, Consumer<ClickContext>> byType = new EnumMap<>(ClickType.class);
    private @Nullable Consumer<ClickContext> onLeft;
    private @Nullable Consumer<ClickContext> onRight;
    private @Nullable Consumer<ClickContext> onShift;
    private @Nullable Sound sound;
    private float pitch = 1f;
    private int cooldownTicks;
    private int updateInterval;
    private @Nullable Predicate<MenuView> visible;
    private @Nullable Predicate<Player> requirement;
    private @Nullable Consumer<ClickContext> denied;

    ItemButton(Function<MenuView, @Nullable ItemStack> renderer) {
        this.renderer = renderer;
    }

    // ------------------------------------------------------------------ configuration

    /** Runs on any click not claimed by a more specific handler. */
    public ItemButton onClick(Consumer<ClickContext> handler) {
        handlers.add(Objects.requireNonNull(handler, "handler"));
        return this;
    }

    /** Runs only for exactly {@code type}. */
    public ItemButton onClick(ClickType type, Consumer<ClickContext> handler) {
        byType.put(type, Objects.requireNonNull(handler, "handler"));
        return this;
    }

    public ItemButton onLeft(Consumer<ClickContext> handler) {
        this.onLeft = handler;
        return this;
    }

    public ItemButton onRight(Consumer<ClickContext> handler) {
        this.onRight = handler;
        return this;
    }

    /** Shift + left or right click. */
    public ItemButton onShift(Consumer<ClickContext> handler) {
        this.onShift = handler;
        return this;
    }

    public ItemButton onMiddle(Consumer<ClickContext> handler) {
        return onClick(ClickType.MIDDLE, handler);
    }

    public ItemButton onDrop(Consumer<ClickContext> handler) {
        onClick(ClickType.DROP, handler);
        return onClick(ClickType.CONTROL_DROP, handler);
    }

    /** Plays {@code sound} to the clicker on every handled click. */
    public ItemButton sound(Sound sound) {
        return sound(sound, 1f);
    }

    public ItemButton sound(Sound sound, float pitch) {
        this.sound = sound;
        this.pitch = pitch;
        return this;
    }

    /** Minimum ticks between handled clicks on this button, per viewer. */
    public ItemButton cooldown(int ticks) {
        this.cooldownTicks = Math.max(0, ticks);
        return this;
    }

    /** Re-render every {@code ticks} ticks (clocks, countdowns, live stats). */
    public ItemButton updateEvery(int ticks) {
        this.updateInterval = Math.max(0, ticks);
        return this;
    }

    /** Hides the button (renders an empty, unclickable slot) unless {@code condition} holds. */
    public ItemButton visibleIf(Predicate<MenuView> condition) {
        this.visible = condition;
        return this;
    }

    /** Only players matching {@code requirement} may click; others trigger {@code denied} (if set). */
    public ItemButton requires(Predicate<Player> requirement, @Nullable Consumer<ClickContext> denied) {
        this.requirement = requirement;
        this.denied = denied;
        return this;
    }

    /** Shorthand for a permission requirement. */
    public ItemButton permission(String permission, @Nullable Consumer<ClickContext> denied) {
        return requires(p -> p.hasPermission(permission), denied);
    }

    // ------------------------------------------------------------------ Button

    @Override
    public @Nullable ItemStack render(MenuView view) {
        if (visible != null && !visible.test(view)) {
            return null;
        }
        return renderer.apply(view);
    }

    @Override
    public void click(ClickContext ctx) {
        if (visible != null && !visible.test(ctx.view())) {
            return;
        }
        if (requirement != null && !requirement.test(ctx.player())) {
            if (denied != null) {
                denied.accept(ctx);
            }
            return;
        }
        if (cooldownTicks > 0 && !ctx.view().tryClickCooldown(ctx.slot(), cooldownTicks)) {
            return;
        }
        Consumer<ClickContext> exact = byType.get(ctx.type());
        boolean handled = true;
        if (exact != null) {
            exact.accept(ctx);
        } else if (ctx.isShift() && onShift != null) {
            onShift.accept(ctx);
        } else if (ctx.isLeft() && onLeft != null) {
            onLeft.accept(ctx);
        } else if (ctx.isRight() && onRight != null) {
            onRight.accept(ctx);
        } else if (!handlers.isEmpty()) {
            for (Consumer<ClickContext> h : handlers) {
                h.accept(ctx);
            }
        } else {
            handled = false;
        }
        if (handled && sound != null) {
            ctx.sound(sound, 1f, pitch);
        }
    }

    @Override
    public int updateInterval() {
        return updateInterval;
    }
}
