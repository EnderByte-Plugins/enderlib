package com.enderbyte.enderlib.database.api.query;

/**
 * Something that can appear in a {@code SELECT} list, {@code GROUP BY} or {@code ORDER BY}: a
 * column reference, an aggregate, a subquery or raw SQL.
 *
 * <p>Build instances with {@link Expressions}.
 */
public interface Expression {

    /**
     * Renders this expression.
     *
     * @param context render target for SQL text and bind values
     */
    void render(SqlRenderContext context);

    /**
     * @param alias result column name, validated as an identifier
     * @return this expression with an {@code AS} alias
     */
    default Expression as(final String alias) {
        return Expressions.aliased(this, alias);
    }
}
