package com.enderbyte.enderlib.database.api.query;

import com.enderbyte.enderlib.database.api.schema.SqlType;

/** H2, mainly used for tests and small embedded deployments. Has a native UUID type. */
final class H2Dialect extends AnsiDialect {

    static final SqlDialect INSTANCE = new H2Dialect();

    private H2Dialect() {
        super('"', '"');
    }

    @Override
    public String name() {
        return "H2";
    }

    @Override
    public String rowLockClause(final boolean skipLocked) {
        // H2 locks rows but has no SKIP LOCKED; degrade to a plain lock rather than fail to parse.
        return " FOR UPDATE";
    }

    /**
     * H2 has neither {@code ON CONFLICT} nor {@code ON DUPLICATE KEY UPDATE}. Its upsert is
     * {@code MERGE INTO t (cols) KEY (key) VALUES (...)}, which changes the leading keyword and puts
     * the key list before {@code VALUES}, so the whole statement is rendered here.
     *
     * <p>{@code MERGE ... KEY} overwrites every listed column, so the caller's narrower
     * {@code updateColumns} cannot be honoured exactly; in practice an upsert lists the columns it
     * is inserting anyway.
     */
    @Override
    public void renderInsert(final SqlRenderContext context, final InsertSpec spec) {
        if (!spec.isUpsert()) {
            super.renderInsert(context, spec);
            return;
        }
        renderInsertHead(context, "MERGE INTO", spec);
        context.sql(" KEY (");
        for (int i = 0; i < spec.conflictColumns().size(); i++) {
            if (i > 0) {
                context.sql(", ");
            }
            context.identifier(spec.conflictColumns().get(i));
        }
        context.sql(')');
        renderValues(context, spec);
    }

    @Override
    public String columnType(final SqlType type, final int length, final int precision, final int scale) {
        return switch (type) {
            case UUID -> "UUID";
            case BINARY -> "VARBINARY(" + (length > 0 ? length : 255) + ')';
            case BLOB -> "BLOB";
            case TIMESTAMP_WITH_TIMEZONE -> "TIMESTAMP WITH TIME ZONE";
            case JSON -> "JSON";
            default -> super.columnType(type, length, precision, scale);
        };
    }

    @Override
    public boolean hasNativeUuidType() {
        return true;
    }
}
