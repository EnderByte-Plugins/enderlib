package com.enderbyte.enderlib.database.api.result;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * One row of a query result, already detached from the driver.
 *
 * <p>Rows are materialised while the JDBC {@code ResultSet} is still open and stay valid after the
 * connection returns to the pool. That is what makes the asynchronous API safe: a plugin can hold a
 * {@code Row} for as long as it likes without pinning a connection.
 *
 * <p>Column lookup is case-insensitive, matching the behaviour of every supported engine.
 *
 * <p>Typed getters return the type's zero value when the column is SQL {@code NULL}; use
 * {@link #isNull(String)} or {@link #optional(String, Class)} when the difference matters.
 */
public interface Row {

    /**
     * @param column column name or alias
     * @return the raw value, or {@code null}
     * @throws com.enderbyte.enderlib.database.api.exception.MappingException if the column is absent
     */
    Object get(String column);

    /**
     * Converts a column to the requested type using the registered converters.
     *
     * @param column column name or alias
     * @param type   target type
     * @param <T>    target type
     * @return the converted value, or {@code null} when the column is SQL {@code NULL}
     */
    <T> T get(String column, Class<T> type);

    /** @return the value by 0-based position, useful for aggregate results without an alias */
    Object get(int index);

    /** @return the converted value, or empty when the column is absent or {@code NULL} */
    <T> Optional<T> optional(String column, Class<T> type);

    String getString(String column);

    boolean getBoolean(String column);

    int getInt(String column);

    long getLong(String column);

    float getFloat(String column);

    double getDouble(String column);

    BigDecimal getBigDecimal(String column);

    /**
     * Reads a UUID regardless of how the engine stores it: a native {@code UUID} column, a
     * {@code CHAR(36)} string, or a {@code BINARY(16)} blob.
     *
     * @param column column name
     * @return the UUID, or {@code null}
     */
    UUID getUuid(String column);

    Instant getInstant(String column);

    LocalDate getLocalDate(String column);

    LocalDateTime getLocalDateTime(String column);

    byte[] getBytes(String column);

    <E extends Enum<E>> E getEnum(String column, Class<E> type);

    /** @return {@code true} if the column exists and holds SQL {@code NULL} */
    boolean isNull(String column);

    /** @return {@code true} if the result contains this column */
    boolean hasColumn(String column);

    /** @return the column names in selection order */
    Set<String> columns();

    /** @return an immutable snapshot of this row */
    Map<String, Object> asMap();
}
