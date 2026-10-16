package com.enderbyte.enderlib.database.api.exception;

/**
 * Thrown when a transaction cannot be started, committed or rolled back.
 *
 * <p>If the transaction body threw, that exception is the {@linkplain #getCause() cause}. If the
 * rollback itself also failed, the rollback failure is attached as a
 * {@linkplain #getSuppressed() suppressed} exception so neither problem is lost.
 */
public class TransactionException extends DatabaseException {

    private static final long serialVersionUID = 1L;

    public TransactionException(final String message) {
        super(message);
    }

    public TransactionException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
