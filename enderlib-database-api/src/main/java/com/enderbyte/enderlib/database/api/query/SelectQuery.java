package com.enderbyte.enderlib.database.api.query;

import com.enderbyte.enderlib.database.api.util.Preconditions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Fluent {@code SELECT} builder.
 *
 * <pre>{@code
 * SqlQuery query = Query.select("uuid", "name", "balance")
 *         .from("players")
 *         .where("uuid", Operator.EQUALS, uuid)
 *         .and("balance", Operator.GREATER_THAN, 0)
 *         .orderBy("balance", SortDirection.DESCENDING)
 *         .limit(10)
 *         .build();
 * }</pre>
 */
public final class SelectQuery extends ConditionalStatement<SelectQuery> {

    private final List<Expression> columns = new ArrayList<>(8);
    private final List<Join> joins = new ArrayList<>(2);
    private final List<Expression> groupBy = new ArrayList<>(2);
    private final List<Order> orderBy = new ArrayList<>(2);
    private boolean distinct;
    private String table;
    private String tableAlias;
    private Condition having;
    private int limit = -1;
    private int offset = -1;
    private boolean forUpdate;
    private boolean skipLocked;

    SelectQuery(final List<Expression> columns) {
        this.columns.addAll(columns);
    }

    /** Adds {@code DISTINCT}. */
    public SelectQuery distinct() {
        this.distinct = true;
        return this;
    }

    /** Adds columns to the projection. */
    public SelectQuery column(final String... references) {
        for (final String reference : references) {
            columns.add(Expressions.column(reference));
        }
        return this;
    }

    /** Adds expressions (aggregates, subqueries, aliases) to the projection. */
    public SelectQuery column(final Expression... expressions) {
        columns.addAll(Arrays.asList(expressions));
        return this;
    }

    /** Sets the source table. */
    public SelectQuery from(final String table) {
        this.table = Preconditions.requireNonBlank(table, "table");
        return this;
    }

    /**
     * Sets the source table with an alias, enabling qualified references such as {@code p.uuid}.
     */
    public SelectQuery from(final String table, final String alias) {
        this.table = Preconditions.requireNonBlank(table, "table");
        this.tableAlias = Preconditions.requireNonBlank(alias, "alias");
        return this;
    }

    /** {@code INNER JOIN table ON condition} */
    public SelectQuery join(final String table, final Condition on) {
        return join(JoinType.INNER, table, null, on);
    }

    /** {@code LEFT JOIN table ON condition} */
    public SelectQuery leftJoin(final String table, final Condition on) {
        return join(JoinType.LEFT, table, null, on);
    }

    /** {@code RIGHT JOIN table ON condition} */
    public SelectQuery rightJoin(final String table, final Condition on) {
        return join(JoinType.RIGHT, table, null, on);
    }

    /** {@code INNER JOIN table ON condition} */
    public SelectQuery innerJoin(final String table, final Condition on) {
        return join(JoinType.INNER, table, null, on);
    }

    /** {@code FULL OUTER JOIN table ON condition} */
    public SelectQuery fullJoin(final String table, final Condition on) {
        return join(JoinType.FULL, table, null, on);
    }

    /**
     * Adds a join.
     *
     * @param type  join flavour
     * @param table joined table
     * @param alias optional alias, may be {@code null}
     * @param on    join predicate; {@code null} only for {@link JoinType#CROSS}
     * @return this builder
     */
    public SelectQuery join(final JoinType type, final String table, final String alias, final Condition on) {
        Objects.requireNonNull(type, "type");
        Preconditions.requireNonBlank(table, "table");
        if (on == null && type != JoinType.CROSS) {
            throw new IllegalArgumentException(type.sql() + " requires an ON condition");
        }
        joins.add(new Join(type, table, alias, on));
        return this;
    }

    /** {@code GROUP BY} on the given columns. */
    public SelectQuery groupBy(final String... references) {
        for (final String reference : references) {
            groupBy.add(Expressions.column(reference));
        }
        return this;
    }

    /** {@code GROUP BY} on the given expressions. */
    public SelectQuery groupBy(final Expression... expressions) {
        groupBy.addAll(Arrays.asList(expressions));
        return this;
    }

