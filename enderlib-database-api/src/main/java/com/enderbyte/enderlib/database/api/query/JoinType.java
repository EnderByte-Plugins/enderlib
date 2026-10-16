package com.enderbyte.enderlib.database.api.query;

/** Supported join flavours. */
public enum JoinType {

    INNER("INNER JOIN"),
    LEFT("LEFT JOIN"),
    RIGHT("RIGHT JOIN"),
    FULL("FULL OUTER JOIN"),
    CROSS("CROSS JOIN");

    private final String sql;

    JoinType(final String sql) {
        this.sql = sql;
    }

    public String sql() {
        return sql;
    }
}
