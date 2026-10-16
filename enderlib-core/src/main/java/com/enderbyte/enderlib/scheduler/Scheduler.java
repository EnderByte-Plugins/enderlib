package com.enderbyte.enderlib.scheduler;

import com.enderbyte.enderlib.core.CompositeTerminable;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Folia-safe scheduling facade.
 *
 * <p>On Paper every "region" and "entity" method simply runs on the main thread. On Folia the same
 * calls land on the thread that owns the region or entity, so code written against this class
 * runs unchanged on both.</p>
 *
 * <p>The executors are the bridge for asynchronous database work:</p>
 * <pre>{@code
 * database.queryAsync("SELECT ...", uuid)
 *         .thenAcceptAsync(result -> player.sendMessage(...), lib.scheduler().entity(player));
 * }</pre>
 */
public final class Scheduler {

    private static final boolean FOLIA = classExists("io.papermc.paper.threadedregions.RegionizedServer");

    private final Plugin plugin;
    private final CompositeTerminable owner;
    private final Executor sync = this::executeSync;
    private final Executor async = this::executeAsync;

    public Scheduler(Plugin plugin, CompositeTerminable owner) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    /** Whether the server is running Folia (regionised multithreading). */
    public static boolean isFolia() {
        return FOLIA;
    }

    /** Whether the current thread is the main thread (Paper) or the global region thread (Folia). */
    public boolean isMainThread() {
        return FOLIA ? Bukkit.isGlobalTickThread() : Bukkit.isPrimaryThread();
    }

    /** Whether the current thread may touch {@code entity}. */
    public boolean owns(Entity entity) {
        return FOLIA ? Bukkit.isOwnedByCurrentRegion(entity) : Bukkit.isPrimaryThread();
    }

    /** Whether the current thread may touch blocks at {@code location}. */
    public boolean owns(Location location) {
        return FOLIA ? Bukkit.isOwnedByCurrentRegion(location) : Bukkit.isPrimaryThread();
    }

    // ------------------------------------------------------------------ executors

    /**
     * Main-thread (global region) executor. Runs inline when already on that thread, otherwise on the
     * next tick.
     */
    public Executor sync() {
        return sync;
    }

    /** Executor backed by Paper's async scheduler pool. */
    public Executor async() {
        return async;
    }

    /**
     * Executor that runs on the thread owning {@code entity}. Work submitted after the entity was
     * removed (for example, the player logged out) is silently dropped.
     */
    public Executor entity(Entity entity) {
        Objects.requireNonNull(entity, "entity");
        return task -> {
            if (owns(entity)) {
                task.run();
            } else {
                entity.getScheduler().execute(plugin, task, null, 1L);
            }
        };
    }

    /** Executor that runs on the thread owning the region at {@code location}. */
    public Executor region(Location location) {
        Objects.requireNonNull(location, "location");
        return task -> {
            if (owns(location)) {
                task.run();
            } else {
                Bukkit.getRegionScheduler().execute(plugin, location, task);
            }
        };
    }

    // ------------------------------------------------------------------ global / main

    /** Runs on the main thread on the next tick. */
    public Task run(Runnable task) {
        return new Task(Bukkit.getGlobalRegionScheduler().run(plugin, t -> task.run()));
    }

