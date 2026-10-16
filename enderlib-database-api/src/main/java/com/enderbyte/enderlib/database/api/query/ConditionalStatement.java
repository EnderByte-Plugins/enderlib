package com.enderbyte.enderlib.database.api.query;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Shared {@code WHERE} support for {@code SELECT}, {@code UPDATE} and {@code DELETE}.
 *
 * <p>Consecutive {@code where(...)} calls are combined with {@code AND}; use {@link #or} for the
 * alternative, and pass a {@link Conditions#and} / {@link Conditions#or} group for nesting.
 *
 * <p>Builders are mutable and intended to be used by a single thread; the {@link SqlQuery} they
 * produce is immutable and freely shareable.
 *
 * @param <SELF> concrete builder type, for fluent chaining
 */
public abstract class ConditionalStatement<SELF extends ConditionalStatement<SELF>> implements SqlStatement {

    private final List<Condition> conditions = new ArrayList<>(4);
    private final List<String> connectors = new ArrayList<>(4);

    @SuppressWarnings("unchecked")
    protected final SELF self() {
        return (SELF) this;
    }

    /** Adds a predicate, combined with {@code AND}. */
    public SELF where(final Condition condition) {
        return append(condition, "AND");
    }

    /**
     * Adds {@code column <operator> value}, combined with {@code AND}.
     *
     * @param column   column reference
     * @param operator comparison
     * @param value    value, collection or subquery depending on the operator
     * @return this builder
     */
    public SELF where(final String column, final Operator operator, final Object value) {
        return append(Conditions.of(column, operator, value), "AND");
    }

    /** Shorthand for {@code where(column, Operator.EQUALS, value)}. */
    public SELF where(final String column, final Object value) {
        return append(Conditions.equal(column, value), "AND");
    }

    /** Adds a predicate, combined with {@code AND}. Reads better after a first {@code where}. */
    public SELF and(final Condition condition) {
        return append(condition, "AND");
    }

    /** @see #and(Condition) */
    public SELF and(final String column, final Operator operator, final Object value) {
        return append(Conditions.of(column, operator, value), "AND");
    }

    /** Adds a predicate, combined with {@code OR}. */
    public SELF or(final Condition condition) {
        return append(condition, "OR");
    }

    /** @see #or(Condition) */
    public SELF or(final String column, final Operator operator, final Object value) {
        return append(Conditions.of(column, operator, value), "OR");
    }

    /** {@code column IN (...)} */
    public SELF whereIn(final String column, final Collection<?> values) {
        return append(Conditions.in(column, values), "AND");
    }

    /** {@code column NOT IN (...)} */
    public SELF whereNotIn(final String column, final Collection<?> values) {
        return append(Conditions.notIn(column, values), "AND");
    }

    /** {@code column IN (SELECT ...)} */
    public SELF whereIn(final String column, final SelectQuery subquery) {
        return append(Conditions.in(column, subquery), "AND");
    }

    /** {@code column BETWEEN ? AND ?} */
    public SELF whereBetween(final String column, final Object low, final Object high) {
        return append(Conditions.between(column, low, high), "AND");
    }

    /** {@code column LIKE ?} */
    public SELF whereLike(final String column, final String pattern) {
        return append(Conditions.like(column, pattern), "AND");
    }

    /** {@code column IS NULL} */
    public SELF whereNull(final String column) {
        return append(Conditions.isNull(column), "AND");
    }

    /** {@code column IS NOT NULL} */
    public SELF whereNotNull(final String column) {
        return append(Conditions.isNotNull(column), "AND");
    }

    /** {@code EXISTS (SELECT ...)} */
    public SELF whereExists(final SelectQuery subquery) {
        return append(Conditions.exists(subquery), "AND");
    }

    /** @return {@code true} if at least one predicate was added */
    protected final boolean hasConditions() {
        return !conditions.isEmpty();
    }

    /** @return the combined predicate, or {@code null} when none were added */
    protected final Condition whereCondition() {
        return conditions.isEmpty() ? null : Conditions.composite(conditions, connectors);
    }

    /** Renders {@code " WHERE ..."} when predicates are present. */
    protected final void renderWhere(final SqlRenderContext context) {
        final Condition condition = whereCondition();
        if (condition == null) {
            return;
        }
        context.sql(" WHERE ");
        condition.render(context);
    }

    private SELF append(final Condition condition, final String connector) {
        Objects.requireNonNull(condition, "condition");
        if (!conditions.isEmpty()) {
            connectors.add(connector);
        }
        conditions.add(condition);
        return self();
    }
}
