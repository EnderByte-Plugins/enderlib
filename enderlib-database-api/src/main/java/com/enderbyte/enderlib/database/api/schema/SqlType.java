package com.enderbyte.enderlib.database.api.schema;

/**
 * Engine-neutral column types used by the schema helpers.
 *
 * <p>Each {@link com.enderbyte.enderlib.database.api.query.SqlDialect} maps these to a native type, so a
 * schema definition written once works on PostgreSQL, MySQL, MariaDB, SQLite and H2.
 */
public enum SqlType {

    BOOLEAN,
    TINYINT,
    SMALLINT,
    INTEGER,
    BIGINT,
    FLOAT,
    DOUBLE,
    DECIMAL,
    CHAR,
    VARCHAR,
    TEXT,
    /** Stored natively where available, otherwise as {@code BINARY(16)} or {@code CHAR(36)}. */
    UUID,
    BINARY,
    BLOB,
    DATE,
    TIME,
    TIMESTAMP,
    /** Instant-in-time with timezone semantics; the right choice for {@code last_seen} columns. */
    TIMESTAMP_WITH_TIMEZONE,
    JSON
}
