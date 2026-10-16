package com.enderbyte.enderlib.database.api.transaction;

import java.time.Duration;
import java.util.Objects;

/**
 * Per-transaction settings: isolation, read-only hint and timeout.
 *
 * <p>Immutable; every method returns a copy, so a constant can be shared safely.
 */
public final class TransactionOptions {

    private static final TransactionOptions DEFAULTS =
            new TransactionOptions(IsolationLevel.DEFAULT, false, null);

    private final IsolationLevel isolationLevel;
    private final boolean readOnly;
    private final Duration timeout;

    private TransactionOptions(final IsolationLevel isolationLevel,
                               final boolean readOnly,
                               final Duration timeout) {
        this.isolationLevel = isolationLevel;
        this.readOnly = readOnly;
        this.timeout = timeout;
    }

    /** @return the engine defaults: engine isolation, read-write, library timeout */
    public static TransactionOptions defaults() {
        return DEFAULTS;
    }

    /** @return options using the given isolation level */
    public static TransactionOptions isolation(final IsolationLevel level) {
        return DEFAULTS.withIsolation(level);
    }

    /** @return options marking the transaction read-only, which lets engines optimise */
    public static TransactionOptions readOnly() {
        return DEFAULTS.withReadOnly(true);
    }

    public IsolationLevel isolationLevel() {
        return isolationLevel;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    /** @return the timeout, or {@code null} to use {@code TimeoutConfig.transaction()} */
    public Duration timeout() {
        return timeout;
    }

    public TransactionOptions withIsolation(final IsolationLevel level) {
        return new TransactionOptions(Objects.requireNonNull(level, "level"), readOnly, timeout);
    }

    public TransactionOptions withReadOnly(final boolean readOnly) {
        return new TransactionOptions(isolationLevel, readOnly, timeout);
    }

    public TransactionOptions withTimeout(final Duration timeout) {
        return new TransactionOptions(isolationLevel, readOnly, timeout);
    }

    @Override
    public String toString() {
        return "TransactionOptions{isolation=" + isolationLevel
                + ", readOnly=" + readOnly
                + ", timeout=" + timeout + '}';
    }
}
