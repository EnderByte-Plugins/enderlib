package com.enderbyte.enderlib.database.sql;

import com.enderbyte.enderlib.database.api.Database;
import com.enderbyte.enderlib.database.api.DatabaseConnection;
import com.enderbyte.enderlib.database.api.config.DatabaseConfig;
import com.enderbyte.enderlib.database.api.config.DatabaseType;
import com.enderbyte.enderlib.database.api.exception.ConfigurationException;
import com.enderbyte.enderlib.database.api.exception.ConnectionException;
import com.enderbyte.enderlib.database.api.metrics.PoolStatistics;
import com.enderbyte.enderlib.database.api.migration.MigrationService;
import com.enderbyte.enderlib.database.api.migration.MigrationServiceFactory;
import com.enderbyte.enderlib.database.api.query.DeleteQuery;
import com.enderbyte.enderlib.database.api.query.InsertQuery;
import com.enderbyte.enderlib.database.api.query.Query;
import com.enderbyte.enderlib.database.api.query.SqlDialect;
import com.enderbyte.enderlib.database.api.query.SqlQuery;
import com.enderbyte.enderlib.database.api.query.SqlStatement;
import com.enderbyte.enderlib.database.api.query.UpdateQuery;
import com.enderbyte.enderlib.database.api.repository.CrudRepository;
import com.enderbyte.enderlib.database.api.result.QueryResult;
import com.enderbyte.enderlib.database.api.result.RowMapper;
import com.enderbyte.enderlib.database.api.transaction.IsolationLevel;
import com.enderbyte.enderlib.database.api.transaction.TransactionCallback;
import com.enderbyte.enderlib.database.api.transaction.TransactionOptions;
import com.enderbyte.enderlib.database.api.transaction.TransactionWork;
import com.enderbyte.enderlib.database.core.AbstractDatabase;
import com.enderbyte.enderlib.database.core.mapping.DefaultConverterRegistry;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The JDBC implementation of {@link Database}, backed by a HikariCP pool.
 *
 * <p>Every public method follows the same shape: validate, submit to the database executor, retry
 * transient failures, apply a deadline, record metrics. The blocking JDBC work itself lives in
 * {@link JdbcConnection}, which is deliberately synchronous — mixing asynchrony into the layer that
 * talks to the driver is how connection leaks are born.
 */
final class HikariDatabase extends AbstractDatabase {

    private static final Logger LOGGER = LoggerFactory.getLogger(HikariDatabase.class);

    private final SqlDialect dialect;
    private final int queryTimeoutSeconds;
    private final Map<Class<?>, CrudRepository<?, ?>> repositories = new ConcurrentHashMap<>();

    private volatile HikariDataSource dataSource;
    private volatile MigrationService migrationService;

    HikariDatabase(final DatabaseConfig config) {
        super(config, "SQL", new DefaultConverterRegistry(
                SqlDialect.forType(config.type()).hasNativeUuidType(),
                config.type() == DatabaseType.SQLITE));
        this.dialect = SqlDialect.forType(config.type());
        // JDBC takes whole seconds. Round up so a sub-second configured timeout still bounds the
        // statement instead of silently becoming "no timeout".
        final long millis = config.timeouts().query().toMillis();
        this.queryTimeoutSeconds = millis <= 0 ? 0 : (int) Math.max(1L, (millis + 999L) / 1000L);
    }

    // ------------------------------------------------------------------------------ lifecycle

    @Override
    protected void doConnect() {
        dataSource = HikariPoolFactory.create(config());
    }

    @Override
    protected void doClose() {
        final HikariDataSource current = dataSource;
        if (current != null) {
            dataSource = null;
            current.close();
        }
        repositories.clear();
    }

    @Override
    protected void doPing() throws SQLException {
        try (Connection connection = requireDataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.setQueryTimeout((int) Math.max(1L, config().timeouts().connect().toSeconds()));
            statement.execute(dialect.validationQuery());
        }
    }

