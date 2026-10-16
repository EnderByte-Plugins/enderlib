package com.enderbyte.enderlib.database.api.util;

import java.util.Objects;

/** Small argument-checking helpers, kept internal to avoid a Guava-style dependency. */
public final class Preconditions {

    private Preconditions() {
    }

    public static <T> T requireNonNull(final T value, final String name) {
        return Objects.requireNonNull(value, name + " must not be null");
    }

    public static String requireNonBlank(final String value, final String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public static int requirePositive(final int value, final String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive but was " + value);
        }
        return value;
    }

    public static int requireNonNegative(final int value, final String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative but was " + value);
        }
        return value;
    }

    public static void checkState(final boolean condition, final String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    public static void checkArgument(final boolean condition, final String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
