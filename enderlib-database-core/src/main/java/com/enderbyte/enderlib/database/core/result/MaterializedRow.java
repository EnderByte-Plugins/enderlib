package com.enderbyte.enderlib.database.core.result;

import com.enderbyte.enderlib.database.api.exception.MappingException;
import com.enderbyte.enderlib.database.api.mapping.ConverterRegistry;
import com.enderbyte.enderlib.database.api.result.Row;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A row read out of the driver and detached from it.
 *
 * <p>Values are stored in a flat array indexed through a shared {@link ColumnIndex}, so a row costs
 * one array and nothing else. Because nothing here references the {@code ResultSet}, the connection
 * is free the moment the result is built.
 */
public final class MaterializedRow implements Row {

    private final ColumnIndex index;
    private final Object[] values;
    private final ConverterRegistry converters;

    /**
     * @param index      shared column index
     * @param values     raw driver values, one per column
     * @param converters registry used by the typed getters
     */
    public MaterializedRow(final ColumnIndex index, final Object[] values, final ConverterRegistry converters) {
        this.index = index;
        this.values = values;
        this.converters = converters;
    }

    @Override
    public Object get(final String column) {
        final int position = index.position(column);
        if (position < 0) {
            throw new MappingException("The result has no column '" + column
                    + "'. Available columns: " + index.names());
        }
        return values[position];
    }

    @Override
    public <T> T get(final String column, final Class<T> type) {
        return converters.fromDatabase(get(column), type);
    }

    @Override
    public Object get(final int position) {
        if (position < 0 || position >= values.length) {
            throw new MappingException("Column index " + position + " is out of range (0.."
                    + (values.length - 1) + ')');
        }
        return values[position];
    }

    @Override
    public <T> Optional<T> optional(final String column, final Class<T> type) {
        final int position = index.position(column);
        if (position < 0 || values[position] == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(converters.fromDatabase(values[position], type));
    }

    @Override
    public String getString(final String column) {
        return get(column, String.class);
    }

    @Override
    public boolean getBoolean(final String column) {
        final Boolean value = get(column, Boolean.class);
        return value != null && value;
    }

    @Override
    public int getInt(final String column) {
        final Integer value = get(column, Integer.class);
        return value == null ? 0 : value;
    }

    @Override
    public long getLong(final String column) {
        final Long value = get(column, Long.class);
        return value == null ? 0L : value;
    }

    @Override
    public float getFloat(final String column) {
        final Float value = get(column, Float.class);
        return value == null ? 0.0f : value;
    }

    @Override
    public double getDouble(final String column) {
        final Double value = get(column, Double.class);
        return value == null ? 0.0d : value;
    }

    @Override
    public BigDecimal getBigDecimal(final String column) {
        return get(column, BigDecimal.class);
    }

    @Override
    public UUID getUuid(final String column) {
        return get(column, UUID.class);
    }

    @Override
    public Instant getInstant(final String column) {
        return get(column, Instant.class);
    }

    @Override
    public LocalDate getLocalDate(final String column) {
        return get(column, LocalDate.class);
    }

    @Override
    public LocalDateTime getLocalDateTime(final String column) {
        return get(column, LocalDateTime.class);
    }

    @Override
    public byte[] getBytes(final String column) {
        return get(column, byte[].class);
    }

    @Override
    public <E extends Enum<E>> E getEnum(final String column, final Class<E> type) {
        return get(column, type);
    }

    @Override
    public boolean isNull(final String column) {
        final int position = index.position(column);
        return position < 0 || values[position] == null;
    }

    @Override
    public boolean hasColumn(final String column) {
        return index.position(column) >= 0;
    }

    @Override
    public Set<String> columns() {
        return index.names();
    }

    @Override
    public Map<String, Object> asMap() {
        final Map<String, Object> map = new LinkedHashMap<>(values.length * 2);
        int position = 0;
        for (final String name : index.names()) {
            map.put(name, values[position++]);
        }
        return Collections.unmodifiableMap(map);
    }

    @Override
    public String toString() {
        // Column names only: values may contain player data that should not reach a log by accident.
        return "Row" + index.names();
    }
}
