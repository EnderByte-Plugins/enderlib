package com.enderbyte.enderlib.database.api.exception;

/**
 * Thrown when a statement fails to execute.
 *
 * <p>The failing SQL is kept for diagnostics but parameter <em>values</em> are deliberately not
 * captured: they routinely contain player names, IP addresses or other data that must not end up
 * in a server log.
 */
public class QueryException extends DatabaseException {

    private static final long serialVersionUID = 1L;

    private final String sql;

    public QueryException(final String message, final String sql, final Throwable cause) {
        super(message, cause);
        this.sql = sql;
    }

    public QueryException(final String message, final Throwable cause) {
        this(message, null, cause);
    }

    public QueryException(final String message) {
        this(message, null, null);
    }

    /**
     * @return the statement that failed, or {@code null} if it was not recorded
     */
    public String sql() {
        return sql;
    }
}
