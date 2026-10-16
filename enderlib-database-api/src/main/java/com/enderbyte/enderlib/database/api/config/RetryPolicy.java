package com.enderbyte.enderlib.database.api.config;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Retry behaviour for <em>transient</em> failures.
 *
 * <p>Only failures classified as recoverable are retried: connection loss, failover, deadlocks,
 * serialization conflicts and pool checkout timeouts. Constraint violations, syntax errors and
 * permission problems are permanent and are surfaced immediately — blindly retrying every
 * {@code SQLException} turns a bug into an outage.
 *
 * <p>Instances are immutable; every configuration method returns a new policy, so a policy can be
 * shared freely between databases and threads.
 *
 * <pre>{@code
 * RetryPolicy policy = RetryPolicy.exponentialBackoff()
 *         .maxAttempts(5)
 *         .initialDelay(Duration.ofMillis(100))
 *         .maxDelay(Duration.ofSeconds(5));
 * }</pre>
 */
public final class RetryPolicy {

    private static final RetryPolicy NONE = new RetryPolicy(1, Duration.ZERO, Duration.ZERO, 1.0d, 0.0d, false);

    private final int maxAttempts;
    private final Duration initialDelay;
    private final Duration maxDelay;
    private final double multiplier;
    private final double jitterFactor;
    private final boolean retryTransactions;

    private RetryPolicy(final int maxAttempts,
                        final Duration initialDelay,
                        final Duration maxDelay,
                        final double multiplier,
                        final double jitterFactor,
                        final boolean retryTransactions) {
        this.maxAttempts = maxAttempts;
        this.initialDelay = initialDelay;
        this.maxDelay = maxDelay;
        this.multiplier = multiplier;
        this.jitterFactor = jitterFactor;
        this.retryTransactions = retryTransactions;
    }

    /**
     * Exponential backoff with jitter: 3 attempts, 100ms initial delay, 2x growth capped at 2s,
     * 20% jitter.
     *
     * <p>Jitter matters at scale: without it, 50 Minecraft servers reconnecting after the same
     * database restart retry in lockstep and hammer the engine the moment it comes back.
     */
    public static RetryPolicy exponentialBackoff() {
        return new RetryPolicy(3, Duration.ofMillis(100), Duration.ofSeconds(2), 2.0d, 0.2d, false);
    }

    /** Fixed delay between attempts, no growth. */
    public static RetryPolicy fixedDelay(final Duration delay) {
        Objects.requireNonNull(delay, "delay");
        return new RetryPolicy(3, delay, delay, 1.0d, 0.0d, false);
    }

    /** Disables retrying entirely. */
    public static RetryPolicy none() {
        return NONE;
    }

    /** @return total attempts including the first one; {@code 1} means "no retry" */
    public int maxAttempts() {
        return maxAttempts;
    }

    public Duration initialDelay() {
        return initialDelay;
    }

    public Duration maxDelay() {
        return maxDelay;
    }

    public double multiplier() {
        return multiplier;
    }

    public double jitterFactor() {
        return jitterFactor;
    }

    /** @return whether whole transaction bodies may be replayed */
    public boolean retryTransactions() {
        return retryTransactions;
    }

    public boolean isEnabled() {
        return maxAttempts > 1;
    }

    /**
     * @param maxAttempts total attempts including the first, must be {@code >= 1}
     * @return a copy with the new attempt count
     */
    public RetryPolicy maxAttempts(final int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        return new RetryPolicy(maxAttempts, initialDelay, maxDelay, multiplier, jitterFactor, retryTransactions);
    }

    /** @return a copy with the given delay before the second attempt */
    public RetryPolicy initialDelay(final Duration initialDelay) {
        return new RetryPolicy(maxAttempts, requireNonNegative(initialDelay, "initialDelay"),
                maxDelay, multiplier, jitterFactor, retryTransactions);
    }

    /** @return a copy with the given upper bound on any single delay */
    public RetryPolicy maxDelay(final Duration maxDelay) {
        return new RetryPolicy(maxAttempts, initialDelay, requireNonNegative(maxDelay, "maxDelay"),
                multiplier, jitterFactor, retryTransactions);
    }

    /** @return a copy with the given backoff growth factor, must be {@code >= 1.0} */
    public RetryPolicy multiplier(final double multiplier) {
        if (multiplier < 1.0d) {
            throw new IllegalArgumentException("multiplier must be >= 1.0");
        }
        return new RetryPolicy(maxAttempts, initialDelay, maxDelay, multiplier, jitterFactor, retryTransactions);
    }

    /**
     * @param jitterFactor fraction of the computed delay to randomise, {@code 0.0} to {@code 1.0}
     * @return a copy with the given jitter
     */
    public RetryPolicy jitterFactor(final double jitterFactor) {
        if (jitterFactor < 0.0d || jitterFactor > 1.0d) {
            throw new IllegalArgumentException("jitterFactor must be within [0.0, 1.0]");
        }
        return new RetryPolicy(maxAttempts, initialDelay, maxDelay, multiplier, jitterFactor, retryTransactions);
    }

    /**
     * Enables replaying a whole transaction body after a transient failure.
     *
     * <p>Off by default and genuinely dangerous to enable blindly: the callback may have already
     * mutated in-memory plugin state, published events or charged an economy account. Turn it on
     * only for callbacks that are safe to run twice — which is also the only way to survive
     * PostgreSQL {@code SERIALIZABLE} conflicts (SQLSTATE {@code 40001}) automatically.
     *
     * @return a copy with transaction replay enabled or disabled
     */
    public RetryPolicy retryTransactions(final boolean retryTransactions) {
        return new RetryPolicy(maxAttempts, initialDelay, maxDelay, multiplier, jitterFactor, retryTransactions);
    }

    /**
     * Computes the delay before the given attempt.
     *
     * @param attempt 1-based number of the attempt that just failed
     * @return the delay to wait before the next attempt, never negative
     */
    public Duration delayForAttempt(final int attempt) {
        if (attempt < 1 || initialDelay.isZero()) {
            return Duration.ZERO;
        }
        double millis = initialDelay.toMillis() * Math.pow(multiplier, attempt - 1.0d);
        final long capped = maxDelay.isZero()
                ? (long) millis
                : Math.min((long) millis, maxDelay.toMillis());
        if (jitterFactor <= 0.0d) {
            return Duration.ofMillis(capped);
        }
        final long jitterRange = (long) (capped * jitterFactor);
        if (jitterRange <= 0L) {
            return Duration.ofMillis(capped);
        }
        // Full-window jitter around the computed delay, clamped at zero.
        final long offset = ThreadLocalRandom.current().nextLong(-jitterRange, jitterRange + 1L);
        return Duration.ofMillis(Math.max(0L, capped + offset));
    }

    @Override
    public String toString() {
        return "RetryPolicy{maxAttempts=" + maxAttempts
                + ", initialDelay=" + initialDelay
                + ", maxDelay=" + maxDelay
                + ", multiplier=" + multiplier
                + ", jitterFactor=" + jitterFactor
                + ", retryTransactions=" + retryTransactions + '}';
    }

    private static Duration requireNonNegative(final Duration duration, final String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return duration;
    }
}
