package com.enderbyte.enderlib.database.sql;

import com.enderbyte.enderlib.database.api.exception.ConnectionException;
import com.enderbyte.enderlib.database.api.exception.DatabaseException;
import com.enderbyte.enderlib.database.api.exception.DatabaseTimeoutException;
import com.enderbyte.enderlib.database.api.exception.QueryException;

import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransientConnectionException;

/**
 * Translates {@link SQLException}s into the library's exception hierarchy.
 *
 * <p>The original exception is always kept as the cause, so SQLSTATE and vendor code stay available
 * both to the retry classifier and to whoever reads the stack trace.
 *
 * <p>Messages carry the statement but never the parameter values: parameters routinely hold player
 * names, IP addresses or economy amounts, and an exception message tends to end up in a public
 * paste of the server log.
 */
final class SqlExceptions {

    private SqlExceptions() {
    }

    /**
     * @param error the driver exception
     * @param sql   the statement being executed, may be {@code null}
     * @return the translated exception
     */
    static DatabaseException translate(final SQLException error, final String sql) {
        final String state = error.getSQLState();
        if (error instanceof SQLTimeoutException) {
            return new DatabaseTimeoutException("Statement exceeded its timeout", error);
        }
        if (error instanceof SQLTransientConnectionException
                || (state != null && state.startsWith("08"))) {
            return new ConnectionException("Lost or could not obtain a database connection", error);
        }
        final String description = sql == null
                ? "Statement failed"
                : "Statement failed: " + summarize(sql);
        return new QueryException(description + " [SQLSTATE " + state + ']', sql, error);
    }

    /**
     * @param error any failure raised while executing a statement
     * @param sql   the statement, may be {@code null}
     * @return a library exception, wrapping the failure when needed
     */
    static RuntimeException wrap(final Exception error, final String sql) {
        if (error instanceof SQLException sqlException) {
            return translate(sqlException, sql);
        }
        if (error instanceof RuntimeException runtime) {
            return runtime;
        }
        return new QueryException("Statement failed", sql, error);
    }

    /** Keeps log lines readable when someone executes a very long generated statement. */
    private static String summarize(final String sql) {
        final String collapsed = sql.replaceAll("\\s+", " ").trim();
        return collapsed.length() <= 200 ? collapsed : collapsed.substring(0, 200) + "...";
    }
}
