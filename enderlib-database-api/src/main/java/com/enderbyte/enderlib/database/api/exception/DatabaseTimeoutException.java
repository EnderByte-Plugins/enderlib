package com.enderbyte.enderlib.database.api.exception;

/**
 * Thrown when an operation exceeds its configured timeout.
 *
 * <p>Named {@code DatabaseTimeoutException} rather than {@code TimeoutException} so that it never
 * collides with {@link java.util.concurrent.TimeoutException} in consumer imports.
 */
public class DatabaseTimeoutException extends DatabaseException {

    private static final long serialVersionUID = 1L;

    public DatabaseTimeoutException(final String message) {
        super(message);
    }

    public DatabaseTimeoutException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
