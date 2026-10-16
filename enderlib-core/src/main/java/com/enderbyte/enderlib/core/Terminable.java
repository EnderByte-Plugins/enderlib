package com.enderbyte.enderlib.core;

/**
 * Something that can be shut down: a listener, a task, a menu, a hologram.
 *
 * <p>Unlike {@link AutoCloseable}, closing never throws a checked exception, so terminables compose
 * cleanly inside a {@link CompositeTerminable} and are safe to call from {@code onDisable}.</p>
 */
@FunctionalInterface
public interface Terminable extends AutoCloseable {

    /** A terminable that does nothing. */
    Terminable EMPTY = () -> { };

    @Override
    void close();

    /** Whether {@link #close()} has already run. Implementations that cannot tell return {@code false}. */
    default boolean isClosed() {
        return false;
    }

    /** Registers this terminable with {@code owner}, so it closes when the owner closes. */
    default <T extends Terminable> T bindTo(CompositeTerminable owner) {
        owner.with(this);
        @SuppressWarnings("unchecked")
        T self = (T) this;
        return self;
    }
}
