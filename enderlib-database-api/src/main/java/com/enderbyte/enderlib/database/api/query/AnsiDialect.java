package com.enderbyte.enderlib.database.api.query;

import com.enderbyte.enderlib.database.api.schema.SqlType;
import com.enderbyte.enderlib.database.api.util.Identifiers;

import java.util.List;

/**
 * Neutral ANSI rendering. Also the base class for the engine-specific dialects, which override only
 * what actually differs.
 */
class AnsiDialect implements SqlDialect {

    static final SqlDialect INSTANCE = new AnsiDialect();

    private final char openQuote;
    private final char closeQuote;

    AnsiDialect() {
        this('"', '"');
    }

    AnsiDialect(final char openQuote, final char closeQuote) {
        this.openQuote = openQuote;
        this.closeQuote = closeQuote;
    }

    @Override
    public String name() {
        return "ANSI";
    }

    @Override
    public String identifier(final String identifier) {
        Identifiers.requireValid(identifier, "identifier");
        return Identifiers.isReserved(identifier)
                ? openQuote + identifier + closeQuote
                : identifier;
    }

    @Override
    public String reference(final String reference) {
        Identifiers.requireValidReference(reference);
        if (reference.indexOf('.') < 0) {
            return "*".equals(reference) ? reference : identifier(reference);
        }
        final StringBuilder sb = new StringBuilder(reference.length() + 4);
        int start = 0;
        while (start <= reference.length()) {
            int dot = reference.indexOf('.', start);
            if (dot < 0) {
                dot = reference.length();
            }
            final String part = reference.substring(start, dot);
            if (start > 0) {
                sb.append('.');
            }
            sb.append("*".equals(part) ? part : identifier(part));
            start = dot + 1;
        }
        return sb.toString();
    }

    @Override
    public void appendPagination(final StringBuilder sql, final int limit, final int offset) {
        // Limit and offset are validated non-negative ints owned by the builder, never user text,
        // so inlining them is injection-safe and lets the engine plan for the actual row count.
        if (limit >= 0) {
            sql.append(" LIMIT ").append(limit);
        }
        if (offset > 0) {
            sql.append(" OFFSET ").append(offset);
        }
    }

    @Override
    public void renderInsert(final SqlRenderContext context, final InsertSpec spec) {
        renderInsertHead(context, "INSERT INTO", spec);
        renderValues(context, spec);
        if (spec.isUpsert()) {
            appendConflictClause(context, spec);
        }
    }

    /** {@code ON CONFLICT (key) DO UPDATE SET col = EXCLUDED.col}, as used by PostgreSQL and SQLite. */
    void appendConflictClause(final SqlRenderContext context, final InsertSpec spec) {
        final StringBuilder clause = new StringBuilder(64);
        clause.append(" ON CONFLICT (");
        appendJoined(clause, spec.conflictColumns());
        clause.append(") DO UPDATE SET ");
        for (int i = 0; i < spec.updateColumns().size(); i++) {
            if (i > 0) {
                clause.append(", ");
            }
            final String column = identifier(spec.updateColumns().get(i));
            clause.append(column).append(" = EXCLUDED.").append(column);
        }
        context.sql(clause.toString());
    }

    /** Renders {@code <keyword> table (col, col)}. */
    final void renderInsertHead(final SqlRenderContext context,
                                final String keyword,
                                final InsertSpec spec) {
        context.sql(keyword).sql(' ').identifier(spec.table()).sql(" (");
        for (int i = 0; i < spec.columns().size(); i++) {
            if (i > 0) {
                context.sql(", ");
            }
            context.identifier(spec.columns().get(i));
        }
        context.sql(')');
    }

    /** Renders {@code VALUES (?, ?), (?, ?)}. */
    final void renderValues(final SqlRenderContext context, final InsertSpec spec) {
        context.sql(" VALUES ");
        for (int row = 0; row < spec.rows().size(); row++) {
            if (row > 0) {
                context.sql(", ");
            }
            context.sql('(').parameters(spec.rows().get(row)).sql(')');
        }
    }

    @Override
    public String rowLockClause(final boolean skipLocked) {
        return skipLocked ? " FOR UPDATE SKIP LOCKED" : " FOR UPDATE";
    }

    @Override
    public String columnType(final SqlType type, final int length, final int precision, final int scale) {
        return switch (type) {
            case BOOLEAN -> "BOOLEAN";
            case TINYINT -> "SMALLINT";
            case SMALLINT -> "SMALLINT";
            case INTEGER -> "INTEGER";
            case BIGINT -> "BIGINT";
            case FLOAT -> "REAL";
            case DOUBLE -> "DOUBLE PRECISION";
            case DECIMAL -> "DECIMAL(" + (precision > 0 ? precision : 19) + ", " + (scale >= 0 ? scale : 4) + ')';
            case CHAR -> "CHAR(" + (length > 0 ? length : 1) + ')';
            case VARCHAR -> "VARCHAR(" + (length > 0 ? length : 255) + ')';
            case TEXT -> "TEXT";
            case UUID -> "CHAR(36)";
            case BINARY -> "VARBINARY(" + (length > 0 ? length : 255) + ')';
            case BLOB -> "BLOB";
            case DATE -> "DATE";
            case TIME -> "TIME";
            case TIMESTAMP -> "TIMESTAMP";
            case TIMESTAMP_WITH_TIMEZONE -> "TIMESTAMP WITH TIME ZONE";
            case JSON -> "TEXT";
        };
    }

    @Override
    public boolean supportsTransactionalDdl() {
        return true;
    }

    @Override
    public boolean supportsIfNotExistsForIndex() {
        return true;
    }

    @Override
    public boolean hasNativeUuidType() {
        return false;
    }

    @Override
    public String validationQuery() {
        return "SELECT 1";
    }

    final void appendJoined(final StringBuilder sql, final List<String> columns) {
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(identifier(columns.get(i)));
        }
    }
}
