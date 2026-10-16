package com.enderbyte.enderlib.database.api.util;

import java.util.Locale;
import java.util.Set;

/**
 * Validation of SQL identifiers (table, column, index and alias names).
 *
 * <p>This is the library's second line of defence against SQL injection. Values are <em>always</em>
 * bound as statement parameters and never reach this class; identifiers, however, cannot be
 * parameterised in SQL, so anything that ends up in an identifier position is validated against a
 * strict allow-list before it is rendered.
 *
 * <p>Accepted form: an ASCII letter or underscore followed by up to 127 letters, digits,
 * underscores or dollar signs. Qualified names ({@code table.column}) are validated part by part.
 * Everything else — quotes, whitespace, semicolons, comment markers, Unicode homoglyphs — is
 * rejected outright.
 */
public final class Identifiers {

    private static final int MAX_LENGTH = 128;

    /**
     * Words that must be quoted when used as an identifier. Kept intentionally small: it only needs
     * to cover names that realistically appear in plugin schemas.
     */
    private static final Set<String> RESERVED = Set.of(
            "order", "group", "key", "value", "values", "desc", "asc", "select", "from", "where",
            "table", "index", "user", "left", "right", "join", "inner", "outer", "limit", "offset",
            "check", "default", "primary", "foreign", "references", "constraint", "column", "int",
            "integer", "double", "float", "decimal", "date", "time", "timestamp", "level", "rank",
            "row", "rows", "grant", "range", "start", "end", "current", "natural", "using", "when",
            "case", "then", "else", "null", "true", "false", "and", "or", "not", "in", "is", "like",
            "between", "exists", "distinct", "having", "union", "all", "as", "on", "by", "into",
            "insert", "update", "delete", "set", "create", "drop", "alter", "add");

    private Identifiers() {
    }

    /**
     * Validates a single, unqualified identifier.
     *
     * @param identifier candidate name
     * @param role       what the name is used for, used in the error message ("table", "column"...)
     * @return the identifier unchanged
     * @throws IllegalArgumentException if the identifier is not a plain SQL name
     */
    public static String requireValid(final String identifier, final String role) {
        if (identifier == null || identifier.isEmpty()) {
            throw new IllegalArgumentException("A " + role + " name must not be null or empty");
        }
        if (identifier.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("The " + role + " name '" + identifier
                    + "' exceeds " + MAX_LENGTH + " characters");
        }
        final char first = identifier.charAt(0);
        if (!isLetter(first) && first != '_') {
            throw new IllegalArgumentException("Invalid " + role + " name '" + identifier
                    + "': must start with a letter or underscore");
        }
        for (int i = 1; i < identifier.length(); i++) {
            final char c = identifier.charAt(i);
            if (!isLetter(c) && !isDigit(c) && c != '_' && c != '$') {
                throw new IllegalArgumentException("Invalid " + role + " name '" + identifier
                        + "': only letters, digits, '_' and '$' are allowed. Values must be passed as"
                        + " parameters, not embedded in identifiers.");
            }
        }
        return identifier;
    }

    /**
     * Validates a possibly qualified column reference such as {@code players.uuid}, {@code uuid},
     * {@code *} or {@code players.*}.
     *
     * @param reference candidate reference
     * @return the reference unchanged
     * @throws IllegalArgumentException if any part is not a plain SQL name
     */
    public static String requireValidReference(final String reference) {
        if (reference == null || reference.isEmpty()) {
            throw new IllegalArgumentException("A column reference must not be null or empty");
        }
        if ("*".equals(reference)) {
            return reference;
        }
        int start = 0;
        int parts = 0;
        for (int i = 0; i <= reference.length(); i++) {
            if (i == reference.length() || reference.charAt(i) == '.') {
                final String part = reference.substring(start, i);
                if (i == reference.length() && "*".equals(part) && parts > 0) {
                    return reference;
                }
                requireValid(part, "column");
                parts++;
                start = i + 1;
            }
        }
        if (parts > 3) {
            throw new IllegalArgumentException("Column reference '" + reference + "' has too many parts");
        }
        return reference;
    }

    /** @return {@code true} if the identifier collides with a SQL keyword and should be quoted */
    public static boolean isReserved(final String identifier) {
        return RESERVED.contains(identifier.toLowerCase(Locale.ROOT));
    }

    private static boolean isLetter(final char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isDigit(final char c) {
        return c >= '0' && c <= '9';
    }
}
