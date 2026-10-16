package com.enderbyte.enderlib.display;

import com.enderbyte.enderlib.core.EnderLib;
import com.enderbyte.enderlib.core.Terminable;
import org.bukkit.Location;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates and tracks {@link Hologram}s of one plugin; all are removed on shutdown.
 */
public final class Holograms implements Terminable {

    private final EnderLib lib;
    private final Set<Hologram> holograms = ConcurrentHashMap.newKeySet();

    private Holograms(EnderLib lib) {
        this.lib = lib;
        lib.events().listen(PlayerQuitEvent.class).priority(EventPriority.MONITOR).handler(e -> {
            for (Hologram h : holograms) {
                h.forgetViewer(e.getPlayer().getUniqueId());
            }
        });
    }

    public static Holograms of(EnderLib lib) {
        return lib.service(Holograms.class, Holograms::new);
    }

    EnderLib lib() {
        return lib;
    }

    /** Starts a hologram at {@code location}; configure it, then call {@link Hologram#spawn()}. */
    public Hologram create(Location location) {
        Hologram h = new Hologram(this, location);
        holograms.add(h);
        return h;
    }

    public List<Hologram> all() {
        return List.copyOf(holograms);
    }

    void forget(Hologram hologram) {
        holograms.remove(hologram);
    }

    @Override
    public void close() {
        for (Hologram h : List.copyOf(holograms)) {
            h.close();
        }
        holograms.clear();
    }
}
