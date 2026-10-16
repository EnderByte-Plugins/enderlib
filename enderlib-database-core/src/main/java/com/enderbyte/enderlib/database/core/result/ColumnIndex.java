package com.enderbyte.enderlib.database.core.result;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Column name to position mapping, built once per result and shared by every row.
 *
 * <p>Sharing matters at scale: a thousand-row result allocates one index, not a thousand maps.
 * Lookup is case-insensitive because engines disagree about the case they report — PostgreSQL
 * lowercases, Oracle-style engines uppercase, MySQL preserves.
 */
public final class ColumnIndex {

    private final Map<String, Integer> positions;
    private final Set<String> names;

    /**
     * @param columnNames column labels in selection order
     */
    public ColumnIndex(final String[] columnNames) {
        final Map<String, Integer> byName = new LinkedHashMap<>(columnNames.length * 2);
        final Set<String> ordered = new LinkedHashSet<>(columnNames.length * 2);
        for (int i = 0; i < columnNames.length; i++) {
            final String name = columnNames[i];
            ordered.add(name);
            // First occurrence wins, matching JDBC's own behaviour for duplicate labels.
            byName.putIfAbsent(normalize(name), i);
        }
        this.positions = Collections.unmodifiableMap(byName);
        this.names = Collections.unmodifiableSet(ordered);
    }

    /**
     * @param column column name or alias
     * @return the 0-based position, or {@code -1} when absent
     */
    public int position(final String column) {
        final Integer index = positions.get(normalize(column));
        return index == null ? -1 : index;
    }

    /** @return the column names in selection order */
    public Set<String> names() {
        return names;
    }

    /** @return the number of columns */
    public int size() {
        return names.size();
    }

    private static String normalize(final String column) {
        return column.toLowerCase(Locale.ROOT);
    }
}
