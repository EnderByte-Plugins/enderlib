package com.enderbyte.enderlib.database.api.query;

import com.enderbyte.enderlib.database.api.schema.SqlType;

import java.util.List;

/**
 * MySQL and MariaDB.
 *
 * <p>Differences that matter: backtick quoting, {@code ON DUPLICATE KEY UPDATE} instead of
 * {@code ON CONFLICT}, {@code OFFSET} requires a {@code LIMIT}, and DDL commits implicitly — the
 * last point is why migrations cannot be rolled back on these engines.
 */
final class MySqlDialect extends AnsiDialect {

    static final SqlDialect MYSQL = new MySqlDialect("MySQL", false);
    static final SqlDialect MARIADB = new MySqlDialect("MariaDB", true);

    /** MySQL's documented "no limit" sentinel when only an offset is wanted. */
    private static final String NO_LIMIT = "18446744073709551615";

    private final String name;
    private final boolean mariadb;

    private MySqlDialect(final String name, final boolean mariadb) {
        super('`', '`');
        this.name = name;
        this.mariadb = mariadb;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void appendPagination(final StringBuilder sql, final int limit, final int offset) {
        if (limit < 0 && offset <= 0) {
            return;
        }
        sql.append(" LIMIT ").append(limit >= 0 ? Integer.toString(limit) : NO_LIMIT);
        if (offset > 0) {
            sql.append(" OFFSET ").append(offset);
        }
    }

    @Override
    void appendConflictClause(final SqlRenderContext context, final InsertSpec spec) {
        // MySQL infers the conflicting key itself, so conflictColumns is unused here.
        final StringBuilder clause = new StringBuilder(64);
        clause.append(" ON DUPLICATE KEY UPDATE ");
        final List<String> updateColumns = spec.updateColumns();
        for (int i = 0; i < updateColumns.size(); i++) {
            if (i > 0) {
                clause.append(", ");
            }
            final String column = identifier(updateColumns.get(i));
            clause.append(column).append(" = VALUES(").append(column).append(')');
        }
        context.sql(clause.toString());
    }

    @Override
    public String columnType(final SqlType type, final int length, final int precision, final int scale) {
        return switch (type) {
            case BOOLEAN -> "TINYINT(1)";
            case TINYINT -> "TINYINT";
            case FLOAT -> "FLOAT";
            case DOUBLE -> "DOUBLE";
            case TEXT -> "TEXT";
            // CHAR(36) keeps UUIDs readable from any other tool or plugin touching the same table.
            // BINARY(16) is smaller and indexes better; opt into it explicitly with SqlType.BINARY.
            case UUID -> "CHAR(36)";
            case BINARY -> "BINARY(" + (length > 0 ? length : 16) + ')';
            case BLOB -> "LONGBLOB";
            case TIMESTAMP -> "DATETIME(3)";
            case TIMESTAMP_WITH_TIMEZONE -> "TIMESTAMP(3)";
            case JSON -> "JSON";
            default -> super.columnType(type, length, precision, scale);
        };
    }

    @Override
    public boolean supportsTransactionalDdl() {
        return false;
    }

    @Override
    public boolean supportsIfNotExistsForIndex() {
        return mariadb;
    }
}
