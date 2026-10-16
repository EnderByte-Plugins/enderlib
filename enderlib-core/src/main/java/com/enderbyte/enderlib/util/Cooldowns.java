package com.enderbyte.enderlib.util;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Per-key cooldowns.
 *
 * <pre>{@code
 * Cooldowns<UUID> heal = Cooldowns.of(Duration.ofSeconds(30));
 * if (!heal.tryUse(player.getUniqueId())) {
 *     player.sendMessage("Wait " + Durations.format(heal.remaining(player.getUniqueId())));
 *     return;
 * }
 * }</pre>
 *
 * Expired entries are removed lazily on access and in bulk by {@link #cleanup()}, so the map does not
 * grow without bound on a busy server.
 */
public final class Cooldowns<K> {

    private final Duration defaultDuration;
    private final LongSupplier clock;
    private final Map<K, Long> expiry = new ConcurrentHashMap<>();
    private int opsSinceCleanup;

    private Cooldowns(Duration defaultDuration, LongSupplier clock) {
        this.defaultDuration = Objects.requireNonNull(defaultDuration, "defaultDuration");
        this.clock = clock;
    }

    public static <K> Cooldowns<K> of(Duration duration) {
        return new Cooldowns<>(duration, System::currentTimeMillis);
    }

    /** Uses a custom millisecond clock (for tests). */
    public static <K> Cooldowns<K> of(Duration duration, LongSupplier clockMillis) {
        return new Cooldowns<>(duration, clockMillis);
    }

    /** Whether {@code key} is currently cooling down. */
    public boolean isActive(K key) {
        Long until = expiry.get(key);
        if (until == null) {
            return false;
        }
        if (until <= clock.getAsLong()) {
            expiry.remove(key, until);
            return false;
        }
        return true;
    }

    /** Starts the default cooldown if none is active. Returns {@code true} if the action may proceed. */
    public boolean tryUse(K key) {
        return tryUse(key, defaultDuration);
    }

    public boolean tryUse(K key, Duration duration) {
        long now = clock.getAsLong();
        long until = now + duration.toMillis();
        boolean[] allowed = {false};
        expiry.compute(key, (k, current) -> {
            if (current == null || current <= now) {
                allowed[0] = true;
                return until;
            }
            return current;
        });
        maybeCleanup();
        return allowed[0];
    }

    /** Starts (or restarts) a cooldown unconditionally. */
    public void put(K key, Duration duration) {
        expiry.put(key, clock.getAsLong() + duration.toMillis());
        maybeCleanup();
    }

    public void put(K key) {
        put(key, defaultDuration);
    }

    /** Remaining time, or {@link Duration#ZERO}. */
    public Duration remaining(K key) {
        Long until = expiry.get(key);
        if (until == null) {
            return Duration.ZERO;
        }
        long left = until - clock.getAsLong();
        return left <= 0 ? Duration.ZERO : Duration.ofMillis(left);
    }

    public void reset(K key) {
        expiry.remove(key);
    }

    public void resetAll() {
        expiry.clear();
    }

    /** Drops every expired entry. */
    public void cleanup() {
        long now = clock.getAsLong();
        expiry.values().removeIf(until -> until <= now);
    }

    public int size() {
        return expiry.size();
    }

    public Duration defaultDuration() {
        return defaultDuration;
    }

    private void maybeCleanup() {
        if (++opsSinceCleanup >= 512) {
            opsSinceCleanup = 0;
            cleanup();
        }
    }
}
