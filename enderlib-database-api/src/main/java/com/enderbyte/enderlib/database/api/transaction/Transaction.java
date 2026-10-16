package com.enderbyte.enderlib.database.api.transaction;

import com.enderbyte.enderlib.database.api.DatabaseConnection;

/**
 * A transaction handed to a {@link TransactionCallback}.
 *
 * <p>The surrounding machinery begins the transaction, commits it when the callback returns
 * normally, rolls it back when the callback throws, and always releases the connection. The
 * callback therefore never needs {@code commit()} or {@code rollback()} — only
 * {@link #setRollbackOnly()} when it wants to abort without throwing.
 *
 * <pre>{@code
 * database.transactionAsync(tx -> {
 *     tx.execute("UPDATE accounts SET balance = balance - ? WHERE uuid = ?", amount, from);
 *     tx.execute("UPDATE accounts SET balance = balance + ? WHERE uuid = ?", amount, to);
 * });
 * }</pre>
 *
 * <p>Keep transactions short. They hold row locks, and on a shared network database a long
 * transaction on one server stalls every other server touching the same rows.
 */
public interface Transaction extends DatabaseConnection {

    /** @return the isolation level actually in effect */
    IsolationLevel isolationLevel();

    /**
     * Marks the transaction for rollback. The callback can keep running and return a value; the
     * transaction is rolled back rather than committed when it finishes.
     */
    void setRollbackOnly();

    /** @return {@code true} if {@link #setRollbackOnly()} was called */
    boolean isRollbackOnly();

    /**
     * Creates a savepoint that {@link #rollbackTo(String)} can return to, allowing part of a
     * transaction to fail without discarding the rest.
     *
     * @param name savepoint name, validated as an identifier
     */
    void savepoint(String name);

    /**
     * Rolls back to a previously created savepoint.
     *
     * @param name savepoint name
     */
    void rollbackTo(String name);

    /**
     * Releases a savepoint, freeing the resources the engine holds for it.
     *
     * @param name savepoint name
     */
    void releaseSavepoint(String name);
}
