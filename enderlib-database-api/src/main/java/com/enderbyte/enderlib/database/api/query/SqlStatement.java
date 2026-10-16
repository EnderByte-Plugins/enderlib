package com.enderbyte.enderlib.database.api.query;

/**
 * Anything that can be turned into a {@link SqlQuery}.
 *
 * <p>Builders stay uncompiled until execution so that the database can compile them with its own
 * {@link SqlDialect}: the same {@code SelectQuery} then renders correct {@code LIMIT}/{@code OFFSET}
 * and identifier quoting on PostgreSQL, MySQL, SQLite and H2 alike.
 */
public interface SqlStatement {

    /**
     * Renders this statement for the given engine.
     *
     * @param dialect the target engine's dialect
     * @return parameterised SQL plus bind values
     */
    SqlQuery compile(SqlDialect dialect);

    /**
     * Renders using the ANSI dialect. Convenient for tests and for engines that need no special
     * handling; execution paths use {@link #compile(SqlDialect)} with the real dialect instead.
     *
     * @return parameterised SQL plus bind values
     */
    default SqlQuery build() {
        return compile(SqlDialect.ansi());
    }
}
