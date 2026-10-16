package com.enderbyte.enderlib.database.api.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Entry point of the query builder.
 *
 * <pre>{@code
 * Query.select("uuid", "name").from("players").where("uuid", Operator.EQUALS, uuid);
 * Query.insertInto("players").value("uuid", uuid).value("name", name);
 * Query.update("players").set("balance", balance).where("uuid", Operator.EQUALS, uuid);
 * Query.deleteFrom("players").where("uuid", Operator.EQUALS, uuid);
 * }</pre>
 *
 * <p>Every builder produces a {@link SqlQuery} whose values are bound as parameters. There is no
 * API that concatenates a value into SQL text.
 */
public final class Query {

    private Query() {
    }

    /**
     * Starts a {@code SELECT} over the given columns. Pass no arguments for {@code SELECT *}.
     *
     * @param columns column references, optionally qualified
     * @return the select builder
     */
    public static SelectQuery select(final String... columns) {
        final List<Expression> expressions = new ArrayList<>(columns.length);
        for (final String column : columns) {
            expressions.add(Expressions.column(column));
        }
        return new SelectQuery(expressions);
    }

    /** Starts a {@code SELECT} over expressions such as aggregates or aliased columns. */
    public static SelectQuery select(final Expression... expressions) {
        return new SelectQuery(Arrays.asList(expressions));
    }

    /** {@code SELECT * FROM table} */
    public static SelectQuery selectFrom(final String table) {
        return new SelectQuery(List.of()).from(table);
    }

    /** {@code SELECT COUNT(*) FROM table} */
    public static SelectQuery selectCount(final String table) {
        return new SelectQuery(List.of(Expressions.count())).from(table);
    }

    /** Starts an {@code INSERT INTO table}. */
    public static InsertQuery insertInto(final String table) {
        return new InsertQuery(table);
    }

    /** Starts an {@code UPDATE table}. */
    public static UpdateQuery update(final String table) {
        return new UpdateQuery(table);
    }

    /** Starts a {@code DELETE FROM table}. */
    public static DeleteQuery deleteFrom(final String table) {
        return new DeleteQuery(table);
    }

    /**
     * Wraps hand-written SQL for cases the builder does not cover (window functions, CTEs, engine
     * specific syntax).
     *
     * @param sql        statement text using {@code ?} placeholders
     * @param parameters values to bind
     * @return the compiled statement
     */
    public static SqlQuery raw(final String sql, final Object... parameters) {
        return SqlQuery.of(sql, parameters);
    }
}
