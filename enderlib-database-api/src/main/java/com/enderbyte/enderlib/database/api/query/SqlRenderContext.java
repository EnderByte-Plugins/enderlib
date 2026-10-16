package com.enderbyte.enderlib.database.api.query;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Accumulator used while rendering a statement: collects SQL text on one side and bind values on
 * the other.
 *
 * <p>Because the only way to contribute a value is {@link #parameter(Object)}, which always emits a
 * {@code ?} placeholder, a correctly written {@link Expression} or {@link Condition} cannot inject
 * a value into the SQL text even by accident.
 *
 * <p>Not thread-safe; a context is created per render and discarded.
 */
public final class SqlRenderContext {

    private final SqlDialect dialect;
    private final StringBuilder sql;
    private final List<Object> parameters = new ArrayList<>();

    public SqlRenderContext(final SqlDialect dialect) {
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.sql = new StringBuilder(128);
    }

    public SqlDialect dialect() {
        return dialect;
    }

    /** Appends raw SQL structure (keywords, punctuation). Never pass user input here. */
    public SqlRenderContext sql(final String text) {
        sql.append(text);
        return this;
    }

    /** @see #sql(String) */
    public SqlRenderContext sql(final char c) {
        sql.append(c);
        return this;
    }

    /** Appends a number that the library itself produced, such as a validated limit. */
    public SqlRenderContext number(final int value) {
        sql.append(value);
        return this;
    }

    /** Validates, quotes if needed, and appends an identifier. */
    public SqlRenderContext identifier(final String identifier) {
        sql.append(dialect.identifier(identifier));
        return this;
    }

    /** Validates and appends a possibly qualified column reference. */
    public SqlRenderContext reference(final String reference) {
        sql.append(dialect.reference(reference));
        return this;
    }

    /** Appends a {@code ?} placeholder and records the value to bind. */
    public SqlRenderContext parameter(final Object value) {
        sql.append('?');
        parameters.add(value);
        return this;
    }

    /** Appends {@code ?, ?, ...} for a collection of values. */
    public SqlRenderContext parameters(final Collection<?> values) {
        boolean first = true;
        for (final Object value : values) {
            if (!first) {
                sql.append(", ");
            }
            first = false;
            parameter(value);
        }
        return this;
    }

    /**
     * Appends pre-rendered SQL together with its own parameters. Used by the raw escape hatch and
     * by nested statements that were rendered separately.
     */
    public SqlRenderContext embed(final String rawSql, final List<Object> rawParameters) {
        sql.append(rawSql);
        parameters.addAll(rawParameters);
        return this;
    }

    /** @return the current text length, used to detect whether anything was rendered */
    public int length() {
        return sql.length();
    }

    /** @return the finished statement */
    public SqlQuery toQuery() {
        return SqlQuery.of(sql.toString(), parameters);
    }
}