    @Override
    protected PoolStatistics poolStatistics() {
        final HikariDataSource current = dataSource;
        if (current == null || !current.isRunning()) {
            return PoolStatistics.UNAVAILABLE;
        }
        final HikariPoolMXBean pool = current.getHikariPoolMXBean();
        if (pool == null) {
            return PoolStatistics.UNAVAILABLE;
        }
        return new PoolStatistics(
                pool.getTotalConnections(),
                pool.getActiveConnections(),
                pool.getIdleConnections(),
                pool.getThreadsAwaitingConnection(),
                current.getMaximumPoolSize());
    }

    @Override
    public SqlDialect dialect() {
        return dialect;
    }

    // --------------------------------------------------------------------------------- queries

    @Override
    public CompletableFuture<QueryResult> queryAsync(final String sql, final Object... parameters) {
        return queryAsync(SqlQuery.of(sql, parameters));
    }

    @Override
    public CompletableFuture<QueryResult> queryAsync(final SqlStatement statement) {
        final SqlQuery compiled = compile(statement);
        return run("query", config().timeouts().query(),
                connection -> connection.query(compiled));
    }

    @Override
    public <T> CompletableFuture<List<T>> queryAsync(final SqlStatement statement, final Class<T> type) {
        final SqlQuery compiled = compile(statement);
        return run("query", config().timeouts().query(),
                connection -> connection.query(compiled).map(type));
    }

    @Override
    public <T> CompletableFuture<List<T>> queryAsync(final SqlStatement statement, final RowMapper<T> mapper) {
        final SqlQuery compiled = compile(statement);
        return run("query", config().timeouts().query(),
                connection -> connection.query(compiled).map(mapper));
    }

    @Override
    public <T> CompletableFuture<Optional<T>> queryFirstAsync(final SqlStatement statement, final Class<T> type) {
        final SqlQuery compiled = compile(statement);
        return run("query", config().timeouts().query(),
                connection -> connection.query(compiled).first(type));
    }

    // --------------------------------------------------------------------------------- updates

    @Override
    public CompletableFuture<Integer> executeAsync(final String sql, final Object... parameters) {
        return executeAsync(SqlQuery.of(sql, parameters));
    }

    @Override
    public CompletableFuture<Integer> executeAsync(final SqlStatement statement) {
        final SqlQuery compiled = compile(statement);
        return run("execute", config().timeouts().query(),
                connection -> connection.execute(compiled));
    }

    @Override
    public CompletableFuture<Integer> insertAsync(final InsertQuery query) {
        return executeAsync(query);
    }

    @Override
    public CompletableFuture<QueryResult> insertReturningKeysAsync(final InsertQuery query) {
        final SqlQuery compiled = compile(query);
        return run("insert", config().timeouts().query(),
                connection -> connection.executeReturningKeys(compiled));
    }

    @Override
    public CompletableFuture<Integer> updateAsync(final UpdateQuery query) {
        return executeAsync(query);
    }

    @Override
    public CompletableFuture<Integer> deleteAsync(final DeleteQuery query) {
        return executeAsync(query);
    }

    // --------------------------------------------------------------------------------- batches

    @Override
    public CompletableFuture<int[]> batchAsync(final String sql, final List<List<Object>> parameterSets) {
        final List<List<Object>> copy = List.copyOf(parameterSets);
        return run("batch", config().timeouts().batch(),
                connection -> connection.batch(sql, copy));
    }

    @Override
    public CompletableFuture<int[]> batchInsertAsync(final String table,
                                                     final List<String> columns,
                                                     final List<List<Object>> rows) {
        if (rows.isEmpty()) {
            return CompletableFuture.completedFuture(new int[0]);
        }
        // Compile one placeholder row through the builder so identifiers are validated and quoted
        // exactly as they would be for a normal insert.
        final InsertQuery template = Query.insertInto(table)
                .columns(columns.toArray(new String[0]))
                .row(new Object[columns.size()]);
        final String sql = template.compile(dialect).sql();
        return batchAsync(sql, rows);
    }

    @Override
    public CompletableFuture<int[]> batchUpdateAsync(final String sql, final List<List<Object>> parameterSets) {
        return batchAsync(sql, parameterSets);
    }

    // ---------------------------------------------------------------------------- transactions

