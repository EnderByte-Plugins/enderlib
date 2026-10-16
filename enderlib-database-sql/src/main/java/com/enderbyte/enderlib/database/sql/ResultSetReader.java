package com.enderbyte.enderlib.database.sql;

import com.enderbyte.enderlib.database.api.mapping.ConverterRegistry;
import com.enderbyte.enderlib.database.api.result.QueryResult;
import com.enderbyte.enderlib.database.api.result.Row;
import com.enderbyte.enderlib.database.core.result.ColumnIndex;
import com.enderbyte.enderlib.database.core.result.MaterializedQueryResult;
import com.enderbyte.enderlib.database.core.result.MaterializedRow;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a {@link ResultSet} into detached rows.
 *
 * <p>Everything is read before the statement and connection are closed, which is what makes the
 * asynchronous API safe: the caller receives plain objects and cannot accidentally hold a
 * connection open by keeping the result around.
 */
final class ResultSetReader {

    private ResultSetReader() {
    }

    /**
     * @param resultSet  the open result set
     * @param converters registry used by the typed getters on each row
     * @return the materialised result
     * @throws SQLException if reading fails
     */
    static QueryResult read(final ResultSet resultSet, final ConverterRegistry converters) throws SQLException {
        final ResultSetMetaData metaData = resultSet.getMetaData();
        final int columnCount = metaData.getColumnCount();
        final String[] labels = new String[columnCount];
        for (int i = 0; i < columnCount; i++) {
            // getColumnLabel honours "AS" aliases; getColumnName would ignore them.
            labels[i] = metaData.getColumnLabel(i + 1);
        }
        final ColumnIndex index = new ColumnIndex(labels);

        final List<Row> rows = new ArrayList<>();
        while (resultSet.next()) {
            final Object[] values = new Object[columnCount];
            for (int i = 0; i < columnCount; i++) {
                values[i] = resultSet.getObject(i + 1);
            }
            rows.add(new MaterializedRow(index, values, converters));
        }
        return new MaterializedQueryResult(rows, List.of(), converters);
    }

    /**
     * Reads the keys a driver generated for an insert.
     *
     * @param resultSet  generated-keys result set
     * @param converters registry, kept for consistency with the row API
     * @return the keys, in insertion order
     * @throws SQLException if reading fails
     */
    static List<Object> readGeneratedKeys(final ResultSet resultSet, final ConverterRegistry converters)
            throws SQLException {
        final List<Object> keys = new ArrayList<>(1);
        while (resultSet.next()) {
            keys.add(resultSet.getObject(1));
        }
        return keys;
    }
}
