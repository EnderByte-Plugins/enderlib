package com.enderbyte.enderlib.database.api;

import com.enderbyte.enderlib.database.api.query.SqlStatement;
import com.enderbyte.enderlib.database.api.result.QueryResult;

import java.sql.Connection;
import java.util.List;

/**
 * A single checked-out SQL connection with synchronous operations.
 *
 * <p>Everything here blocks. Instances are only ever handed to code that already runs on a database
 * worker thread — the body of {@code withConnectionAsync} or {@code transactionAsync} — so the
 * blocking is contained and the server thread is never involved.
 *
 * <p>A connection must not outlive the callback it was given to. Closing it returns it to the pool;
 * the library does that automatically, which is why there is no code path that can leak one.
 */
public interface DatabaseConnection extends AutoCloseable {

    /**
     * Runs a query.
     *
     * @param sql        statement text with {@code ?} placeholders
     * @param parameters values to bind
     * @return the materialised result
     */
    QueryResult query(String sql, Object... parameters);

    /** Runs a built query. */
    QueryResult query(SqlStatement statement);

    /**
     * Runs an update, insert or delete.
     *
     * @param sql        statement text with {@code ?} placeholders
     * @param parameters values to bind
     * @return the number of affected rows
     */
    int execute(String sql, Object... parameters);

    /** Runs a built statement. */
    int execute(SqlStatement statement);

    /**
     * Runs a statement and returns the keys the database generated.
     *
     * @param sql        insert statement
     * @param parameters values to bind
     * @return a result whose {@link QueryResult#generatedKeys()} holds the new keys
     */
    QueryResult executeReturningKeys(String sql, Object... parameters);

    /** Runs a built insert and returns the keys the database generated. */
    QueryResult executeReturningKeys(SqlStatement statement);

    /**
     * Runs one statement repeatedly as a JDBC batch.
     *
     * @param sql            statement text with {@code ?} placeholders
     * @param parameterSets  one list of values per execution
     * @return per-statement update counts
     */
    int[] batch(String sql, List<List<Object>> parameterSets);

    /**
     * @return the underlying JDBC connection, for driver-specific features the library does not
     *         model. Do not close it or change its auto-commit state.
     */
    Connection jdbc();

    /** Releases the connection back to the pool. Never throws a checked exception. */
    @Override
    void close();
}
