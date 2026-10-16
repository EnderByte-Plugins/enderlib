package com.enderbyte.enderlib.database.api.exception;

/**
 * Thrown when schema migration fails: a broken script, a checksum mismatch against an already
 * applied version, duplicate version numbers, or an unreachable migration lock.
 */
public class MigrationException extends DatabaseException {

    private static final long serialVersionUID = 1L;

    public MigrationException(final String message) {
        super(message);
    }

    public MigrationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