    @Override
    public <T> CompletableFuture<T> transactionAsync(final TransactionCallback<T> callback) {
        return transactionAsync(TransactionOptions.defaults(), callback);
    }

    @Override
    public CompletableFuture<Void> runTransactionAsync(final TransactionWork work) {
        return runTransactionAsync(TransactionOptions.defaults(), work);
    }

    @Override
    public CompletableFuture<Void> runTransactionAsync(final TransactionOptions options,
                                                       final TransactionWork work) {
        return transactionAsync(options, transaction -> {
            work.accept(transaction);
            return null;
        });
    }

    @Override
    public <T> CompletableFuture<T> transactionAsync(final TransactionOptions options,
                                                     final TransactionCallback<T> callback) {
        final Duration timeout = options.timeout() != null
                ? options.timeout()
                : config().timeouts().transaction();
        // Replaying a transaction re-runs user code, so it only happens when the policy opts in.
        final int attempts = config().retryPolicy().retryTransactions()
                ? config().retryPolicy().maxAttempts()
                : 1;
        return submit("transaction", timeout, attempts, () -> runTransaction(options, callback));
    }

    @Override
    public <T> CompletableFuture<T> withConnectionAsync(final Function<DatabaseConnection, T> work) {
        return run("connection", config().timeouts().query(), work::apply);
    }

    private <T> T runTransaction(final TransactionOptions options,
                                 final TransactionCallback<T> callback) throws Exception {
        final long startedAt = System.nanoTime();
        final Connection connection = borrow();
        final boolean previousAutoCommit = connection.getAutoCommit();
        final boolean previousReadOnly = connection.isReadOnly();
        final int previousIsolation = connection.getTransactionIsolation();
        boolean committed = false;
        boolean settled = false;
        try {
            connection.setAutoCommit(false);
            if (options.isReadOnly()) {
                connection.setReadOnly(true);
            }
            if (options.isolationLevel() != IsolationLevel.DEFAULT) {
                connection.setTransactionIsolation(options.isolationLevel().jdbcLevel());
            }

            final JdbcTransaction transaction = new JdbcTransaction(
                    connection, converters(), dialect, queryTimeoutSeconds, options.isolationLevel());
            final T result = callback.apply(transaction);

            if (transaction.isRollbackOnly()) {
                connection.rollback();
            } else {
                connection.commit();
                committed = true;
            }
            settled = true;
            metrics().recordTransaction(committed, System.nanoTime() - startedAt);
            return result;
        } catch (final Exception error) {
            rollbackQuietly(connection);
            settled = true;
            metrics().recordTransaction(false, System.nanoTime() - startedAt);
            throw error instanceof SQLException sqlError
                    ? SqlExceptions.translate(sqlError, "transaction")
                    : error;
        } finally {
            // An Error skips the catch block, and restoring auto-commit below commits the
            // half-finished transaction on several drivers. Roll it back first.
            if (!settled) {
                rollbackQuietly(connection);
            }
            restore(connection, previousAutoCommit, previousReadOnly, previousIsolation, committed);
            close(connection);
        }
    }

    private void restore(final Connection connection,
                         final boolean autoCommit,
                         final boolean readOnly,
                         final int isolation,
                         final boolean committed) {
        // The connection goes back into a shared pool, so every setting the transaction changed has
        // to be undone. Hikari resets some of these itself, but not on every version or driver.
        try {
            if (connection.getAutoCommit() != autoCommit) {
                connection.setAutoCommit(autoCommit);
            }
            if (connection.isReadOnly() != readOnly) {
                connection.setReadOnly(readOnly);
            }
            if (connection.getTransactionIsolation() != isolation) {
                connection.setTransactionIsolation(isolation);
            }
        } catch (final SQLException e) {
            LOGGER.warn("Could not restore connection state after a {} transaction on '{}';"
                            + " the connection will be evicted from the pool",
                    committed ? "committed" : "rolled back", name(), e);
            evict(connection);
        }
    }

    // ------------------------------------------------------------------------------ subsystems

