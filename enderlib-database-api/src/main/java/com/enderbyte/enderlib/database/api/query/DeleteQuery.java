package com.enderbyte.enderlib.database.api.query;

import com.enderbyte.enderlib.database.api.util.Preconditions;

/**
 * Fluent {@code DELETE} builder.
 *
 * <pre>{@code
 * SqlQuery query = Query.deleteFrom("players")
 *         .where("uuid", Operator.EQUALS, uuid)
 *         .build();
 * }</pre>
 *
 * <p>As with {@link UpdateQuery}, a {@code DELETE} without a {@code WHERE} clause must be requested
 * explicitly via {@link #allowFullTableDelete()}.
 */
public final class DeleteQuery extends ConditionalStatement<DeleteQuery> {

    private final String table;
    private boolean fullTableDeleteAllowed;

    DeleteQuery(final String table) {
        this.table = Preconditions.requireNonBlank(table, "table");
    }

    /** Permits a delete with no {@code WHERE} clause. */
    public DeleteQuery allowFullTableDelete() {
        this.fullTableDeleteAllowed = true;
        return this;
    }

    @Override
    public SqlQuery compile(final SqlDialect dialect) {
        Preconditions.checkState(hasConditions() || fullTableDeleteAllowed,
                "Refusing to build a DELETE without a WHERE clause; call allowFullTableDelete() if intended");
        final SqlRenderContext context = new SqlRenderContext(dialect);
        context.sql("DELETE FROM ").identifier(table);
        renderWhere(context);
        return context.toQuery();
    }
}
