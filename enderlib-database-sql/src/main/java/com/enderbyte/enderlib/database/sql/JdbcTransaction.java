package com.enderbyte.enderlib.database.sql;

import com.enderbyte.enderlib.database.api.exception.TransactionException;
import com.enderbyte.enderlib.database.api.mapping.ConverterRegistry;
import com.enderbyte.enderlib.database.api.query.SqlDialect;
import com.enderbyte.enderlib.database.api.transaction.IsolationLevel;
import com.enderbyte.enderlib.database.api.transaction.Transaction;
import com.enderbyte.enderlib.database.api.util.Identifiers;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link Transaction} over a borrowed JDBC connection.
 *
 * <p>Begin, commit, rollback and release are all handled by {@link HikariDatabase}; this class only
 * exposes what the callback legitimately needs. {@link #close()} is intentionally inert so a
 * misplaced try-with-resources inside a callback cannot return the connection to the pool while the
 * transaction is still open.
 */
final class JdbcTransaction extends JdbcConnection implements Transaction {

    private final Connection connection;
    private final IsolationLevel isolationLevel;
    private final Map<String, Savepoint> savepoints = new HashMap<>(2);
    private boolean rollbackOnly;

    JdbcTransaction(final Connection connection,
                    final ConverterRegistry converters,
                    final SqlDialect dialect,
                    final int queryTimeoutSeconds,
                    final IsolationLevel isolationLevel) {
        super(connection, converters, dialect, queryTimeoutSeconds, true);
        this.connection = connection;
        this.isolationLevel = isolationLevel;
    }

    @Override
    public IsolationLevel isolationLevel() {
        return isolationLevel;
    }

    @Override
    public void setRollbackOnly() {
        this.rollbackOnly = true;
    }

    @Override
    public boolean isRollbackOnly() {
        return rollbackOnly;
    }

    @Override
    public void savepoint(final String name) {
        Identifiers.requireValid(name, "savepoint");
        try {
            savepoints.put(name, connection.setSavepoint(name));
        } catch (final SQLException e) {
            throw new TransactionException("Could not create savepoint '" + name + '\'', e);
        }
    }

    @Override
    public void rollbackTo(final String name) {
        final Savepoint savepoint = requireSavepoint(name);
        try {
            connection.rollback(savepoint);
        } catch (final SQLException e) {
            throw new TransactionException("Could not roll back to savepoint '" + name + '\'', e);
        }
    }

    @Override
    public void releaseSavepoint(final String name) {
        final Savepoint savepoint = requireSavepoint(name);
        try {
            connection.releaseSavepoint(savepoint);
            savepoints.remove(name);
        } catch (final SQLException e) {
            throw new TransactionException("Could not release savepoint '" + name + '\'', e);
        }
    }

    private Savepoint requireSavepoint(final String name) {
        final Savepoint savepoint = savepoints.get(name);
        if (savepoint == null) {
            throw new TransactionException("No savepoint named '" + name + "' exists in this transaction");
        }
        return savepoint;
    }
}
