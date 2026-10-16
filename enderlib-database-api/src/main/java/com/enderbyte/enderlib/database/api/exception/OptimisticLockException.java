package com.enderbyte.enderlib.database.api.exception;

/**
 * Thrown when a versioned update affected no rows, meaning another server (or another thread)
 * modified the same record first.
 *
 * <p>This is the expected failure mode when several Minecraft servers share one database and both
 * try to write the same player. Callers should re-read the entity and re-apply their change rather
 * than blindly retrying the same update.
 */
public class OptimisticLockException extends DatabaseException {

    private static final long serialVersionUID = 1L;

    public OptimisticLockException(final String message) {
        super(message);
    }
}
