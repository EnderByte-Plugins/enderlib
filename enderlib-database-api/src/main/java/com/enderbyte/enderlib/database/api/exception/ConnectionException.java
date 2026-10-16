package com.enderbyte.enderlib.database.api.exception;

/**
 * Thrown when a connection cannot be established, acquired from the pool, or has been lost.
 *
 * <p>Failures of this kind are frequently transient (database restart, failover, brief network
 * partition) and are therefore retry candidates by default.
 */
public class ConnectionException extends DatabaseException {

    private static final long serialVersionUID = 1L;

    public ConnectionException(final String message) {
        super(message);
    }

    public ConnectionException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
