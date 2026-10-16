package com.enderbyte.enderlib.database.sql;

import com.enderbyte.enderlib.database.api.DatabaseConnection;
import com.enderbyte.enderlib.database.api.mapping.ConverterRegistry;
import com.enderbyte.enderlib.database.api.query.SqlDialect;
import com.enderbyte.enderlib.database.api.query.SqlQuery;
import com.enderbyte.enderlib.database.api.query.SqlStatement;
import com.enderbyte.enderlib.database.api.result.QueryResult;
import com.enderbyte.enderlib.database.core.result.MaterializedQueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * A checked-out JDBC connection with the library's synchronous operations.
 *
 * <p>Statements and result sets are always closed with try-with-resources, and the connection is
 * released the moment the enclosing callback returns. That is why the library has no connection
 * leak path of its own: no method here hands a live JDBC resource to user code except
 * {@link #jdbc()}, which is documented as borrowed.
 */
class JdbcConnection implements DatabaseConnection {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdbcConnection.class);

    private final Connection connection;
    private final ConverterRegistry converters;
    private final SqlDialect dialect;
    private final int queryTimeoutSeconds;
    private final boolean managed;
    private boolean closed;

    /**
     * @param connection          the pooled connection
     * @param converters          value converter registry
     * @param dialect             engine dialect, used to compile statements
     * @param queryTimeoutSeconds JDBC statement timeout, {@code 0} to disable
     * @param managed             {@code true} when a transaction owns the connection's lifecycle
     */
    JdbcConnection(final Connection connection,
                   final ConverterRegistry converters,
                   final SqlDialect dialect,
                   final int queryTimeoutSeconds,
                   final boolean managed) {
        this.connection = connection;
        this.converters = converters;
        this.dialect = dialect;
        this.queryTimeoutSeconds = queryTimeoutSeconds;
        this.managed = managed;
    }

    @Override
    public QueryResult query(final String sql, final Object... parameters) {
        return query(SqlQuery.of(sql, parameters));
    }

    @Override
    public QueryResult query(final SqlStatement statement) {
        final SqlQuery compiled = statement.compile(dialect);
        try (PreparedStatement prepared = prepare(compiled.sql())) {
            ParameterBinder.bind(prepared, compiled.parameters(), converters);
            try (ResultSet resultSet = prepared.executeQuery()) {
                return ResultSetReader.read(resultSet, converters);
            }
        } catch (final SQLException e) {
            throw SqlExceptions.translate(e, compiled.sql());
        }
    }

    @Override
    public int execute(final String sql, final Object... parameters) {
        return execute(SqlQuery.of(sql, parameters));
    }

    @Override
    public int execute(final SqlStatement statement) {
        final SqlQuery compiled = statement.compile(dialect);
        try (PreparedStatement prepared = prepare(compiled.sql())) {
            ParameterBinder.bind(prepared, compiled.parameters(), converters);
            return prepared.executeUpdate();
        } catch (final SQLException e) {
            throw SqlExceptions.translate(e, compiled.sql());
        }
    }

    @Override
    public QueryResult executeReturningKeys(final String sql, final Object... parameters) {
        return executeReturningKeys(SqlQuery.of(sql, parameters));
    }

    @Override
    public QueryResult executeReturningKeys(final SqlStatement statement) {
        final SqlQuery compiled = statement.compile(dialect);
        try (PreparedStatement prepared =
                     connection.prepareStatement(compiled.sql(), Statement.RETURN_GENERATED_KEYS)) {
            applyTimeout(prepared);
            ParameterBinder.bind(prepared, compiled.parameters(), converters);
            prepared.executeUpdate();
            try (ResultSet keys = prepared.getGeneratedKeys()) {
                return new MaterializedQueryResult(
                        List.of(), ResultSetReader.readGeneratedKeys(keys, converters), converters);
            }
        } catch (final SQLException e) {
            throw SqlExceptions.translate(e, compiled.sql());
        }
    }

    @Override
    public int[] batch(final String sql, final List<List<Object>> parameterSets) {
        if (parameterSets.isEmpty()) {
            return new int[0];
        }
        boolean restoreAutoCommit = false;
        try {
            // One transaction around the whole batch: without it every statement commits
            // separately and the round-trip saving is lost.
            if (!managed && connection.getAutoCommit()) {
                connection.setAutoCommit(false);
                restoreAutoCommit = true;
            }
            final int[] counts;
            try (PreparedStatement prepared = prepare(sql)) {
                for (final List<Object> parameters : parameterSets) {
                    ParameterBinder.bind(prepared, parameters, converters);
                    prepared.addBatch();
                }
                counts = prepared.executeBatch();
            }
            if (restoreAutoCommit) {
                connection.commit();
            }
            return counts;
        } catch (final SQLException e) {
            if (restoreAutoCommit) {
                rollbackQuietly();
            }
            throw SqlExceptions.translate(e, sql);
        } finally {
            if (restoreAutoCommit) {
                try {
                    connection.setAutoCommit(true);
                } catch (final SQLException e) {
                    LOGGER.warn("Could not restore auto-commit after a batch", e);
                }
            }
        }
    }

    @Override
    public Connection jdbc() {
        return connection;
    }

    @Override
    public void close() {
        if (managed || closed) {
            return;
        }
        closed = true;
        try {
            connection.close();
        } catch (final SQLException e) {
            LOGGER.warn("Failed to return a connection to the pool", e);
        }
    }

    /** @return the converter registry, for subclasses */
    final ConverterRegistry converters() {
        return converters;
    }

    /** @return the dialect, for subclasses */
    final SqlDialect dialect() {
        return dialect;
    }

    private PreparedStatement prepare(final String sql) throws SQLException {
        final PreparedStatement prepared = connection.prepareStatement(sql);
        applyTimeout(prepared);
        return prepared;
    }

    private void applyTimeout(final Statement statement) throws SQLException {
        if (queryTimeoutSeconds > 0) {
            statement.setQueryTimeout(queryTimeoutSeconds);
        }
    }

    private void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (final SQLException e) {
            LOGGER.warn("Rollback after a failed batch also failed", e);
        }
    }
}
