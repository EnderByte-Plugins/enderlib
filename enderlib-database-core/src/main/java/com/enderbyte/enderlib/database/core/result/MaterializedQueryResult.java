package com.enderbyte.enderlib.database.core.result;

import com.enderbyte.enderlib.database.api.exception.MappingException;
import com.enderbyte.enderlib.database.api.mapping.ConverterRegistry;
import com.enderbyte.enderlib.database.api.result.QueryResult;
import com.enderbyte.enderlib.database.api.result.Row;
import com.enderbyte.enderlib.database.api.result.RowMapper;
import com.enderbyte.enderlib.database.core.mapping.EntityRowMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A fully read, immutable query result.
 *
 * <p>Reading everything up front is a deliberate trade: it costs memory proportional to the result
 * size but frees the connection immediately and makes the result safe to pass to any thread. Use
 * {@code LIMIT} for large tables rather than expecting a lazy cursor.
 */
public final class MaterializedQueryResult implements QueryResult {

    private static final MaterializedQueryResult EMPTY =
            new MaterializedQueryResult(List.of(), List.of(), null);

    private final List<Row> rows;
    private final List<Object> generatedKeys;
    private final ConverterRegistry converters;

    /**
     * @param rows          the rows, already detached from the driver
     * @param generatedKeys keys produced by an insert, or an empty list
     * @param converters    registry used for entity mapping
     */
    public MaterializedQueryResult(final List<Row> rows,
                                   final List<Object> generatedKeys,
                                   final ConverterRegistry converters) {
        this.rows = Collections.unmodifiableList(rows);
        this.generatedKeys = Collections.unmodifiableList(generatedKeys);
        this.converters = converters;
    }

    /** @return a shared empty result */
    public static QueryResult empty() {
        return EMPTY;
    }

    @Override
    public Optional<Row> first() {
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public Row firstOrNull() {
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public List<Row> all() {
        return rows;
    }

    @Override
    public boolean isEmpty() {
        return rows.isEmpty();
    }

    @Override
    public int size() {
        return rows.size();
    }

    @Override
    public Stream<Row> stream() {
        return rows.stream();
    }

    @Override
    public <T> Optional<T> first(final Class<T> type) {
        return first(mapperFor(type));
    }

    @Override
    public <T> Optional<T> first(final RowMapper<T> mapper) {
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(mapper.map(rows.get(0)));
    }

    @Override
    public <T> List<T> map(final Class<T> type) {
        return map(mapperFor(type));
    }

    @Override
    public <T> List<T> map(final RowMapper<T> mapper) {
        final List<T> mapped = new ArrayList<>(rows.size());
        for (final Row row : rows) {
            mapped.add(mapper.map(row));
        }
        return Collections.unmodifiableList(mapped);
    }

    @Override
    public <T> Optional<T> value(final Class<T> type) {
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        final Row row = rows.get(0);
        if (row.columns().isEmpty()) {
            return Optional.empty();
        }
        final Object raw = row.get(0);
        if (raw == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(requireConverters().fromDatabase(raw, type));
    }

    @Override
    public List<Object> generatedKeys() {
        return generatedKeys;
    }

    @Override
    public Iterator<Row> iterator() {
        return rows.iterator();
    }

    @Override
    public String toString() {
        return "QueryResult{rows=" + rows.size() + '}';
    }

    private <T> RowMapper<T> mapperFor(final Class<T> type) {
        return new EntityRowMapper<>(type, requireConverters());
    }

    private ConverterRegistry requireConverters() {
        if (converters == null) {
            throw new MappingException("This result was created without a converter registry"
                    + " and cannot map values");
        }
        return converters;
    }
}
