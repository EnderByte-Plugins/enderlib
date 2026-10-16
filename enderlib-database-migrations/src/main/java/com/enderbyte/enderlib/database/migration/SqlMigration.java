package com.enderbyte.enderlib.database.migration;

import com.enderbyte.enderlib.database.api.DatabaseConnection;
import com.enderbyte.enderlib.database.api.migration.Migration;

import java.util.List;

/**
 * A migration made of SQL statements, the form virtually every migration takes.
 *
 * <p>The script is split on semicolons so one file can create a table and its indexes together.
 * Splitting is string-level and therefore deliberately conservative: semicolons inside quoted
 * literals and inside {@code $$ ... $$} blocks are left alone, because PostgreSQL function bodies
 * are full of them.
 */
public final class SqlMigration implements Migration {

    private final int version;
    private final String description;
    private final String script;
    private final String checksum;

    /**
     * @param version     migration version
     * @param description short description
     * @param script      one or more SQL statements separated by semicolons
     */
    public SqlMigration(final int version, final String description, final String script) {
        this.version = version;
        this.description = description;
        this.script = script;
        this.checksum = Checksums.of(script);
    }

    @Override
    public int version() {
        return version;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public String checksum() {
        return checksum;
    }

    /** @return the raw script */
    public String script() {
        return script;
    }

    @Override
    public void apply(final DatabaseConnection connection) {
        for (final String statement : split(script)) {
            connection.execute(statement);
        }
    }

    @Override
    public String toString() {
        return 'V' + Integer.toString(version) + "__" + description;
    }

    /**
     * Splits a script into statements.
     *
     * @param script the script
     * @return the statements, comments and blank entries removed
     */
    static List<String> split(final String script) {
        final List<String> statements = new java.util.ArrayList<>(4);
        final StringBuilder current = new StringBuilder(128);
        char quote = 0;
        boolean inDollarBlock = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        for (int i = 0; i < script.length(); i++) {
            final char c = script.charAt(i);
            final char next = i + 1 < script.length() ? script.charAt(i + 1) : 0;

            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                    current.append(c);
                }
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (quote == 0 && !inDollarBlock) {
                if (c == '-' && next == '-') {
                    inLineComment = true;
                    continue;
                }
                if (c == '/' && next == '*') {
                    inBlockComment = true;
                    i++;
                    continue;
                }
                if (c == '$' && next == '$') {
                    inDollarBlock = true;
                    current.append(c).append(next);
                    i++;
                    continue;
                }
                if (c == '\'' || c == '"') {
                    quote = c;
                } else if (c == ';') {
                    addIfNotBlank(statements, current);
                    continue;
                }
            } else if (inDollarBlock) {
                if (c == '$' && next == '$') {
                    inDollarBlock = false;
                    current.append(c).append(next);
                    i++;
                    continue;
                }
            } else if (c == quote) {
                // '' inside a quoted literal is an escaped quote, not the end of it.
                if (next == quote) {
                    current.append(c).append(next);
                    i++;
                    continue;
                }
                quote = 0;
            }
            current.append(c);
        }
        addIfNotBlank(statements, current);
        return statements;
    }

    private static void addIfNotBlank(final List<String> statements, final StringBuilder current) {
        final String statement = current.toString().trim();
        if (!statement.isEmpty()) {
            statements.add(statement);
        }
        current.setLength(0);
    }
}
