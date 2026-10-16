package com.enderbyte.enderlib.database.api.query;

import com.enderbyte.enderlib.database.api.util.Preconditions;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fluent {@code UPDATE} builder.
 *
 * <pre>{@code
 * SqlQuery query = Query.update("players")
 *         .set("name", name)
 *         .set("balance", balance)
 *         .where("uuid", Operator.EQUALS, uuid)
 *         .build();
 * }</pre>
 *
 * <p>An update without a {@code WHERE} clause is rejected: a full-table update is almost never
 * intended, and on a shared network database it is unrecoverable. Call {@link #allowFullTableUpdate()}
 * if you really mean it.
 */
public final class UpdateQuery extends ConditionalStatement<UpdateQuery> {

    private final String table;
    private final Map<String, Object> assignments = new LinkedHashMap<>(8);
    private final Map<String, Expression> expressionAssignments = new LinkedHashMap<>(2);
    private boolean fullTableUpdateAllowed;

    UpdateQuery(final String table) {
        this.table = Preconditions.requireNonBlank(table, "table");
    }

    /**
     * Assigns a value.
     *
     * @param column column name
     * @param value  value, bound as a parameter
     * @return this builder
     */
    public UpdateQuery set(final String column, final Object value) {
        Preconditions.requireNonBlank(column, "column");
        assignments.put(column, value);
        return this;
    }

    /** Assigns several values at once. */
    public UpdateQuery set(final Map<String, ?> values) {
        values.forEach(this::set);
        return this;
    }

    /**
     * Assigns the result of an expression, for example an atomic increment:
     * {@code set("balance", Expressions.raw("balance + ?", amount))}.
     *
     * <p>Prefer this over read-modify-write whenever several servers may touch the same row: the
     * engine applies the delta under its own row lock, so no update is lost.
     *
     * @param column     column name
     * @param expression value expression
     * @return this builder
     */
    public UpdateQuery set(final String column, final Expression expression) {
        Preconditions.requireNonBlank(column, "column");
        expressionAssignments.put(column, expression);
        return this;
    }

    /** Permits an update with no {@code WHERE} clause. */
    public UpdateQuery allowFullTableUpdate() {
        this.fullTableUpdateAllowed = true;
        return this;
    }

    @Override
    public SqlQuery compile(final SqlDialect dialect) {
        Preconditions.checkState(!assignments.isEmpty() || !expressionAssignments.isEmpty(),
                "An UPDATE needs at least one set(...) assignment");
        Preconditions.checkState(hasConditions() || fullTableUpdateAllowed,
                "Refusing to build an UPDATE without a WHERE clause; call allowFullTableUpdate() if intended");

        final SqlRenderContext context = new SqlRenderContext(dialect);
        context.sql("UPDATE ").identifier(table).sql(" SET ");
        boolean first = true;
        for (final Map.Entry<String, Object> entry : assignments.entrySet()) {
            if (!first) {
                context.sql(", ");
            }
            first = false;
            context.identifier(entry.getKey()).sql(" = ").parameter(entry.getValue());
        }
        for (final Map.Entry<String, Expression> entry : expressionAssignments.entrySet()) {
            if (!first) {
                context.sql(", ");
            }
            first = false;
            context.identifier(entry.getKey()).sql(" = ");
            entry.getValue().render(context);
        }
        renderWhere(context);
        return context.toQuery();
    }
}
