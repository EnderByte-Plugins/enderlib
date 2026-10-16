package com.enderbyte.enderlib.database.api;

import com.enderbyte.enderlib.database.api.cache.CacheBuilder;
import com.enderbyte.enderlib.database.api.cache.CacheManager;
import com.enderbyte.enderlib.database.api.config.DatabaseConfig;
import com.enderbyte.enderlib.database.api.config.DatabaseType;
import com.enderbyte.enderlib.database.api.mapping.ConverterRegistry;
import com.enderbyte.enderlib.database.api.metrics.DatabaseMetrics;
import com.enderbyte.enderlib.database.api.migration.MigrationService;
import com.enderbyte.enderlib.database.api.query.DeleteQuery;
import com.enderbyte.enderlib.database.api.query.InsertQuery;
import com.enderbyte.enderlib.database.api.query.SqlDialect;
import com.enderbyte.enderlib.database.api.query.SqlStatement;
import com.enderbyte.enderlib.database.api.query.UpdateQuery;
import com.enderbyte.enderlib.database.api.repository.CrudRepository;
import com.enderbyte.enderlib.database.api.result.QueryResult;
import com.enderbyte.enderlib.database.api.result.RowMapper;
import com.enderbyte.enderlib.database.api.transaction.TransactionCallback;
import com.enderbyte.enderlib.database.api.transaction.TransactionOptions;
import com.enderbyte.enderlib.database.api.transaction.TransactionWork;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * A configured database, and the entry point of the library.
 *
 * <pre>{@code
 * // in onEnable
 * Database database = Database.builder()
 *         .type(DatabaseType.POSTGRESQL)
 *         .host("localhost")
 *         .port(5432)
 *         .database("minecraft")
 *         .username("minecraft")
 *         .password(config.getString("database.password"))
 *         .build();
 *
 * database.connectAsync();
 *
 * // in onDisable
 * database.close();
 * }</pre>
 *
 * <p>An instance owns a connection pool, an executor and optionally caches and metrics. Create one
 * per logical database and keep it for the plugin's lifetime; there is no global singleton, and
 * several instances (and several plugins) coexist happily in one JVM.
 *
 * <p><strong>Everything is asynchronous.</strong> No method here blocks the caller, which is what
 * keeps database work off the Minecraft server thread. The one exception is {@link #connect()},
 * which is meant for plugin startup and says so.
 *
 * <p>Remember that a future's callbacks run on a database thread. Hop back to the server thread
 * with your plugin's scheduler before touching anything from the Minecraft API.
 *
 * <p>Implementations are thread-safe.
 */
public interface Database extends AutoCloseable {

    /**
     * Starts configuring a database.
     *
     * @return a new builder
     */
    static DatabaseBuilder builder() {
        return new DatabaseBuilder();
    }

    // ------------------------------------------------------------------ identity and lifecycle

    /** @return the logical name, as used in pool names, thread names and metrics */
    String name();

    /** @return the engine */
    DatabaseType type();

    /** @return the configuration this instance was built from */
    DatabaseConfig config();

    /** @return the dialect used to compile query builders for this engine */
    SqlDialect dialect();

    /**
     * Opens the pool and, unless disabled, verifies connectivity.
     *
     * <p>Blocks until the database answers or the connect timeout elapses. That is acceptable
     * during {@code onEnable} — the server is not serving players yet, and starting a plugin whose
     * database is unreachable is usually worse than a slow boot. Use {@link #connectAsync()} if you
     * would rather let the server finish starting.
     *
     * @return this database, connected
     * @throws com.enderbyte.enderlib.database.api.exception.ConnectionException if the database is unreachable
     */
    Database connect();

    /**
     * Opens the pool without blocking.
     *
     * @return a future completing with this database once it is usable
     */
    CompletableFuture<Database> connectAsync();

    /** @return {@code true} once {@link #connect()} succeeded and {@link #close()} has not run */
    boolean isConnected();

    /**
     * Checks that the database answers, without throwing.
     *
     * <p>Suitable for a {@code /myplugin status} command or a periodic health task.
     *
     * @return a future completing with {@code true} when the database responded
     */
    CompletableFuture<Boolean> pingAsync();

    /**
     * Closes the pool, shuts down the owned executor and clears the caches.
     *
     * <p>Call this from {@code onDisable}. It waits up to the configured shutdown timeout for
     * in-flight work, then gives up rather than hanging the server. Idempotent.
     */
    @Override
    void close();

    /**
     * Closes without blocking the caller.
     *
     * @return a future completing when shutdown finished
     */
    CompletableFuture<Void> closeAsync();

    // ------------------------------------------------------------------------------- queries

    /**
     * Runs a query.
     *
     * @param sql        statement text with {@code ?} placeholders
     * @param parameters values to bind
     * @return the materialised result
     */
    CompletableFuture<QueryResult> queryAsync(String sql, Object... parameters);

    /**
     * Runs a built query.
     *
     * @param statement a {@link com.enderbyte.enderlib.database.api.query.SelectQuery} or {@code SqlQuery}
     * @return the materialised result
     */
    CompletableFuture<QueryResult> queryAsync(SqlStatement statement);

    /**
     * Runs a query and maps every row onto an entity.
     *
     * @param statement query to run
     * @param type      entity class
     * @param <T>       entity type
     * @return the mapped rows
     */
    <T> CompletableFuture<List<T>> queryAsync(SqlStatement statement, Class<T> type);

    /**
     * Runs a query and maps every row with a hand-written mapper.
     *
     * @param statement query to run
     * @param mapper    row mapper
     * @param <T>       result type
     * @return the mapped rows
     */
    <T> CompletableFuture<List<T>> queryAsync(SqlStatement statement, RowMapper<T> mapper);

    /**
     * Runs a query and maps the first row, the shape most player lookups take.
     *
     * @param statement query to run
     * @param type      entity class
     * @param <T>       entity type
     * @return the first mapped row, or empty
     */
    <T> CompletableFuture<Optional<T>> queryFirstAsync(SqlStatement statement, Class<T> type);

    // ------------------------------------------------------------------------------- updates

    /**
     * Runs an insert, update or delete.
     *
     * @param sql        statement text with {@code ?} placeholders
     * @param parameters values to bind
     * @return the number of affected rows
     */
    CompletableFuture<Integer> executeAsync(String sql, Object... parameters);

    /** Runs a built statement. */
    CompletableFuture<Integer> executeAsync(SqlStatement statement);

    /**
     * Runs an insert.
     *
     * @param query the insert
     * @return the number of inserted rows
     */
    CompletableFuture<Integer> insertAsync(InsertQuery query);

    /**
     * Runs an insert and returns the keys the database generated.
     *
     * @param query the insert
     * @return a result whose {@link QueryResult#generatedKeys()} holds the new keys
     */
    CompletableFuture<QueryResult> insertReturningKeysAsync(InsertQuery query);

    /**
     * Runs an update.
     *
     * @param query the update
     * @return the number of updated rows
     */
    CompletableFuture<Integer> updateAsync(UpdateQuery query);

    /**
     * Runs a delete.
     *
     * @param query the delete
     * @return the number of deleted rows
     */
    CompletableFuture<Integer> deleteAsync(DeleteQuery query);

    // -------------------------------------------------------------------------------- batches

    /**
     * Runs one statement many times as a JDBC batch, in a single transaction.
     *
     * <p>Use this for bulk work — flushing a stats buffer, saving every online player on shutdown.
     * A batch of a thousand rows is one round trip instead of a thousand.
     *
     * @param sql           statement text with {@code ?} placeholders
     * @param parameterSets one list of values per execution
     * @return per-statement update counts
     */
    CompletableFuture<int[]> batchAsync(String sql, List<List<Object>> parameterSets);

    /**
     * Batch-inserts rows into a table.
     *
     * @param table   target table
     * @param columns column names
     * @param rows    one list of values per row, matching {@code columns}
     * @return per-statement update counts
     */
    CompletableFuture<int[]> batchInsertAsync(String table, List<String> columns, List<List<Object>> rows);

    /**
     * Batch-updates using one statement and many parameter sets.
     *
     * @param sql           update statement with {@code ?} placeholders
     * @param parameterSets one list of values per execution
     * @return per-statement update counts
     */
    CompletableFuture<int[]> batchUpdateAsync(String sql, List<List<Object>> parameterSets);

    // --------------------------------------------------------------------------- transactions

    /**
     * Runs a transaction that produces a value. Committed on normal return, rolled back on any
     * exception, and the connection is always released.
     *
     * <pre>{@code
     * database.transactionAsync(tx -> tx.query("SELECT balance FROM accounts WHERE uuid = ?", uuid)
     *         .value(Long.class)
     *         .orElse(0L));
     * }</pre>
     *
     * @param callback transaction body
     * @param <T>      result type
     * @return the callback's value
     */
    <T> CompletableFuture<T> transactionAsync(TransactionCallback<T> callback);

    /**
     * Runs a transaction that produces no value.
     *
     * <pre>{@code
     * database.runTransactionAsync(tx -> {
     *     tx.execute("UPDATE accounts SET balance = balance - ? WHERE uuid = ?", amount, from);
     *     tx.execute("UPDATE accounts SET balance = balance + ? WHERE uuid = ?", amount, to);
     * });
     * }</pre>
     *
     * <p>This is a separate name rather than an overload of {@code transactionAsync} on purpose: a
     * lambda such as {@code tx -> tx.execute(...)} fits both a value-returning and a void body, and
     * overloading would make that ordinary-looking code fail to compile as ambiguous.
     *
     * @param work transaction body
     * @return completion signal
     */
    CompletableFuture<Void> runTransactionAsync(TransactionWork work);

    /**
     * Runs a transaction with explicit isolation, read-only hint or timeout.
     *
     * @param options  transaction settings
     * @param callback transaction body
     * @param <T>      result type
     * @return the callback's value
     */
    <T> CompletableFuture<T> transactionAsync(TransactionOptions options, TransactionCallback<T> callback);

    /**
     * Runs a value-less transaction with explicit isolation, read-only hint or timeout.
     *
     * @param options transaction settings
     * @param work    transaction body
     * @return completion signal
     */
    CompletableFuture<Void> runTransactionAsync(TransactionOptions options, TransactionWork work);

    /**
     * Borrows a connection for work the rest of the API does not cover, releasing it afterwards.
     *
     * @param work function receiving the connection
     * @param <T>  result type
     * @return the function's value
     */
    <T> CompletableFuture<T> withConnectionAsync(Function<DatabaseConnection, T> work);

    // ------------------------------------------------------------------- supporting subsystems

    /**
     * @return the migration service for this database
     * @throws com.enderbyte.enderlib.database.api.exception.ConfigurationException if the
     *         {@code enderlib-database-migrations} module is not on the classpath
     */
    MigrationService migrations();

    /**
     * Builds a ready-made repository for an annotated entity.
     *
     * @param entityType entity class annotated with {@link com.enderbyte.enderlib.database.api.mapping.Table}
     * @param idType     primary key type
     * @param <T>        entity type
     * @param <ID>       key type
     * @return a repository; repeated calls for the same entity return the same instance
     */
    <T, ID> CrudRepository<T, ID> repository(Class<T> entityType, Class<ID> idType);

    /**
     * @return the executor that runs database work. Use it to chain follow-up work off the server
     *         thread; do not submit long-running unrelated tasks to it.
     */
    Executor executor();

    /** @return the metrics collector; a no-op instance when metrics are disabled */
    DatabaseMetrics metrics();

    /** @return the cache manager for this database */
    CacheManager caches();

    /**
     * Starts building a named cache. Requires an explicit type witness; the three-argument overload
     * is usually more readable.
     *
     * @param name unique cache name
     * @param <K>  key type
     * @param <V>  value type
     * @return the cache builder
     */
    <K, V> CacheBuilder<K, V> cache(String name);

    /**
     * Starts building a named cache with inferred types.
     *
     * <pre>{@code
     * Cache<UUID, PlayerData> cache = database.cache("players", UUID.class, PlayerData.class)
     *         .maximumSize(100_000)
     *         .expireAfterWrite(Duration.ofMinutes(10))
     *         .build();
     * }</pre>
     *
     * @param name      unique cache name
     * @param keyType   key type, for inference only
     * @param valueType value type, for inference only
     * @param <K>       key type
     * @param <V>       value type
     * @return the cache builder
     */
    <K, V> CacheBuilder<K, V> cache(String name, Class<K> keyType, Class<V> valueType);

    /** @return the converter registry, where custom {@code TypeConverter}s are registered */
    ConverterRegistry converters();
}
