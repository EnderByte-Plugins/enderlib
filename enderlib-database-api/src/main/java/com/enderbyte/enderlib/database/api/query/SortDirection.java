package com.enderbyte.enderlib.database.api.query;

/** Sort direction for {@code ORDER BY}. */
public enum SortDirection {

    ASCENDING("ASC"),
    DESCENDING("DESC");

    private final String sql;

    SortDirection(final String sql) {
        this.sql = sql;
    }

    public String sql() {
        return sql;
    }
}
