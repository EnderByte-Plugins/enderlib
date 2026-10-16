package com.enderbyte.enderlib.database.api.query;

import com.enderbyte.enderlib.database.api.util.Preconditions;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Factory methods for {@link Expression}s: columns, aggregates, subqueries and the raw escape
 * hatch.
 *
 * <pre>{@code
 * Query.select(Expressions.column("server_id"), Expressions.count().as("players"))
 *      .from("player_sessions")
 *      .groupBy("server_id");
 * }</pre>
 */
public final class Expressions {

    private static final Expression ALL = context -> context.sql('*');

    private Expressions() {
    }

    /** {@code *} */
    public static Expression all() {
        return ALL;
    }

    /** A column reference, optionally qualified ({@code players.uuid}). */
    public static Expression column(final String reference) {
        Preconditions.requireNonBlank(reference, "reference");
        return context -> context.reference(reference);
    }

    /** {@code COUNT(*)} */
    public static Expression count() {
        return context -> context.sql("COUNT(*)");
    }

    /** {@code COUNT(column)} */
    public static Expression count(final String reference) {
        return function("COUNT", reference, false);
    }

    /** {@code COUNT(DISTINCT column)} */
    public static Expression countDistinct(final String reference) {
        return function("COUNT", reference, true);
    }

    /** {@code SUM(column)} */
    public static Expression sum(final String reference) {
        return function("SUM", reference, false);
    }

    /** {@code AVG(column)} */
    public static Expression avg(final String reference) {
        return function("AVG", reference, false);
    }

    /** {@code MIN(column)} */
    public static Expression min(final String reference) {
        return function("MIN", reference, false);
    }

    /** {@code MAX(column)} */
    public static Expression max(final String reference) {
        return function("MAX", reference, false);
    }

    /** A scalar subquery, for example a per-row aggregate. */
    public static Expression subquery(final SelectQuery query) {
        Objects.requireNonNull(query, "query");
        return context -> {
            context.sql('(');
            query.renderInto(context);
            context.sql(')');
        };
    }

    /** A bound literal value, rendered as a placeholder. */
    public static Expression value(final Object value) {
        return context -> context.parameter(value);
    }

    /**
     * Raw SQL. Use {@code ?} placeholders for values and never build the string from user input.
     *
     * @param sql        expression text
     * @param parameters values to bind
     * @return the expression
     */
    public static Expression raw(final String sql, final Object... parameters) {
        Preconditions.requireNonBlank(sql, "sql");
        final List<Object> values = parameters == null ? List.of() : Arrays.asList(parameters);
        return context -> context.embed(sql, values);
    }

    /** Wraps an expression with an {@code AS} alias. */
    public static Expression aliased(final Expression expression, final String alias) {
        Objects.requireNonNull(expression, "expression");
        Preconditions.requireNonBlank(alias, "alias");
        return context -> {
            expression.render(context);
            context.sql(" AS ").identifier(alias);
        };
    }

    private static Expression function(final String function, final String reference, final boolean distinct) {
        Preconditions.requireNonBlank(reference, "reference");
        return context -> {
            context.sql(function).sql('(');
            if (distinct) {
                context.sql("DISTINCT ");
            }
            context.reference(reference).sql(')');
        };
    }
}
