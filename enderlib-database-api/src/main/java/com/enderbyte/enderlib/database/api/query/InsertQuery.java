package com.enderbyte.enderlib.database.api.query;

import com.enderbyte.enderlib.database.api.util.Preconditions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Fluent {@code INSERT} builder, including multi-row inserts and upserts.
 *
 * <pre>{@code
 * SqlQuery insert = Query.insertInto("players")
 *         .value("uuid", uuid)
 *         .value("name", name)
 *         .value("balance", 100)
 *         .build();
 *
 * // "save or create", rendered as ON CONFLICT / ON DUPLICATE KEY UPDATE per engine
 * SqlQuery upsert = Query.insertInto("players")
 *         .value("uuid", uuid)
 *         .value("name", name)
 *         .onConflictUpdate(List.of("uuid"), List.of("name"))
 *         .build();
 * }</pre>
 */
public final class InsertQuery implements SqlStatement {

    private final String table;
    private final List<String> columns = new ArrayList<>(8);
    private final List<List<Object>> rows = new ArrayList<>(1);
    private final Map<String, Object> pendingRow = new LinkedHashMap<>();
    private List<String> conflictColumns;
    private List<String> updateColumns;

    InsertQuery(final String table) {
        this.table = Preconditions.requireNonBlank(table, "table");
    }

    /**
     * Sets a column value for the row currently being built.
     *
     * @param column column name
     * @param value  value, bound as a parameter
     * @return this builder
     */
    public InsertQuery value(final String column, final Object value) {
        Preconditions.requireNonBlank(column, "column");
        Preconditions.checkState(rows.isEmpty() || !columns.isEmpty(),
                "Cannot mix value() with columns()/row()");
        pendingRow.put(column, value);
        return this;
    }

    /** Sets several column values at once. */
    public InsertQuery values(final Map<String, ?> values) {
        values.forEach(this::value);
        return this;
    }

    /**
     * Declares the column list for multi-row inserts, to be followed by {@link #row(Object...)}.
     */
    public InsertQuery columns(final String... columnNames) {
        Preconditions.checkState(pendingRow.isEmpty(), "Cannot mix columns() with value()");
        columns.clear();
        for (final String column : columnNames) {
            columns.add(Preconditions.requireNonBlank(column, "column"));
        }
        return this;
    }

    /**
     * Adds a row for the previously declared {@link #columns(String...)}.
     *
     * <p>Multi-row inserts are dramatically faster than one statement per row; for very large
     * volumes prefer {@code batchInsertAsync}, which streams through a JDBC batch instead of
     * building one enormous statement.
     *
     * @param values one value per declared column
     * @return this builder
     */
    public InsertQuery row(final Object... values) {
        Preconditions.checkState(!columns.isEmpty(), "columns(...) must be declared before row(...)");
        if (values.length != columns.size()) {
            throw new IllegalArgumentException("Expected " + columns.size()
                    + " values but got " + values.length);
        }
        rows.add(Arrays.asList(values));
        return this;
    }

    /**
     * Turns this insert into an upsert.
     *
     * @param conflictColumns the unique key that may collide (ignored by MySQL/MariaDB, which infer it)
     * @param updateColumns   columns to overwrite when the row already exists
     * @return this builder
     */
    public InsertQuery onConflictUpdate(final List<String> conflictColumns, final List<String> updateColumns) {
        Objects.requireNonNull(conflictColumns, "conflictColumns");
        Objects.requireNonNull(updateColumns, "updateColumns");
        if (updateColumns.isEmpty()) {
            throw new IllegalArgumentException("At least one column must be updated on conflict");
        }
        this.conflictColumns = List.copyOf(conflictColumns);
        this.updateColumns = List.copyOf(updateColumns);
        return this;
    }

    /**
     * Upserts every inserted column except the conflict key. The common "save this player, create
     * the row if it is new" case.
     *
     * @param conflictColumns the unique key
     * @return this builder
     */
    public InsertQuery onConflictUpdateAll(final String... conflictColumns) {
        final List<String> keys = Arrays.asList(conflictColumns);
        final List<String> all = new ArrayList<>(effectiveColumns());
        all.removeAll(keys);
        return onConflictUpdate(keys, all);
    }

    @Override
    public SqlQuery compile(final SqlDialect dialect) {
        final List<String> effectiveColumns = effectiveColumns();
        Preconditions.checkState(!effectiveColumns.isEmpty(), "An INSERT needs at least one column");

        final SqlRenderContext context = new SqlRenderContext(dialect);
        dialect.renderInsert(context, new InsertSpec(
                table, effectiveColumns, effectiveRows(), conflictColumns, updateColumns));
        return context.toQuery();
    }

    /** @return the column names this insert writes, in order */
    public List<String> columnNames() {
        return List.copyOf(effectiveColumns());
    }

    private List<String> effectiveColumns() {
        return columns.isEmpty() ? new ArrayList<>(pendingRow.keySet()) : columns;
    }

    private List<List<Object>> effectiveRows() {
        if (!rows.isEmpty()) {
            return rows;
        }
        if (pendingRow.isEmpty()) {
            throw new IllegalStateException("An INSERT needs at least one row");
        }
        return List.of(new ArrayList<>(pendingRow.values()));
    }
}
