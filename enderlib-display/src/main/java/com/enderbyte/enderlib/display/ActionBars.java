package com.enderbyte.enderlib.display;

import com.enderbyte.enderlib.core.EnderLib;
import com.enderbyte.enderlib.core.Terminable;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Persistent action bars. Vanilla action bars fade after about three seconds; entries here are
 * re-sent until they expire or are removed. When several entries target one player, the highest
 * priority wins (ties: the newest).
 *
 * <pre>{@code
 * ActionBars bars = ActionBars.of(lib);
 * bars.show(player, "combat", p -> Text.mm("<red>In combat: " + combat.secondsLeft(p) + "s"), 100, Duration.ofSeconds(15));
 * bars.show(player, "zone", Text.mm("<green>Safe zone"), 10, null);   // until removed
 * bars.remove(player, "combat");
 * }</pre>
 */
public final class ActionBars implements Terminable {

    private record Entry(Function<Player, Component> content, int priority, long expiresAt, long order) {
    }

    private final EnderLib lib;
    private final Map<UUID, Map<String, Entry>> entries = new ConcurrentHashMap<>();
    private final Terminable loop;
    private long counter;

    private ActionBars(EnderLib lib) {
        this.lib = lib;
        lib.events().listen(PlayerQuitEvent.class).priority(EventPriority.MONITOR)
                .handler(e -> entries.remove(e.getPlayer().getUniqueId()));
        this.loop = lib.ticker().subscribe(tick -> {
            if (tick % 20 == 0 && !entries.isEmpty()) {
                sendAll();
            }
        });
    }

    public static ActionBars of(EnderLib lib) {
        return lib.service(ActionBars.class, ActionBars::new);
    }

    /**
     * Shows (or replaces) entry {@code key} for {@code player}.
     *
     * @param duration how long it stays; {@code null} until {@link #remove}
     */
    public void show(Player player, String key, Function<Player, Component> content, int priority, @Nullable Duration duration) {
        long expires = duration == null ? Long.MAX_VALUE : System.currentTimeMillis() + duration.toMillis();
        entries.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(key, new Entry(content, priority, expires, ++counter));
        send(player);
    }

    public void show(Player player, String key, Component content, int priority, @Nullable Duration duration) {
        show(player, key, p -> content, priority, duration);
    }

    public void remove(Player player, String key) {
        Map<String, Entry> map = entries.get(player.getUniqueId());
        if (map != null && map.remove(key) != null) {
            send(player);
        }
    }

    public void clear(Player player) {
        if (entries.remove(player.getUniqueId()) != null) {
            player.sendActionBar(Component.empty());
        }
    }

    private void sendAll() {
        for (UUID id : entries.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                entries.remove(id);
            } else {
                lib.scheduler().runAt(p, () -> send(p));
            }
        }
    }

    private void send(Player player) {
        Map<String, Entry> map = entries.get(player.getUniqueId());
        if (map == null) {
            return;
        }
        long now = System.currentTimeMillis();
        map.values().removeIf(e -> e.expiresAt() <= now);
        Entry best = null;
        for (Entry e : map.values()) {
            if (best == null || e.priority() > best.priority() || e.priority() == best.priority() && e.order() > best.order()) {
                best = e;
            }
        }
        if (best == null) {
            entries.remove(player.getUniqueId(), map);
            player.sendActionBar(Component.empty());
            return;
        }
        try {
            player.sendActionBar(best.content().apply(player));
        } catch (Throwable t) {
            lib.logger().error("Action bar content for {} failed", player.getName(), t);
        }
    }

    @Override
    public void close() {
        loop.close();
        entries.clear();
    }
}
