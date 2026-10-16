package com.enderbyte.enderlib.scheduler;

import com.enderbyte.enderlib.core.Terminable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One shared tick loop per {@link com.enderbyte.enderlib.core.EnderLib} instance.
 *
 * <p>Animated menus, sidebars and holograms subscribe here instead of each starting their own
 * repeating task, so a thousand open menus cost one scheduler entry. The loop only runs while
 * something is subscribed.</p>
 *
 * <p>On Folia, entity-bound subscriptions run on their entity's own scheduler instead, because the
 * global thread may not touch players.</p>
 */
public final class Ticker {

    /** Work executed once per tick. */
    @FunctionalInterface
    public interface Tickable {
        /** @param tick monotonically increasing tick counter of this ticker */
        void tick(long tick);
    }

    private final Scheduler scheduler;
    private final Logger log;
    private final CopyOnWriteArrayList<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private Task loop = Task.NONE;
    private long tick;

    public Ticker(Scheduler scheduler, Logger log) {
        this.scheduler = scheduler;
        this.log = log;
    }

    /** Subscribes global work (no entity access on Folia). */
    public Terminable subscribe(Tickable tickable) {
        return add(new Subscription(null, tickable));
    }

    /**
     * Subscribes work bound to {@code entity}. The subscription ends by itself once the entity becomes
     * invalid (a player logging out, a mob dying).
     */
    public Terminable subscribe(Entity entity, Tickable tickable) {
        Objects.requireNonNull(entity, "entity");
        Subscription sub = new Subscription(entity, tickable);
        if (Scheduler.isFolia()) {
            sub.foliaTask = scheduler.runAtTimer(entity, () -> sub.run(++sub.localTick), 1L, 1L);
            return sub;
        }
        return add(sub);
    }

    /** Number of live subscriptions; zero means the loop is stopped. */
    public int size() {
        return subscriptions.size();
    }

    private synchronized Subscription add(Subscription sub) {
        subscriptions.add(sub);
        if (!loop.isActive()) {
            loop = scheduler.runTimer(this::runOnce, 1L, 1L);
        }
        return sub;
    }

    private void runOnce() {
        long now = ++tick;
        for (Subscription sub : subscriptions) {
            sub.run(now);
        }
        if (subscriptions.isEmpty()) {
            synchronized (this) {
                if (subscriptions.isEmpty()) {
                    loop.cancel();
                }
            }
        }
    }

    private final class Subscription implements Terminable {
        private final Entity entity;
        private final Tickable tickable;
        private final AtomicBoolean closed = new AtomicBoolean();
        private Task foliaTask = Task.NONE;
        private long localTick;

        private Subscription(Entity entity, Tickable tickable) {
            this.entity = entity;
            this.tickable = tickable;
        }

        void run(long now) {
            if (closed.get()) {
                return;
            }
            if (entity != null && !alive(entity)) {
                close();
                return;
            }
            try {
                tickable.tick(now);
            } catch (Throwable t) {
                log.error("Ticker subscription {} failed; it keeps running", tickable, t);
            }
        }

        private static boolean alive(Entity e) {
            // Dead players are "invalid" but still connected; only a logout should end their subscriptions
            return e instanceof Player p ? p.isOnline() : e.isValid();
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                subscriptions.remove(this);
                foliaTask.cancel();
            }
        }

        @Override
        public boolean isClosed() {
            return closed.get();
        }
    }
}
