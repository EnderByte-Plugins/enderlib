package com.enderbyte.enderlib.database.api.query;

/**
 * Comparison operators accepted by the {@code where(...)} methods.
 *
 * <p>Operators that do not take exactly one value ({@link #IN}, {@link #BETWEEN},
 * {@link #IS_NULL}, ...) are also available as dedicated builder methods, which is usually clearer.
 */
public enum Operator {

    EQUALS("="),
    NOT_EQUALS("<>"),
    GREATER_THAN(">"),
    GREATER_THAN_OR_EQUAL(">="),
    LESS_THAN("<"),
    LESS_THAN_OR_EQUAL("<="),
    /** Pattern match; the pattern is bound as a parameter, wildcards included. */
    LIKE("LIKE"),
    NOT_LIKE("NOT LIKE"),
    /** Expects a {@link java.util.Collection}, an array or a {@link SelectQuery}. */
    IN("IN"),
    NOT_IN("NOT IN"),
    /** Expects a two-element collection or array: the inclusive bounds. */
    BETWEEN("BETWEEN"),
    NOT_BETWEEN("NOT BETWEEN"),
    /** The value argument is ignored. */
    IS_NULL("IS NULL"),
    /** The value argument is ignored. */
    IS_NOT_NULL("IS NOT NULL");

    private final String sql;

    Operator(final String sql) {
        this.sql = sql;
    }

    /** @return the SQL text of this operator */
    public String sql() {
        return sql;
    }

    /** @return {@code true} if the operator is followed by a single bound value */
    public boolean isSimple() {
        return switch (this) {
            case IN, NOT_IN, BETWEEN, NOT_BETWEEN, IS_NULL, IS_NOT_NULL -> false;
            default -> true;
        };
    }
}