    public Task runLater(Runnable task, long delayTicks) {
        return new Task(Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> task.run(), atLeastOne(delayTicks)));
    }

    /** Repeating main-thread task. Bound to this library instance: it stops on shutdown. */
    public Task runTimer(Runnable task, long delayTicks, long periodTicks) {
        return track(new Task(Bukkit.getGlobalRegionScheduler()
                .runAtFixedRate(plugin, t -> task.run(), atLeastOne(delayTicks), atLeastOne(periodTicks))));
    }

    /** Repeating main-thread task that receives its own handle, so it can cancel itself. */
    public Task runTimer(Consumer<Task> task, long delayTicks, long periodTicks) {
        Task[] self = new Task[1];
        self[0] = track(new Task(Bukkit.getGlobalRegionScheduler()
                .runAtFixedRate(plugin, t -> task.accept(self[0]), atLeastOne(delayTicks), atLeastOne(periodTicks))));
        return self[0];
    }

    // ------------------------------------------------------------------ async

    public Task runAsync(Runnable task) {
        return new Task(Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run()));
    }

    public Task runAsyncLater(Runnable task, Duration delay) {
        return new Task(Bukkit.getAsyncScheduler()
                .runDelayed(plugin, t -> task.run(), Math.max(1, delay.toMillis()), TimeUnit.MILLISECONDS));
    }

    public Task runAsyncTimer(Runnable task, Duration delay, Duration period) {
        return track(new Task(Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> task.run(),
                Math.max(1, delay.toMillis()), Math.max(1, period.toMillis()), TimeUnit.MILLISECONDS)));
    }

    // ------------------------------------------------------------------ entity

    /**
     * Runs on the thread owning {@code entity}; {@code retired} runs instead if the entity is removed
     * before the task fires.
     */
    public Task runAt(Entity entity, Runnable task, @Nullable Runnable retired) {
        return nonNull(entity.getScheduler().run(plugin, t -> task.run(), retired));
    }

    public Task runAt(Entity entity, Runnable task) {
        return runAt(entity, task, null);
    }

    public Task runAtLater(Entity entity, Runnable task, long delayTicks) {
        return nonNull(entity.getScheduler().runDelayed(plugin, t -> task.run(), null, atLeastOne(delayTicks)));
    }

    public Task runAtTimer(Entity entity, Runnable task, long delayTicks, long periodTicks) {
        return track(nonNull(entity.getScheduler()
                .runAtFixedRate(plugin, t -> task.run(), null, atLeastOne(delayTicks), atLeastOne(periodTicks))));
    }

    // ------------------------------------------------------------------ region

    public Task runAt(Location location, Runnable task) {
        return new Task(Bukkit.getRegionScheduler().run(plugin, location, t -> task.run()));
    }

    public Task runAtLater(Location location, Runnable task, long delayTicks) {
        return new Task(Bukkit.getRegionScheduler().runDelayed(plugin, location, t -> task.run(), atLeastOne(delayTicks)));
    }

    public Task runAtTimer(Location location, Runnable task, long delayTicks, long periodTicks) {
        return track(new Task(Bukkit.getRegionScheduler()
                .runAtFixedRate(plugin, location, t -> task.run(), atLeastOne(delayTicks), atLeastOne(periodTicks))));
    }

    // ------------------------------------------------------------------ futures

    /** Computes a value on the main thread. */
    public <T> CompletableFuture<T> supplySync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, sync);
    }

    /** Computes a value on the thread owning {@code entity}. */
    public <T> CompletableFuture<T> supplyAt(Entity entity, Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, entity(entity));
    }

    /** Re-completes {@code future} on the main thread, so dependent stages may touch the Bukkit API. */
    public <T> CompletableFuture<T> onMain(CompletableFuture<T> future) {
        return future.whenCompleteAsync((v, e) -> { }, sync);
    }

    /** Re-completes {@code future} on the thread owning {@code entity}. */
    public <T> CompletableFuture<T> onEntity(Entity entity, CompletableFuture<T> future) {
        return future.whenCompleteAsync((v, e) -> { }, entity(entity));
    }

    /** Throws if the caller is not on the main thread. Use at the top of methods that touch shared state. */
    public void ensureMain(String operation) {
        if (!isMainThread()) {
            throw new IllegalStateException(operation + " must be called on the main thread, not "
                    + Thread.currentThread().getName());
        }
    }

    // ------------------------------------------------------------------ internals

    private void executeSync(Runnable task) {
        if (isMainThread()) {
            task.run();
        } else {
            Bukkit.getGlobalRegionScheduler().execute(plugin, task);
        }
    }

    private void executeAsync(Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run());
    }

    private Task track(Task task) {
        return owner.with(task);
    }

    private static Task nonNull(io.papermc.paper.threadedregions.scheduler.@Nullable ScheduledTask handle) {
        return handle == null ? Task.NONE : new Task(handle);
    }

    private static long atLeastOne(long ticks) {
        return Math.max(1L, ticks);
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
