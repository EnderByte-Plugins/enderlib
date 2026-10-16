package com.enderbyte.enderlib.event;

import com.enderbyte.enderlib.core.Terminable;
import org.bukkit.event.Event;

/**
 * A live event handler. Closing it unregisters the handler.
 */
public interface EventSubscription<T extends Event> extends Terminable {

    Class<T> eventType();

    /** How many times the handler has run. */
    long callCount();

    /** Whether the handler is still registered. */
    default boolean isActive() {
        return !isClosed();
    }
}
