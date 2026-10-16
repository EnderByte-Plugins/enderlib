package com.enderbyte.enderlib.display;

import com.enderbyte.enderlib.core.EnderLib;
import com.enderbyte.enderlib.core.Terminable;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Boss bar helpers: timed bars, countdowns and server-wide bars that follow players as they join.
 *
 * <pre>{@code
 * BossBars bars = BossBars.of(lib);
 * bars.countdown(player, Duration.ofSeconds(30), left -> Text.mm("<red>Arena closes in " + left.toSeconds() + "s"),
 *         BossBar.Color.RED);
 * BossBar event = bars.global(BossBar.bossBar(Text.mm("<gold>Double XP weekend!"), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS));
 * }</pre>
 *
 * Every bar shown through this service is hidden on shutdown.
 */
public final class BossBars implements Terminable {

    private final EnderLib lib;
    private final Map<BossBar, Set<UUID>> viewers = new ConcurrentHashMap<>();
    private final Set<BossBar> globals = ConcurrentHashMap.newKeySet();

    private BossBars(EnderLib lib) {
        this.lib = lib;
        lib.events().listen(PlayerJoinEvent.class).priority(EventPriority.MONITOR).handler(e -> {
            for (BossBar bar : globals) {
                show(e.getPlayer(), bar);
            }
        });
    }

    public static BossBars of(EnderLib lib) {
        return lib.service(BossBars.class, BossBars::new);
    }

    public void show(Player player, BossBar bar) {
        player.showBossBar(bar);
        viewers.computeIfAbsent(bar, b -> ConcurrentHashMap.newKeySet()).add(player.getUniqueId());
    }

    public void hide(Player player, BossBar bar) {
        player.hideBossBar(bar);
        Set<UUID> set = viewers.get(bar);
        if (set != null) {
            set.remove(player.getUniqueId());
        }
    }

    /** Shows {@code bar} to {@code player} for {@code duration}. */
    public void show(Player player, BossBar bar, Duration duration) {
        show(player, bar);
        lib.scheduler().runAtLater(player, () -> hide(player, bar), Math.max(1, duration.toMillis() / 50));
    }

    /**
     * A bar whose progress drains from full to empty over {@code duration}, with a title that can
     * show the time left. Returns the bar; it hides itself at the end.
     */
    public BossBar countdown(Player player, Duration duration, Function<Duration, Component> title, BossBar.Color color) {
        BossBar bar = BossBar.bossBar(title.apply(duration), 1f, color, BossBar.Overlay.PROGRESS);
        show(player, bar);
        long totalTicks = Math.max(1, duration.toMillis() / 50);
        long[] elapsed = {0};
        Terminable[] sub = new Terminable[1];
        sub[0] = lib.ticker().subscribe(player, tick -> {
            elapsed[0]++;
            float progress = Math.max(0f, 1f - (float) elapsed[0] / totalTicks);
            bar.progress(progress);
            if (elapsed[0] % 20 == 0 || progress == 0f) {
                bar.name(title.apply(Duration.ofMillis(Math.max(0, (totalTicks - elapsed[0]) * 50))));
            }
            if (elapsed[0] >= totalTicks) {
                hide(player, bar);
                sub[0].close();
            }
        });
        return bar;
    }

    /** Shows {@code bar} to everyone online now and everyone who joins later, until {@link #removeGlobal}. */
    public BossBar global(BossBar bar) {
        globals.add(bar);
        for (Player p : Bukkit.getOnlinePlayers()) {
            show(p, bar);
        }
        return bar;
    }

    public void removeGlobal(BossBar bar) {
        globals.remove(bar);
        hideEverywhere(bar);
    }

    /** Hides {@code bar} from everyone it was shown to through this service. */
    public void hideEverywhere(BossBar bar) {
        Set<UUID> set = viewers.remove(bar);
        if (set == null) {
            return;
        }
        for (UUID id : set) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.hideBossBar(bar);
            }
        }
    }

    @Override
    public void close() {
        globals.clear();
        for (BossBar bar : Set.copyOf(viewers.keySet())) {
            hideEverywhere(bar);
        }
    }
}
