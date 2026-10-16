package com.enderbyte.enderlib.database.api.query;

import com.enderbyte.enderlib.database.api.util.Preconditions;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Factory methods for {@link Condition}s.
 *
 * <p>All value-bearing factories bind their values as parameters. The single exception is
 * {@link #raw(String, Object...)}, which exists for engine-specific predicates the builder does not
 * model; it is the caller's responsibility to keep using {@code ?} placeholders there.
 */
public final class Conditions {

    private Conditions() {
    }

    /**
     * Builds a predicate from a column, an operator and a value.
     *
     * <p>The value's expected shape depends on the operator: a collection or array for
     * {@link Operator#IN}/{@link Operator#NOT_IN}, a two-element collection or array for
     * {@link Operator#BETWEEN}, ignored for the {@code IS NULL} variants, a single value otherwise.
     * A {@link SelectQuery} may be supplied for {@code IN}.
     *
     * @param column   column reference, validated as an identifier
     * @param operator comparison to apply
     * @param value    the value, collection or subquery, as described above
     * @return the predicate
     */
    public static Condition of(final String column, final Operator operator, final Object value) {
        Preconditions.requireNonBlank(column, "column");
        Objects.requireNonNull(operator, "operator");
        return switch (operator) {
            case IS_NULL -> isNull(column);
            case IS_NOT_NULL -> isNotNull(column);
            case IN -> inValue(column, value, false);
            case NOT_IN -> inValue(column, value, true);
            case BETWEEN -> betweenValue(column, value, false);
            case NOT_BETWEEN -> betweenValue(column, value, true);
            default -> new Comparison(column, operator, value);
        };
    }

    /** {@code column = ?} */
    public static Condition equal(final String column, final Object value) {
        return of(column, Operator.EQUALS, value);
    }

    /** {@code column <> ?} */
    public static Condition notEqual(final String column, final Object value) {
        return of(column, Operator.NOT_EQUALS, value);
    }

    /** {@code column > ?} */
    public static Condition greaterThan(final String column, final Object value) {
        return of(column, Operator.GREATER_THAN, value);
    }

    /** {@code column < ?} */
    public static Condition lessThan(final String column, final Object value) {
        return of(column, Operator.LESS_THAN, value);
    }

    /** {@code column LIKE ?}; the pattern (including {@code %}) is bound as a parameter */
    public static Condition like(final String column, final String pattern) {
        return new Comparison(column, Operator.LIKE, pattern);
    }

    /** {@code column NOT LIKE ?} */
    public static Condition notLike(final String column, final String pattern) {
        return new Comparison(column, Operator.NOT_LIKE, pattern);
    }

    /** Compares two columns, for example in a join condition. */
    public static Condition columns(final String leftColumn, final Operator operator, final String rightColumn) {
        return new ColumnComparison(leftColumn, operator, rightColumn);
    }

    /** {@code column IN (?, ?, ...)}; an empty collection renders as a predicate that is never true. */
    public static Condition in(final String column, final Collection<?> values) {
        return new In(column, new ArrayList<>(values), false);
    }

    /** @see #in(String, Collection) */
    public static Condition notIn(final String column, final Collection<?> values) {
        return new In(column, new ArrayList<>(values), true);
    }

    /** {@code column IN (SELECT ...)} */
    public static Condition in(final String column, final SelectQuery subquery) {
        return new InSubquery(column, subquery, false);
    }

    /** {@code column NOT IN (SELECT ...)} */
    public static Condition notIn(final String column, final SelectQuery subquery) {
        return new InSubquery(column, subquery, true);
    }

    /** {@code column BETWEEN ? AND ?} (inclusive) */
    public static Condition between(final String column, final Object low, final Object high) {
        return new Between(column, low, high, false);
    }

    /** {@code column NOT BETWEEN ? AND ?} */
    public static Condition notBetween(final String column, final Object low, final Object high) {
        return new Between(column, low, high, true);
    }

    /** {@code column IS NULL} */
    public static Condition isNull(final String column) {
        return new NullCheck(column, false);
    }

    /** {@code column IS NOT NULL} */
    public static Condition isNotNull(final String column) {
        return new NullCheck(column, true);
    }

    /** {@code EXISTS (SELECT ...)} */
    public static Condition exists(final SelectQuery subquery) {
        return new Exists(subquery, false);
    }

    /** {@code NOT EXISTS (SELECT ...)} */
    public static Condition notExists(final SelectQuery subquery) {
        return new Exists(subquery, true);
    }

    /** Negates a predicate. */
    public static Condition not(final Condition condition) {
        return new Not(condition);
    }

    /** Combines predicates with {@code AND}. */
    public static Condition and(final Condition... conditions) {
        return composite(Arrays.asList(conditions), "AND");
    }

    /** Combines predicates with {@code OR}. */
    public static Condition or(final Condition... conditions) {
        return composite(Arrays.asList(conditions), "OR");
    }

    /** Combines predicates with a single connector. */
    public static Condition composite(final List<Condition> conditions, final String connector) {
        Objects.requireNonNull(conditions, "conditions");
        if (conditions.isEmpty()) {
            throw new IllegalArgumentException("At least one condition is required");
        }
        if (conditions.size() == 1) {
            return conditions.get(0);
        }
        final List<String> connectors = new ArrayList<>(conditions.size() - 1);
        for (int i = 1; i < conditions.size(); i++) {
            connectors.add(connector);
        }
        return new Composite(new ArrayList<>(conditions), connectors);
    }

    /**
     * Combines predicates with an explicit connector per position.
     *
     * <p>Mixed {@code AND}/{@code OR} chains are rendered with explicit parentheses around each
     * {@code AND} run, so the result matches SQL precedence and reads unambiguously.
     *
     * @param conditions predicates, at least one
     * @param connectors {@code "AND"} or {@code "OR"} per gap, exactly {@code conditions - 1} entries
     * @return the combined predicate
     */
    public static Condition composite(final List<Condition> conditions, final List<String> connectors) {
        Objects.requireNonNull(conditions, "conditions");
        Objects.requireNonNull(connectors, "connectors");
        if (conditions.isEmpty()) {
            throw new IllegalArgumentException("At least one condition is required");
        }
        if (conditions.size() == 1) {
            return conditions.get(0);
        }
        if (connectors.size() != conditions.size() - 1) {
            throw new IllegalArgumentException("Expected " + (conditions.size() - 1)
                    + " connectors but got " + connectors.size());
        }
        return new Composite(new ArrayList<>(conditions), new ArrayList<>(connectors));
    }

    /**
     * Escape hatch for predicates the builder does not model, such as PostgreSQL {@code @>} or a
     * function call.
     *
     * <p>Use {@code ?} placeholders for every value. Never build this string from user input.
     *
     * @param sql        predicate text with {@code ?} placeholders
     * @param parameters values to bind
     * @return the predicate
     */
    public static Condition raw(final String sql, final Object... parameters) {
        Preconditions.requireNonBlank(sql, "sql");
        return new Raw(sql, parameters == null ? List.of() : Arrays.asList(parameters));
    }

    private static Condition inValue(final String column, final Object value, final boolean negated) {
        if (value instanceof SelectQuery subquery) {
            return new InSubquery(column, subquery, negated);
        }
        return new In(column, toList(value, Operator.IN), negated);
    }

    private static Condition betweenValue(final String column, final Object value, final boolean negated) {
        final List<Object> bounds = toList(value, Operator.BETWEEN);
        if (bounds.size() != 2) {
            throw new IllegalArgumentException("BETWEEN requires exactly two bounds but got " + bounds.size());
        }
        return new Between(column, bounds.get(0), bounds.get(1), negated);
    }

    private static List<Object> toList(final Object value, final Operator operator) {
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        if (value != null && value.getClass().isArray()) {
            final int length = Array.getLength(value);
            final List<Object> values = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                values.add(Array.get(value, i));
            }
            return values;
        }
        throw new IllegalArgumentException(operator.sql() + " requires a collection or array value");
    }

    private static void renderChild(final Condition condition, final SqlRenderContext context) {
        if (condition.isComposite()) {
            context.sql('(');
            condition.render(context);
            context.sql(')');
        } else {
            condition.render(context);
        }
    }

    private record Comparison(String column, Operator operator, Object value) implements Condition {

        private Comparison {
            Preconditions.requireNonBlank(column, "column");
        }

        @Override
        public void render(final SqlRenderContext context) {
            context.reference(column).sql(' ').sql(operator.sql()).sql(' ').parameter(value);
        }
    }

    private record ColumnComparison(String left, Operator operator, String right) implements Condition {

        @Override
        public void render(final SqlRenderContext context) {
            context.reference(left).sql(' ').sql(operator.sql()).sql(' ').reference(right);
        }
    }

    private record In(String column, List<Object> values, boolean negated) implements Condition {

        @Override
        public void render(final SqlRenderContext context) {
            if (values.isEmpty()) {
                // "IN ()" is a syntax error everywhere. An empty set matches nothing, and its
                // negation matches everything; render constants so callers need no special case.
                context.sql(negated ? "1 = 1" : "1 = 0");
                return;
            }
            context.reference(column).sql(negated ? " NOT IN (" : " IN (").parameters(values).sql(')');
        }
    }

    private record InSubquery(String column, SelectQuery subquery, boolean negated) implements Condition {

        @Override
        public void render(final SqlRenderContext context) {
            context.reference(column).sql(negated ? " NOT IN (" : " IN (");
            subquery.renderInto(context);
            context.sql(')');
        }
    }

    private record Between(String column, Object low, Object high, boolean negated) implements Condition {

        @Override
        public void render(final SqlRenderContext context) {
            context.reference(column)
                    .sql(negated ? " NOT BETWEEN " : " BETWEEN ")
                    .parameter(low)
                    .sql(" AND ")
                    .parameter(high);
        }
    }

    private record NullCheck(String column, boolean negated) implements Condition {

        @Override
        public void render(final SqlRenderContext context) {
            context.reference(column).sql(negated ? " IS NOT NULL" : " IS NULL");
        }
    }

    private record Exists(SelectQuery subquery, boolean negated) implements Condition {

        @Override
        public void render(final SqlRenderContext context) {
            context.sql(negated ? "NOT EXISTS (" : "EXISTS (");
            subquery.renderInto(context);
            context.sql(')');
        }
    }

    private record Not(Condition condition) implements Condition {

        @Override
        public void render(final SqlRenderContext context) {
            context.sql("NOT (");
            condition.render(context);
            context.sql(')');
        }
    }

    private record Raw(String sql, List<Object> parameters) implements Condition {

        @Override
        public void render(final SqlRenderContext context) {
            context.embed(sql, parameters);
        }

        @Override
        public boolean isComposite() {
            return true;
        }
    }

    private record Composite(List<Condition> conditions, List<String> connectors) implements Condition {

        @Override
        public boolean isComposite() {
            return true;
        }

        @Override
        public void render(final SqlRenderContext context) {
            if (isUniform()) {
                for (int i = 0; i < conditions.size(); i++) {
                    if (i > 0) {
                        context.sql(' ').sql(connectors.get(i - 1)).sql(' ');
                    }
                    renderChild(conditions.get(i), context);
                }
                return;
            }
            // Mixed connectors: group AND runs so precedence is explicit in the generated SQL.
            final List<List<Condition>> groups = groupAndRuns();
            for (int g = 0; g < groups.size(); g++) {
                if (g > 0) {
                    context.sql(" OR ");
                }
                final List<Condition> group = groups.get(g);
                final boolean parenthesize = group.size() > 1;
                if (parenthesize) {
                    context.sql('(');
                }
                for (int i = 0; i < group.size(); i++) {
                    if (i > 0) {
                        context.sql(" AND ");
                    }
                    renderChild(group.get(i), context);
                }
                if (parenthesize) {
                    context.sql(')');
                }
            }
        }

        private boolean isUniform() {
            final String first = connectors.get(0);
            for (final String connector : connectors) {
                if (!first.equals(connector)) {
                    return false;
                }
            }
            return true;
        }

        private List<List<Condition>> groupAndRuns() {
            final List<List<Condition>> groups = new ArrayList<>();
            List<Condition> current = new ArrayList<>();
            current.add(conditions.get(0));
            for (int i = 1; i < conditions.size(); i++) {
                if ("AND".equals(connectors.get(i - 1))) {
                    current.add(conditions.get(i));
                } else {
                    groups.add(current);
                    current = new ArrayList<>();
                    current.add(conditions.get(i));
                }
            }
            groups.add(current);
            return groups;
        }
    }
}