    @Override
    public MigrationService migrations() {
        MigrationService service = migrationService;
        if (service == null) {
            synchronized (this) {
                service = migrationService;
                if (service == null) {
                    service = loadMigrationService();
                    migrationService = service;
                }
            }
        }
        return service;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T, ID> CrudRepository<T, ID> repository(final Class<T> entityType, final Class<ID> idType) {
        return (CrudRepository<T, ID>) repositories.computeIfAbsent(
                entityType, type -> new SqlRepository<>(this, (Class<T>) type, idType));
    }

    private MigrationService loadMigrationService() {
        final ServiceLoader<MigrationServiceFactory> loader = ServiceLoader.load(
                MigrationServiceFactory.class, MigrationServiceFactory.class.getClassLoader());
        for (final MigrationServiceFactory factory : loader) {
            return factory.create(this);
        }
        throw new ConfigurationException("No migration service is available."
                + " Add the enderlib-database-migrations module to use database.migrations().");
    }

    // ----------------------------------------------------------------------------- plumbing

    /**
     * Runs blocking work against a borrowed connection, with retries, a deadline and metrics.
     *
     * <p>This is the single place where connections are checked out for non-transactional work,
     * which is what makes "the connection is always returned" a property of the design rather than
     * of every call site remembering to do it.
     */
    private <T> CompletableFuture<T> run(final String operation,
                                         final Duration timeout,
                                         final ConnectionWork<T> work) {
        return submit(operation, timeout, config().retryPolicy().maxAttempts(), () -> {
            final Connection connection = borrow();
            try (JdbcConnection wrapper = new JdbcConnection(
                    connection, converters(), dialect, queryTimeoutSeconds, false)) {
                return work.apply(wrapper);
            }
        });
    }

    private <T> CompletableFuture<T> submit(final String operation,
                                            final Duration timeout,
                                            final int attempts,
                                            final Callable<T> task) {
        try {
            requireConnected();
        } catch (final RuntimeException e) {
            // Configuration and lifecycle mistakes come back as a failed future like everything
            // else, so a caller never has to both catch and handle a failed future.
            return CompletableFuture.failedFuture(e);
        }
        metrics().operationStarted();
        final long startedAt = System.nanoTime();
        final CompletableFuture<T> future =
                retries().executeAsync(operation, task, attempts);
        return withTimeout(future, timeout, operation)
                .whenComplete((result, error) -> {
                    metrics().operationFinished();
                    metrics().recordQuery(operation, System.nanoTime() - startedAt, error == null);
                    if (error != null) {
                        metrics().recordError(unwrap(error).getClass().getSimpleName());
                    }
                });
    }

    private SqlQuery compile(final SqlStatement statement) {
        return statement.compile(dialect);
    }

    private Connection borrow() {
        try {
            return requireDataSource().getConnection();
        } catch (final SQLException e) {
            throw SqlExceptions.translate(e, null);
        }
    }

    private HikariDataSource requireDataSource() {
        final HikariDataSource current = dataSource;
        if (current == null) {
            throw new ConnectionException(
                    "Database '" + name() + "' has no open pool; call connect() first");
        }
        return current;
    }

    private void rollbackQuietly(final Connection connection) {
        try {
            connection.rollback();
        } catch (final SQLException e) {
            // The original failure is the one worth propagating; a failed rollback usually means the
            // connection is already dead, and it is about to be discarded anyway.
            LOGGER.warn("Rollback failed on database '{}'", name(), e);
            evict(connection);
        }
    }

    private void evict(final Connection connection) {
        final HikariDataSource current = dataSource;
        if (current != null) {
            current.evictConnection(connection);
        }
    }

    private void close(final Connection connection) {
        try {
            connection.close();
        } catch (final SQLException e) {
            LOGGER.warn("Failed to return a connection to the pool of '{}'", name(), e);
        }
    }

    /** Unwraps the {@code CompletionException} the future machinery adds, for metric labels. */
    private static Throwable unwrap(final Throwable error) {
        return error instanceof CompletionException && error.getCause() != null
                ? error.getCause()
                : error;
    }

    /** Blocking work against a borrowed connection. */
    @FunctionalInterface
    private interface ConnectionWork<T> {

        T apply(DatabaseConnection connection) throws Exception;
    }
}
