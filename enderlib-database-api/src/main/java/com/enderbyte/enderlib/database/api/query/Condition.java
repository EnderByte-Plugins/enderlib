package com.enderbyte.enderlib.database.api.query;

/**
 * A boolean predicate that can render itself into a {@code WHERE}, {@code HAVING} or {@code ON}
 * clause.
 *
 * <p>Build instances with {@link Conditions}. Custom implementations are supported, but they must
 * contribute values exclusively through {@link SqlRenderContext#parameter(Object)}.
 */
@FunctionalInterface
public interface Condition {

    /**
     * Renders this predicate.
     *
     * @param context render target for SQL text and bind values
     */
    void render(SqlRenderContext context);

    /** @return {@code true} when this predicate needs parentheses inside a larger expression */
    default boolean isComposite() {
        return false;
    }

    /** @return this predicate AND the given one */
    default Condition and(final Condition other) {
        return Conditions.and(this, other);
    }

    /** @return this predicate OR the given one */
    default Condition or(final Condition other) {
        return Conditions.or(this, other);
    }
}
