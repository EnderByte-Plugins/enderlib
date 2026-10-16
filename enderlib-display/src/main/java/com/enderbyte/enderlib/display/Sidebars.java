package com.enderbyte.enderlib.display;

import com.enderbyte.enderlib.core.EnderLib;
import com.enderbyte.enderlib.core.Terminable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the per-player {@link Sidebar}s of one plugin; one sidebar per player at a time.
 * Sidebars are disposed when their player quits and on shutdown.
 */
public final class Sidebars implements Terminable {

    private final EnderLib lib;
    private final Map<UUID, Sidebar> sidebars = new ConcurrentHashMap<>();

    private Sidebars(EnderLib lib) {
        this.lib = lib;
        lib.events().listen(PlayerQuitEvent.class).priority(EventPriority.MONITOR).handler(e -> {
            Sidebar s = sidebars.remove(e.getPlayer().getUniqueId());
            if (s != null) {
                s.close();
            }
        });
    }

    public static Sidebars of(EnderLib lib) {
        return lib.service(Sidebars.class, Sidebars::new);
    }

    EnderLib lib() {
        return lib;
    }

    /** Creates a sidebar for {@code player}, replacing (and closing) any previous one. */
    public Sidebar create(Player player) {
        Sidebar sidebar = new Sidebar(this, player);
        Sidebar old = sidebars.put(player.getUniqueId(), sidebar);
        if (old != null) {
            old.close();
        }
        return sidebar;
    }

    public Optional<Sidebar> get(Player player) {
        return Optional.ofNullable(sidebars.get(player.getUniqueId()));
    }

    public void remove(Player player) {
        Sidebar s = sidebars.remove(player.getUniqueId());
        if (s != null) {
            s.close();
        }
    }

    void forget(Sidebar sidebar) {
        sidebars.remove(sidebar.player().getUniqueId(), sidebar);
    }

    @Override
    public void close() {
        for (Sidebar s : List.copyOf(sidebars.values())) {
            s.close();
        }
        sidebars.clear();
    }
}
