package com.enderbyte.enderlib.database.api.query;

import com.enderbyte.enderlib.database.api.schema.SqlType;

/** PostgreSQL: native UUID and JSONB types, transactional DDL, ANSI pagination and upserts. */
final class PostgresDialect extends AnsiDialect {

    static final SqlDialect INSTANCE = new PostgresDialect();

    private PostgresDialect() {
        super('"', '"');
    }

    @Override
    public String name() {
        return "PostgreSQL";
    }

    @Override
    public String columnType(final SqlType type, final int length, final int precision, final int scale) {
        return switch (type) {
            case TINYINT -> "SMALLINT";
            case FLOAT -> "REAL";
            case DOUBLE -> "DOUBLE PRECISION";
            case UUID -> "UUID";
            case BINARY, BLOB -> "BYTEA";
            case TIMESTAMP_WITH_TIMEZONE -> "TIMESTAMPTZ";
            case JSON -> "JSONB";
            default -> super.columnType(type, length, precision, scale);
        };
    }

    @Override
    public boolean hasNativeUuidType() {
        return true;
    }
}
