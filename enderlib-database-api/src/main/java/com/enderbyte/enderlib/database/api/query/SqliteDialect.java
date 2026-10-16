package com.enderbyte.enderlib.database.api.query;

import com.enderbyte.enderlib.database.api.schema.SqlType;

/**
 * SQLite.
 *
 * <p>Single-writer, file-backed: fine for a standalone server, never for a shared network database.
 * Type affinity means most declared types are advisory.
 */
final class SqliteDialect extends AnsiDialect {

    static final SqlDialect INSTANCE = new SqliteDialect();

    private SqliteDialect() {
        super('"', '"');
    }

    @Override
    public String name() {
        return "SQLite";
    }

    @Override
    public void appendPagination(final StringBuilder sql, final int limit, final int offset) {
        if (limit < 0 && offset <= 0) {
            return;
        }
        // SQLite requires a LIMIT before OFFSET; -1 means "unlimited".
        sql.append(" LIMIT ").append(limit >= 0 ? limit : -1);
        if (offset > 0) {
            sql.append(" OFFSET ").append(offset);
        }
    }

    @Override
    public String rowLockClause(final boolean skipLocked) {
        // SQLite serialises writers at file level; there is no row lock to take.
        return "";
    }

    @Override
    public String columnType(final SqlType type, final int length, final int precision, final int scale) {
        return switch (type) {
            case BOOLEAN -> "INTEGER";
            case TINYINT, SMALLINT, INTEGER, BIGINT -> "INTEGER";
            case FLOAT, DOUBLE, DECIMAL -> "REAL";
            case CHAR, VARCHAR, TEXT, JSON -> "TEXT";
            case UUID -> "TEXT";
            case BINARY, BLOB -> "BLOB";
            case DATE, TIME, TIMESTAMP, TIMESTAMP_WITH_TIMEZONE -> "TEXT";
        };
    }
}
