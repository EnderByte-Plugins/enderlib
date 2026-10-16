package com.enderbyte.enderlib.database.core.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Produces predictably named threads such as {@code EnderDB-players-SQL-1}.
 *
 * <p>Thread names are the cheapest debugging tool there is: when a server owner posts a thread dump
 * or a timings report, the name alone identifies the plugin, the database and the subsystem.
 *
 * <p>Threads carry an uncaught-exception handler so a failure inside a worker is logged rather than
 * dying silently on {@code System.err}.
 */
public final class NamedThreadFactory implements ThreadFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(NamedThreadFactory.class);

    private final String prefix;
    private final boolean daemon;
    private final AtomicInteger counter = new AtomicInteger();

    /**
     * @param prefix name prefix, without the trailing index
     * @param daemon whether workers should be daemon threads
     */
    public NamedThreadFactory(final String prefix, final boolean daemon) {
        this.prefix = prefix;
        this.daemon = daemon;
    }

    @Override
    public Thread newThread(final Runnable runnable) {
        final Thread thread = new Thread(runnable, prefix + '-' + counter.incrementAndGet());
        thread.setDaemon(daemon);
        // Normal priority: database work must never compete with the server tick loop.
        thread.setPriority(Thread.NORM_PRIORITY);
        thread.setUncaughtExceptionHandler((t, error) ->
                LOGGER.error("Uncaught exception on database thread {}", t.getName(), error));
        return thread;
    }
}
