package com.enderbyte.enderlib.database.api.schema;

import com.enderbyte.enderlib.database.api.query.SqlDialect;
import com.enderbyte.enderlib.database.api.util.Identifiers;

/**
 * One column in a {@code CREATE TABLE} or {@code ALTER TABLE ADD COLUMN}.
 *
 * <pre>{@code
 * ColumnDefinition.of("uuid", SqlType.UUID).notNull().primaryKey();
 * ColumnDefinition.of("name", SqlType.VARCHAR).length(16).notNull();
 * ColumnDefinition.of("balance", SqlType.DECIMAL).precision(19).scale(4).defaultLiteral(0);
 * }</pre>
 */
public final class ColumnDefinition {

    private final String name;
    private final SqlType type;
    private int length = -1;
    private int precision = -1;
    private int scale = -1;
    private boolean nullable = true;
    private boolean primaryKey;
    private boolean unique;
    private boolean autoIncrement;
    private String defaultExpression;

    private ColumnDefinition(final String name, final SqlType type) {
        this.name = Identifiers.requireValid(name, "column");
        this.type = java.util.Objects.requireNonNull(type, "type");
    }

    /**
     * @param name column name
     * @param type logical type, mapped to the engine's native type
     * @return a new definition
     */
    public static ColumnDefinition of(final String name, final SqlType type) {
        return new ColumnDefinition(name, type);
    }

    /** Length for {@code CHAR}/{@code VARCHAR}/{@code BINARY}. */
    public ColumnDefinition length(final int length) {
        this.length = length;
        return this;
    }

    /** Precision for {@code DECIMAL}. */
    public ColumnDefinition precision(final int precision) {
        this.precision = precision;
        return this;
    }

    /** Scale for {@code DECIMAL}. */
    public ColumnDefinition scale(final int scale) {
        this.scale = scale;
        return this;
    }

    /** Marks the column {@code NOT NULL}. */
    public ColumnDefinition notNull() {
        this.nullable = false;
        return this;
    }

    /** Marks the column as the single-column primary key. */
    public ColumnDefinition primaryKey() {
        this.primaryKey = true;
        this.nullable = false;
        return this;
    }

    /** Adds a {@code UNIQUE} constraint. */
    public ColumnDefinition unique() {
        this.unique = true;
        return this;
    }

    /** Makes the column auto-incrementing; only meaningful for integer types. */
    public ColumnDefinition autoIncrement() {
        this.autoIncrement = true;
        return this;
    }

    /**
     * Sets a default from a literal value.
     *
     * @param value a number, boolean or string constant from your own code, not user input
     * @return this definition
     */
    public ColumnDefinition defaultLiteral(final Object value) {
        if (value == null) {
            this.defaultExpression = "NULL";
        } else if (value instanceof Number || value instanceof Boolean) {
            this.defaultExpression = value.toString();
        } else {
            this.defaultExpression = '\'' + value.toString().replace("'", "''") + '\'';
        }
        return this;
    }

    /**
     * Sets a default from a SQL expression such as {@code CURRENT_TIMESTAMP}.
     *
     * @param expression a constant from your own code; never build it from user input
     * @return this definition
     */
    public ColumnDefinition defaultExpression(final String expression) {
        this.defaultExpression = expression;
        return this;
    }

    public String name() {
        return name;
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    /**
     * Renders this column for the given engine.
     *
     * @param dialect target dialect
     * @param sql     buffer to append to
     */
    public void render(final SqlDialect dialect, final StringBuilder sql) {
        sql.append(dialect.identifier(name)).append(' ');
        if (autoIncrement) {
            sql.append(autoIncrementType(dialect));
        } else {
            sql.append(dialect.columnType(type, length, precision, scale));
        }
        if (!nullable) {
            sql.append(" NOT NULL");
        }
        if (defaultExpression != null) {
            sql.append(" DEFAULT ").append(defaultExpression);
        }
        if (unique) {
            sql.append(" UNIQUE");
        }
    }

    private String autoIncrementType(final SqlDialect dialect) {
        return switch (dialect.name()) {
            case "PostgreSQL" -> type == SqlType.BIGINT ? "BIGSERIAL" : "SERIAL";
            case "MySQL", "MariaDB" -> dialect.columnType(type, length, precision, scale) + " AUTO_INCREMENT";
            case "SQLite" -> "INTEGER";
            default -> dialect.columnType(type, length, precision, scale) + " AUTO_INCREMENT";
        };
    }
}