    /** Adds a {@code HAVING} predicate, combined with {@code AND}. */
    public SelectQuery having(final Condition condition) {
        Objects.requireNonNull(condition, "condition");
        this.having = having == null ? condition : Conditions.and(having, condition);
        return this;
    }

    /** Ascending {@code ORDER BY}. */
    public SelectQuery orderBy(final String reference) {
        return orderBy(reference, SortDirection.ASCENDING);
    }

    /** {@code ORDER BY} with an explicit direction. */
    public SelectQuery orderBy(final String reference, final SortDirection direction) {
        orderBy.add(new Order(Expressions.column(reference), direction));
        return this;
    }

    /** {@code ORDER BY} an expression, for example an aggregate alias. */
    public SelectQuery orderBy(final Expression expression, final SortDirection direction) {
        orderBy.add(new Order(expression, direction));
        return this;
    }

    /** Caps the number of returned rows. Always paginate large tables. */
    public SelectQuery limit(final int limit) {
        this.limit = Preconditions.requireNonNegative(limit, "limit");
        return this;
    }

    /** Skips rows; requires a {@link #limit(int)} on MySQL, MariaDB and SQLite. */
    public SelectQuery offset(final int offset) {
        this.offset = Preconditions.requireNonNegative(offset, "offset");
        return this;
    }

    /**
     * Adds {@code FOR UPDATE}, taking a row lock for the duration of the surrounding transaction.
     *
     * <p>This is the pessimistic answer to two servers editing the same player at once. It only has
     * meaning inside a transaction, and it is ignored by SQLite, which locks at file level anyway.
     */
    public SelectQuery forUpdate() {
        this.forUpdate = true;
        return this;
    }

    /**
     * Adds {@code FOR UPDATE SKIP LOCKED} where supported, the standard way to hand rows out to
     * exactly one consumer — useful for cross-server job queues.
     */
    public SelectQuery forUpdateSkipLocked() {
        this.forUpdate = true;
        this.skipLocked = true;
        return this;
    }

    @Override
    public SqlQuery compile(final SqlDialect dialect) {
        final SqlRenderContext context = new SqlRenderContext(dialect);
        renderInto(context);
        return context.toQuery();
    }

    /**
     * Renders this query into an existing context. Used for subqueries so that their parameters are
     * collected in the right order relative to the outer statement.
     *
     * @param context the enclosing render context
     */
    public void renderInto(final SqlRenderContext context) {
        Preconditions.checkState(table != null, "from(table) must be called before building a SELECT");
        context.sql("SELECT ");
        if (distinct) {
            context.sql("DISTINCT ");
        }
        if (columns.isEmpty()) {
            context.sql('*');
        } else {
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    context.sql(", ");
                }
                columns.get(i).render(context);
            }
        }
        context.sql(" FROM ").identifier(table);
        if (tableAlias != null) {
            context.sql(' ').identifier(tableAlias);
        }
        for (final Join join : joins) {
            join.render(context);
        }
        renderWhere(context);
        if (!groupBy.isEmpty()) {
            context.sql(" GROUP BY ");
            for (int i = 0; i < groupBy.size(); i++) {
                if (i > 0) {
                    context.sql(", ");
                }
                groupBy.get(i).render(context);
            }
        }
        if (having != null) {
            context.sql(" HAVING ");
            having.render(context);
        }
        if (!orderBy.isEmpty()) {
            context.sql(" ORDER BY ");
            for (int i = 0; i < orderBy.size(); i++) {
                if (i > 0) {
                    context.sql(", ");
                }
                orderBy.get(i).render(context);
            }
        }
        final StringBuilder pagination = new StringBuilder(24);
        context.dialect().appendPagination(pagination, limit, offset);
        if (pagination.length() > 0) {
            context.sql(pagination.toString());
        }
        if (forUpdate) {
            context.sql(context.dialect().rowLockClause(skipLocked));
        }
    }

    private record Join(JoinType type, String table, String alias, Condition on) {

        void render(final SqlRenderContext context) {
            context.sql(' ').sql(type.sql()).sql(' ').identifier(table);
            if (alias != null) {
                context.sql(' ').identifier(alias);
            }
            if (on != null) {
                context.sql(" ON ");
                on.render(context);
            }
        }
    }

    private record Order(Expression expression, SortDirection direction) {

        void render(final SqlRenderContext context) {
            expression.render(context);
            context.sql(' ').sql(direction.sql());
        }
    }
}
