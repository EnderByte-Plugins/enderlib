package com.enderbyte.enderlib.database.core.retry;

import com.enderbyte.enderlib.database.api.exception.ConfigurationException;
import com.enderbyte.enderlib.database.api.exception.ConnectionException;
import com.enderbyte.enderlib.database.api.exception.MappingException;
import com.enderbyte.enderlib.database.api.exception.OptimisticLockException;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLSyntaxErrorException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransientException;
import java.util.Set;

/**
 * Classifies JDBC failures as transient or permanent.
 *
 * <p>The decision is made on SQLSTATE class first, because SQLSTATE is standardised across engines,
 * and only then on vendor error codes for the few cases the standard does not cover.
 *
 * <p>Retried:
 * <ul>
 *   <li>{@code 08} connection exception — the database restarted, failed over, or the network blipped</li>
 *   <li>{@code 40} transaction rollback — deadlock or serialization conflict, the textbook retry case</li>
 *   <li>{@code 53} insufficient resources, {@code 57} operator intervention (PostgreSQL shutdown)</li>
 *   <li>Pool checkout timeouts, socket errors, MySQL "server has gone away"</li>
 * </ul>
 *
 * <p>Never retried: constraint violations, syntax errors, permission problems, mapping errors,
 * configuration errors and optimistic lock conflicts — the last because retrying without re-reading
 * the row would simply overwrite the other server's change.
 */
public final class SqlFailureClassifier implements FailureClassifier {

    /** Shared instance; the classifier is stateless. */
    public static final SqlFailureClassifier INSTANCE = new SqlFailureClassifier();

    private static final Set<String> TRANSIENT_SQL_STATE_CLASSES = Set.of("08", "40", "53", "57", "JZ");

    /** Vendor codes that mean "connection or lock problem" but do not carry a helpful SQLSTATE. */
    private static final Set<Integer> TRANSIENT_VENDOR_CODES = Set.of(
            1040,  // MySQL: too many connections
            1042,  // MySQL: can't get hostname
            1205,  // MySQL/MariaDB: lock wait timeout exceeded
            1213,  // MySQL/MariaDB: deadlock found
            2002,  // MySQL: can't connect to server
            2003,  // MySQL: can't connect to server
            2006,  // MySQL: server has gone away
            2013,  // MySQL: lost connection during query
            5,     // SQLite: SQLITE_BUSY (database is locked)
            6);    // SQLite: SQLITE_LOCKED

    private SqlFailureClassifier() {
    }

    @Override
    public boolean isTransient(final Throwable failure) {
        Throwable current = failure;
        // Walk the cause chain: drivers and pools wrap the interesting exception several times.
        int depth = 0;
        while (current != null && depth++ < 10) {
            final Boolean verdict = classify(current);
            if (verdict != null) {
                return verdict;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * @return {@code TRUE}/{@code FALSE} when this exception decides the outcome, {@code null} when
     *         the decision should move to the cause
     */
    private Boolean classify(final Throwable error) {
        if (error instanceof OptimisticLockException
                || error instanceof ConfigurationException
                || error instanceof MappingException) {
            return Boolean.FALSE;
        }
        if (error instanceof SQLIntegrityConstraintViolationException
                || error instanceof SQLSyntaxErrorException) {
            return Boolean.FALSE;
        }
        if (error instanceof ConnectionException) {
            return Boolean.TRUE;
        }
        if (error instanceof SQLTransientException
                || error instanceof SQLRecoverableException
                || error instanceof SQLTimeoutException
                || error instanceof SQLNonTransientConnectionException) {
            return Boolean.TRUE;
        }
        if (error instanceof SQLException sqlException) {
            final String sqlState = sqlException.getSQLState();
            if (sqlState != null && sqlState.length() >= 2
                    && TRANSIENT_SQL_STATE_CLASSES.contains(sqlState.substring(0, 2))) {
                return Boolean.TRUE;
            }
            if (TRANSIENT_VENDOR_CODES.contains(sqlException.getErrorCode())) {
                return Boolean.TRUE;
            }
            return null;
        }
        if (error instanceof IOException) {
            // Socket read/write failures against a database are always worth one more attempt.
            return Boolean.TRUE;
        }
        return null;
    }
}
