package com.enderbyte.enderlib.database.api.exception;

/**
 * Root of the library's exception hierarchy.
 *
 * <p>All exceptions thrown by this library are unchecked, because virtually every operation is
 * asynchronous and checked exceptions cannot travel through {@link java.util.concurrent.CompletableFuture}
 * without wrapping anyway. The original driver exception is always preserved as the cause.
 */
public class DatabaseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DatabaseException(final String message) {
        super(message);
    }

    public DatabaseException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public DatabaseException(final Throwable cause) {
        super(cause);
    }
}
