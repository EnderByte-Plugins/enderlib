package com.enderbyte.enderlib.database.api.query;

import com.enderbyte.enderlib.database.api.util.Preconditions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A compiled, parameterised statement: SQL text containing only {@code ?} placeholders, plus the
 * values to bind.
 *
 * <p>This is the only shape in which SQL reaches the driver. Values never appear in the text, so
 * there is no string-concatenation path a caller could accidentally use.
 *
 * <p>Immutable and safe to cache or share across threads.
 */
public final class SqlQuery implements SqlStatement {

    private final String sql;
    private final List<Object> parameters;

    private SqlQuery(final String sql, final List<Object> parameters) {
        this.sql = sql;
        this.parameters = parameters;
    }

    /**
     * Wraps hand-written SQL. The caller is responsible for using {@code ?} placeholders for every
     * value; this is the one place where the library trusts the caller with raw SQL text.
     *
     * @param sql        statement text with {@code ?} placeholders
     * @param parameters values to bind, in order
     * @return the compiled statement
     */
    public static SqlQuery of(final String sql, final Object... parameters) {
        Preconditions.requireNonBlank(sql, "sql");
        final List<Object> values = parameters == null || parameters.length == 0
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(java.util.Arrays.asList(parameters)));
        return new SqlQuery(sql, values);
    }

    /** @see #of(String, Object...) */
    public static SqlQuery of(final String sql, final List<Object> parameters) {
        Preconditions.requireNonBlank(sql, "sql");
        final List<Object> values = parameters == null || parameters.isEmpty()
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(parameters));
        return new SqlQuery(sql, values);
    }

    /** @return the statement text, containing {@code ?} placeholders only */
    public String sql() {
        return sql;
    }

    /** @return the bind values in placeholder order; immutable */
    public List<Object> parameters() {
        return parameters;
    }

    public int parameterCount() {
        return parameters.size();
    }

    @Override
    public SqlQuery compile(final SqlDialect dialect) {
        return this;
    }

    /**
     * @return the SQL text plus the <em>number</em> of parameters. Parameter values are never
     *         included: they routinely contain player names, IP addresses and other data that must
     *         not be written to a log.
     */
    @Override
    public String toString() {
        return sql + " [" + parameters.size() + " parameter(s)]";
    }
}
