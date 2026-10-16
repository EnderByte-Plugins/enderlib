package com.enderbyte.enderlib.event;

import com.enderbyte.enderlib.core.CompositeTerminable;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

/**
 * Functional event subscriptions.
 *
 * <pre>{@code
 * lib.events().listen(PlayerJoinEvent.class)
 *         .priority(EventPriority.MONITOR)
 *         .filter(e -> !e.getPlayer().hasPlayedBefore())
 *         .handler(e -> e.getPlayer().sendMessage(welcome));
 *
 * // One-shot: wait for the player's next chat message
 * lib.events().listen(AsyncChatEvent.class)
 *         .filter(e -> e.getPlayer().equals(player))
 *         .expireAfterCalls(1)
 *         .expireAfter(Duration.ofSeconds(30))
 *         .handler(e -> ...);
 * }</pre>
 *
 * Every subscription is bound to the owning {@link com.enderbyte.enderlib.core.EnderLib} and ends on
 * shutdown; closing the returned {@link EventSubscription} ends it earlier.
 */
public final class Events {

    private final Plugin plugin;
    private final CompositeTerminable owner;

    public Events(Plugin plugin, CompositeTerminable owner) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    /** Starts a subscription to {@code type} (and its subclasses that share its handler list). */
    public <T extends Event> EventSubscriptionBuilder<T> listen(Class<T> type) {
        return new EventSubscriptionBuilder<>(plugin, owner, type);
    }

    /**
     * Fires {@code event} through Bukkit and returns it, so callers can read its cancelled state.
     */
    public static <T extends Event> T call(T event) {
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    /** Fires a {@link org.bukkit.event.Cancellable} event and returns {@code true} if nobody cancelled it. */
    public static boolean callAndCheck(Event event) {
        Bukkit.getPluginManager().callEvent(event);
        return !(event instanceof org.bukkit.event.Cancellable c) || !c.isCancelled();
    }
}
