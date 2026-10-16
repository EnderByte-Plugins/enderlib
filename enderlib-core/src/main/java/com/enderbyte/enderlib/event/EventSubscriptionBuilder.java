package com.enderbyte.enderlib.event;

import com.enderbyte.enderlib.core.CompositeTerminable;
import org.bukkit.Bukkit;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Configures an {@link EventSubscription}. Obtain one from {@link Events#listen(Class)}.
 */
public final class EventSubscriptionBuilder<T extends Event> {

    private final Plugin plugin;
    private final CompositeTerminable owner;
    private final Class<T> type;

    private EventPriority priority = EventPriority.NORMAL;
    private boolean ignoreCancelled;
    private final List<Predicate<? super T>> filters = new ArrayList<>();
    private final List<Predicate<? super T>> expiryTests = new ArrayList<>();
    private long maxCalls = -1;
    private @Nullable Duration lifetime;
    private BiConsumer<T, Throwable> exceptionHandler;

    EventSubscriptionBuilder(Plugin plugin, CompositeTerminable owner, Class<T> type) {
        this.plugin = plugin;
        this.owner = owner;
        this.type = Objects.requireNonNull(type, "type");
        this.exceptionHandler = (event, error) -> plugin.getSLF4JLogger()
                .error("Unhandled exception in {} handler", type.getSimpleName(), error);
    }

    public EventSubscriptionBuilder<T> priority(EventPriority priority) {
        this.priority = Objects.requireNonNull(priority, "priority");
        return this;
    }

    /** Skips events another handler already cancelled. */
    public EventSubscriptionBuilder<T> ignoreCancelled() {
        this.ignoreCancelled = true;
        return this;
    }

    /** Only events matching every filter reach the handler. Filters run in registration order. */
    public EventSubscriptionBuilder<T> filter(Predicate<? super T> filter) {
        filters.add(Objects.requireNonNull(filter, "filter"));
        return this;
    }

    /** Ends the subscription after the handler ran {@code calls} times. */
    public EventSubscriptionBuilder<T> expireAfterCalls(long calls) {
        if (calls < 1) {
            throw new IllegalArgumentException("calls must be >= 1");
        }
        this.maxCalls = calls;
        return this;
    }

    /** Ends the subscription after {@code duration}, whether or not it ever fired. */
    public EventSubscriptionBuilder<T> expireAfter(Duration duration) {
        this.lifetime = Objects.requireNonNull(duration, "duration");
        return this;
    }

    /** Ends the subscription (without handling) the first time an event matches {@code test}. */
    public EventSubscriptionBuilder<T> expireIf(Predicate<? super T> test) {
        expiryTests.add(Objects.requireNonNull(test, "test"));
        return this;
    }

    public EventSubscriptionBuilder<T> exceptionHandler(BiConsumer<T, Throwable> handler) {
        this.exceptionHandler = Objects.requireNonNull(handler, "handler");
        return this;
    }

    /** Registers the subscription. */
    public EventSubscription<T> handler(Consumer<? super T> handler) {
        Objects.requireNonNull(handler, "handler");
        return handler((sub, event) -> handler.accept(event));
    }

    /** Registers the subscription with a handler that can close its own subscription. */
    public EventSubscription<T> handler(BiConsumer<EventSubscription<T>, ? super T> handler) {
        Objects.requireNonNull(handler, "handler");
        Sub sub = new Sub(handler);
        Bukkit.getPluginManager().registerEvent(type, sub, priority, (l, e) -> sub.dispatch(e), plugin, ignoreCancelled);
        owner.with(sub);
        if (lifetime != null) {
            long ticks = Math.max(1, lifetime.toMillis() / 50);
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> sub.close(), ticks);
        }
        return sub;
    }

    private final class Sub implements EventSubscription<T>, Listener {
        private final BiConsumer<EventSubscription<T>, ? super T> handler;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicLong calls = new AtomicLong();
        private final long deadline = lifetime == null ? Long.MAX_VALUE : System.nanoTime() + lifetime.toNanos();

        Sub(BiConsumer<EventSubscription<T>, ? super T> handler) {
            this.handler = handler;
        }

        void dispatch(Event raw) {
            if (closed.get() || !type.isInstance(raw)) {
                return;
            }
            T event = type.cast(raw);
            if (System.nanoTime() > deadline) {
                close();
                return;
            }
            if (ignoreCancelled && event instanceof Cancellable c && c.isCancelled()) {
                return;
            }
            for (Predicate<? super T> test : expiryTests) {
                if (test.test(event)) {
                    close();
                    return;
                }
            }
            for (Predicate<? super T> filter : filters) {
                if (!filter.test(event)) {
                    return;
                }
            }
            long n = calls.incrementAndGet();
            if (maxCalls > 0 && n > maxCalls) {
                return; // a concurrent async event raced past the limit
            }
            try {
                handler.accept(this, event);
            } catch (Throwable t) {
                exceptionHandler.accept(event, t);
            } finally {
                if (maxCalls > 0 && n >= maxCalls) {
                    close();
                }
            }
        }

        @Override
        public Class<T> eventType() {
            return type;
        }

        @Override
        public long callCount() {
            return Math.min(calls.get(), maxCalls > 0 ? maxCalls : Long.MAX_VALUE);
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                HandlerList.unregisterAll(this);
                owner.remove(this);
            }
        }

        @Override
        public boolean isClosed() {
            return closed.get();
        }
    }
}
