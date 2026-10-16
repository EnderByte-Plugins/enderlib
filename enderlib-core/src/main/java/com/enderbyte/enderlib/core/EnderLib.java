package com.enderbyte.enderlib.core;

import com.enderbyte.enderlib.event.Events;
import com.enderbyte.enderlib.scheduler.Scheduler;
import com.enderbyte.enderlib.scheduler.Ticker;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventPriority;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Entry point of EnderLib for one plugin.
 *
 * <pre>{@code
 * public final class MyPlugin extends JavaPlugin {
 *     private EnderLib lib;
 *
 *     public void onEnable() {
 *         lib = EnderLib.create(this);
 *         Menus menus = Menus.of(lib);      // modules hang off the same instance
 *     }
 *
 *     public void onDisable() {
 *         lib.shutdown();
 *     }
 * }
 * }</pre>
 *
 * <p>There is deliberately no global singleton. Each plugin shades (and relocates) its own copy of
 * EnderLib and creates its own instance, so two plugins on one server never share or close each
 * other's listeners, menus or tasks.</p>
 *
 * <p>Modules (menus, commands, items, ...) attach to an instance through {@link #service}, which
 * creates each module's manager once and closes it on {@link #shutdown()}.</p>
 */
public final class EnderLib implements Terminable {

    private final JavaPlugin plugin;
    private final Logger log;
    private final CompositeTerminable terminables = CompositeTerminable.create();
    private final Scheduler scheduler;
    private final Ticker ticker;
    private final Events events;
    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();
    private volatile boolean shutdown;

    private EnderLib(JavaPlugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getSLF4JLogger();
        this.scheduler = new Scheduler(plugin, terminables);
        this.ticker = new Ticker(scheduler, log);
        this.events = new Events(plugin, terminables);

        // Safety net: if the plugin forgets lib.shutdown(), clean up when it is disabled anyway.
        events.listen(PluginDisableEvent.class)
                .priority(EventPriority.MONITOR)
                .filter(e -> e.getPlugin() == plugin)
                .handler(e -> shutdown());
    }

    /** Creates the EnderLib instance for {@code plugin}. Call once, in {@code onEnable}. */
    public static EnderLib create(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        return new EnderLib(plugin);
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public Logger logger() {
        return log;
    }

    public Scheduler scheduler() {
        return scheduler;
    }

    /** The shared per-plugin tick loop used by animated menus, sidebars and holograms. */
    public Ticker ticker() {
        return ticker;
    }

    public Events events() {
        return events;
    }

    /** Everything registered here is closed by {@link #shutdown()}. */
    public CompositeTerminable terminables() {
        return terminables;
    }

    /** Binds {@code terminable} to this instance's lifetime. */
    public <T extends AutoCloseable> T bind(T terminable) {
        return terminables.with(terminable);
    }

    /** A key in the plugin's namespace. */
    public NamespacedKey key(String value) {
        return new NamespacedKey(plugin, value);
    }

    /**
     * Returns the module service of {@code type}, creating it on first use. A service that is
     * {@link AutoCloseable} is closed on shutdown.
     *
     * <p>Module entry points use this, for example {@code Menus.of(lib)}; plugins rarely call it.</p>
     */
    public <S> S service(Class<S> type, Function<EnderLib, ? extends S> factory) {
        Objects.requireNonNull(type, "type");
        if (shutdown) {
            throw new IllegalStateException("EnderLib for " + plugin.getName() + " is shut down");
        }
        Object existing = services.get(type);
        if (existing == null) {
            synchronized (services) {
                existing = services.get(type);
                if (existing == null) {
                    existing = Objects.requireNonNull(factory.apply(this), "factory returned null");
                    services.put(type, existing);
                    if (existing instanceof AutoCloseable closeable) {
                        terminables.with(closeable);
                    }
                }
            }
        }
        return type.cast(existing);
    }

    /** Returns a service only if it was already created. */
    public <S> @Nullable S serviceIfPresent(Class<S> type) {
        return type.cast(services.get(type));
    }

    public boolean isShutdown() {
        return shutdown;
    }

    /**
     * Closes every service, listener, menu and repeating task created through this instance.
     * Idempotent; call it from {@code onDisable}.
     */
    public void shutdown() {
        if (shutdown) {
            return;
        }
        shutdown = true;
        terminables.close();
        services.clear();
    }

    @Override
    public void close() {
        shutdown();
    }

    @Override
    public boolean isClosed() {
        return shutdown;
    }
}
