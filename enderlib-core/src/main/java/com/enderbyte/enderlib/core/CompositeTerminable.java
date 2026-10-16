package com.enderbyte.enderlib.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * A group of {@link Terminable}s closed together, newest first.
 *
 * <p>Every {@link EnderLib} instance owns one; anything registered with it is torn down by
 * {@link EnderLib#shutdown()}. A failure in one child is logged and never stops the others.</p>
 */
public final class CompositeTerminable implements Terminable {

    private static final Logger LOG = LoggerFactory.getLogger(CompositeTerminable.class);

    private static final int PRUNE_EVERY = 64;

    private final Deque<AutoCloseable> children = new ArrayDeque<>();
    private boolean closed;
    private int additions;

    public static CompositeTerminable create() {
        return new CompositeTerminable();
    }

    /**
     * Adds a child. If this composite is already closed the child is closed immediately, so late
     * registrations during shutdown never leak.
     */
    public synchronized <T extends AutoCloseable> T with(T child) {
        Objects.requireNonNull(child, "child");
        if (closed) {
            closeQuietly(child);
        } else {
            if (++additions % PRUNE_EVERY == 0) {
                // Long-lived owners collect many short-lived tasks; drop the ones already finished
                children.removeIf(c -> c instanceof Terminable t && t.isClosed());
            }
            children.push(child);
        }
        return child;
    }

    /** Removes a child without closing it (for children that closed themselves). */
    public synchronized void remove(AutoCloseable child) {
        children.remove(child);
    }

    /** Closes every child but keeps this composite open for reuse. */
    public void cleanup() {
        Deque<AutoCloseable> snapshot;
        synchronized (this) {
            snapshot = new ArrayDeque<>(children);
            children.clear();
        }
        snapshot.forEach(CompositeTerminable::closeQuietly);
    }

    @Override
    public void close() {
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
        }
        cleanup();
    }

    @Override
    public synchronized boolean isClosed() {
        return closed;
    }

    private static void closeQuietly(AutoCloseable c) {
        try {
            c.close();
        } catch (Exception e) {
            LOG.warn("Failed to close {}", c, e);
        }
    }
}
