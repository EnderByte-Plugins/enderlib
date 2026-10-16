package com.enderbyte.enderlib.database.api.transaction;

import java.sql.Connection;

/**
 * Transaction isolation levels.
 *
 * <p>Support differs by engine: SQLite behaves as {@code SERIALIZABLE} whatever is requested, MySQL
 * and MariaDB default to {@code REPEATABLE READ}, PostgreSQL to {@code READ COMMITTED}. Requesting
 * a level the engine does not implement is silently upgraded by the driver, never downgraded.
 */
public enum IsolationLevel {

    /** Leaves the connection's level untouched. */
    DEFAULT(-1),
    READ_UNCOMMITTED(Connection.TRANSACTION_READ_UNCOMMITTED),
    READ_COMMITTED(Connection.TRANSACTION_READ_COMMITTED),
    REPEATABLE_READ(Connection.TRANSACTION_REPEATABLE_READ),
    /**
     * Strictest level. On PostgreSQL this can fail with a serialization conflict (SQLSTATE 40001),
     * which is a normal, retryable outcome rather than a bug.
     */
    SERIALIZABLE(Connection.TRANSACTION_SERIALIZABLE);

    private final int jdbcLevel;

    IsolationLevel(final int jdbcLevel) {
        this.jdbcLevel = jdbcLevel;
    }

    /** @return the {@link Connection} constant, or {@code -1} for {@link #DEFAULT} */
    public int jdbcLevel() {
        return jdbcLevel;
    }
}
