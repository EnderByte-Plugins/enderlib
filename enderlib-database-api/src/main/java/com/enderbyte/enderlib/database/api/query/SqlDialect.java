package com.enderbyte.enderlib.database.api.query;

import com.enderbyte.enderlib.database.api.config.DatabaseType;
import com.enderbyte.enderlib.database.api.schema.SqlType;

/**
 * Engine-specific SQL rendering rules.
 *
 * <p>Everything the query builder and schema helpers need to know about a particular engine lives
 * behind this interface: identifier quoting, pagination syntax, upsert syntax and column type
 * names. Supporting another JDBC engine means adding one implementation, not touching the builders.
 *
 * <p>Implementations must be stateless and thread-safe.
 */
public interface SqlDialect {

    /** @return a neutral ANSI dialect, used when no engine is known */
    static SqlDialect ansi() {
        return AnsiDialect.INSTANCE;
    }

    static SqlDialect postgresql() {
        return PostgresDialect.INSTANCE;
    }

    static SqlDialect mysql() {
        return MySqlDialect.MYSQL;
    }

    static SqlDialect mariadb() {
        return MySqlDialect.MARIADB;
    }

    static SqlDialect sqlite() {
        return SqliteDialect.INSTANCE;
    }

    static SqlDialect h2() {
        return H2Dialect.INSTANCE;
    }

    /**
     * @param type engine to render for
     * @return the matching dialect
     */
    static SqlDialect forType(final DatabaseType type) {
        return switch (type) {
            case POSTGRESQL -> postgresql();
            case MYSQL -> mysql();
            case MARIADB -> mariadb();
            case SQLITE -> sqlite();
            case H2 -> h2();
        };
    }

    /** @return short name of this dialect, for logs */
    String name();

    /**
     * Validates and, if necessary, quotes a single identifier.
     *
     * <p>Identifiers are only quoted when they collide with a SQL keyword. Quoting everything would
     * be safe against keywords but changes case-sensitivity semantics on PostgreSQL, where a quoted
     * {@code "Uuid"} is a different column from an unquoted {@code Uuid}.
     *
     * @param identifier raw name
     * @return renderable identifier
     * @throws IllegalArgumentException if the name is not a plain SQL identifier
     */
    String identifier(String identifier);

    /**
     * Validates and renders a possibly qualified reference such as {@code players.uuid} or
     * {@code *}.
     *
     * @param reference raw reference
     * @return renderable reference
     */
    String reference(String reference);

    /**
     * Appends pagination to a rendered {@code SELECT}.
     *
     * @param sql    statement being built
     * @param limit  maximum rows, or {@code -1} for none
     * @param offset rows to skip, or {@code -1} for none
     */
    void appendPagination(StringBuilder sql, int limit, int offset);

    /**
     * Renders a complete {@code INSERT}, including "update the row if the key already exists" when
     * {@link InsertSpec#isUpsert()}.
     *
     * @param context render target
     * @param spec    the statement to render
     * @throws UnsupportedOperationException if the engine cannot express the requested upsert
     */
    void renderInsert(SqlRenderContext context, InsertSpec spec);

    /**
     * Renders a pessimistic row lock for a {@code SELECT}.
     *
     * @param skipLocked skip rows already locked by another transaction
     * @return the clause including a leading space, or an empty string when unsupported
     */
    String rowLockClause(boolean skipLocked);

    /**
     * @param type      logical type
     * @param length    length for character types, or {@code -1}
     * @param precision numeric precision, or {@code -1}
     * @param scale     numeric scale, or {@code -1}
     * @return the engine's column type
     */
    String columnType(SqlType type, int length, int precision, int scale);

    /**
     * @return {@code true} if {@code CREATE TABLE} and friends participate in transactions. False
     *         for MySQL/MariaDB, which commit implicitly on DDL — relevant for migrations.
     */
    boolean supportsTransactionalDdl();

    /** @return {@code true} if the engine supports {@code CREATE INDEX IF NOT EXISTS} */
    boolean supportsIfNotExistsForIndex();

    /**
     * @return {@code true} if UUIDs have a native column type. PostgreSQL and H2 do; MySQL,
     *         MariaDB and SQLite store them as {@code BINARY(16)} or {@code CHAR(36)}.
     */
    boolean hasNativeUuidType();

    /** @return the statement used to prove a connection is alive */
    String validationQuery();
}
