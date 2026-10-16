package com.enderbyte.enderlib.database.api.config;

import java.time.Duration;
import java.util.Objects;

/**
 * Library-level operation timeouts.
 *
 * <p>Every asynchronous operation is bounded so that a hung database can never turn into an
 * ever-growing pile of pending futures. Individual calls can still override the default with
 * {@link java.util.concurrent.CompletableFuture#orTimeout(long, java.util.concurrent.TimeUnit)}.
 *
 * <p>{@link Duration#ZERO} disables a timeout.
 */
public final class TimeoutConfig {

    private final Duration query;
    private final Duration batch;
    private final Duration transaction;
    private final Duration connect;

    private TimeoutConfig(final Builder builder) {
        this.query = builder.query;
        this.batch = builder.batch;
        this.transaction = builder.transaction;
        this.connect = builder.connect;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static TimeoutConfig defaults() {
        return builder().build();
    }

    /**
     * @return the deadline for a single statement, applied both as a JDBC statement timeout and as
     *         a future timeout
     */
    public Duration query() {
        return query;
    }

    /** @return the deadline for a whole batch, which legitimately runs longer than one statement */
    public Duration batch() {
        return batch;
    }

    /** @return the deadline for a whole transaction body */
    public Duration transaction() {
        return transaction;
    }

    /** @return the deadline for initial connectivity validation during {@code connect()} */
    public Duration connect() {
        return connect;
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    @Override
    public String toString() {
        return "TimeoutConfig{query=" + query + ", batch=" + batch
                + ", transaction=" + transaction + ", connect=" + connect + '}';
    }

    /** Mutable builder for {@link TimeoutConfig}. Not thread-safe. */
    public static final class Builder {

        private Duration query = Duration.ofSeconds(10);
        private Duration batch = Duration.ofSeconds(60);
        private Duration transaction = Duration.ofSeconds(30);
        private Duration connect = Duration.ofSeconds(30);

        private Builder() {
        }

        private Builder(final TimeoutConfig source) {
            this.query = source.query;
            this.batch = source.batch;
            this.transaction = source.transaction;
            this.connect = source.connect;
        }

        /** Default 10s. Anything slower than this is already a production incident. */
        public Builder query(final Duration query) {
            this.query = requireNonNegative(query, "query");
            return this;
        }

        /** Default 60s, since a bulk flush of thousands of rows is expected to take longer. */
        public Builder batch(final Duration batch) {
            this.batch = requireNonNegative(batch, "batch");
            return this;
        }

        /** Default 30s. Transactions hold locks, so keep them short. */
        public Builder transaction(final Duration transaction) {
            this.transaction = requireNonNegative(transaction, "transaction");
            return this;
        }

        /** Default 30s. Applies to startup connectivity checks, not to pool checkout. */
        public Builder connect(final Duration connect) {
            this.connect = requireNonNegative(connect, "connect");
            return this;
        }

        public TimeoutConfig build() {
            return new TimeoutConfig(this);
        }

        private static Duration requireNonNegative(final Duration duration, final String name) {
            Objects.requireNonNull(duration, name);
            if (duration.isNegative()) {
                throw new IllegalArgumentException(name + " must not be negative");
            }
            return duration;
        }
    }
}
