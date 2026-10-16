package com.enderbyte.enderlib.database.api.schema;

import com.enderbyte.enderlib.database.api.query.SqlDialect;
import com.enderbyte.enderlib.database.api.query.SqlQuery;
import com.enderbyte.enderlib.database.api.query.SqlStatement;
import com.enderbyte.enderlib.database.api.util.Identifiers;
import com.enderbyte.enderlib.database.api.util.Preconditions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Schema helpers for tables and indexes.
 *
 * <p>These exist for tests, first-run bootstrapping and one-off admin commands. <strong>Migrations
 * remain the production mechanism</strong>: they are versioned, checksummed and safe when fifty
 * servers start at once, none of which is true of ad-hoc DDL.
 *
 * <p>Nothing here runs automatically. The library never creates a table or an index the plugin did
 * not explicitly ask for.
 *
 * <pre>{@code
 * database.executeAsync(Schema.createTable("players")
 *         .ifNotExists()
 *         .column(ColumnDefinition.of("uuid", SqlType.UUID).primaryKey())
 *         .column(ColumnDefinition.of("name", SqlType.VARCHAR).length(16).notNull())
 *         .column(ColumnDefinition.of("last_seen", SqlType.TIMESTAMP_WITH_TIMEZONE)));
 *
 * database.executeAsync(Schema.createIndex("idx_players_name").on("players").columns("name"));
 * }</pre>
 */
public final class Schema {

    private Schema() {
    }

    /** Starts a {@code CREATE TABLE}. */
    public static CreateTable createTable(final String table) {
        return new CreateTable(table);
    }

    /** Starts a {@code CREATE INDEX}. */
    public static CreateIndex createIndex(final String name) {
        return new CreateIndex(name);
    }

    /**
     * {@code DROP TABLE IF EXISTS}.
     *
     * @param table table to drop
     * @return the statement
     */
    public static SqlStatement dropTable(final String table) {
        Identifiers.requireValid(table, "table");
        return dialect -> SqlQuery.of("DROP TABLE IF EXISTS " + dialect.identifier(table));
    }

    /**
     * {@code ALTER TABLE ... ADD COLUMN}.
     *
     * @param table  table to change
     * @param column column to add
     * @return the statement
     */
    public static SqlStatement addColumn(final String table, final ColumnDefinition column) {
        Identifiers.requireValid(table, "table");
        return dialect -> {
            final StringBuilder sql = new StringBuilder("ALTER TABLE ")
                    .append(dialect.identifier(table))
                    .append(" ADD COLUMN ");
            column.render(dialect, sql);
            return SqlQuery.of(sql.toString());
        };
    }

    /**
     * {@code ALTER TABLE ... DROP COLUMN}.
     *
     * @param table  table to change
     * @param column column to remove
     * @return the statement
     */
    public static SqlStatement dropColumn(final String table, final String column) {
        Identifiers.requireValid(table, "table");
        Identifiers.requireValid(column, "column");
        return dialect -> SqlQuery.of("ALTER TABLE " + dialect.identifier(table)
                + " DROP COLUMN " + dialect.identifier(column));
    }

    /**
     * {@code DROP INDEX}.
     *
     * @param name  index name
     * @param table owning table, required by MySQL and MariaDB
     * @return the statement
     */
    public static SqlStatement dropIndex(final String name, final String table) {
        Identifiers.requireValid(name, "index");
        Identifiers.requireValid(table, "table");
        return dialect -> {
            final String dialectName = dialect.name();
            if ("MySQL".equals(dialectName) || "MariaDB".equals(dialectName)) {
                return SqlQuery.of("DROP INDEX " + dialect.identifier(name)
                        + " ON " + dialect.identifier(table));
            }
            return SqlQuery.of("DROP INDEX IF EXISTS " + dialect.identifier(name));
        };
    }

    /** Fluent {@code CREATE TABLE} builder. */
    public static final class CreateTable implements SqlStatement {

        private final String table;
        private final List<ColumnDefinition> columns = new ArrayList<>(8);
        private final List<String> primaryKey = new ArrayList<>(1);
        private boolean ifNotExists;

        private CreateTable(final String table) {
            this.table = Identifiers.requireValid(table, "table");
        }

        /** Adds {@code IF NOT EXISTS}. */
        public CreateTable ifNotExists() {
            this.ifNotExists = true;
            return this;
        }

        /** Adds a column. */
        public CreateTable column(final ColumnDefinition column) {
            columns.add(column);
            return this;
        }

        /** Declares a composite primary key; single-column keys are simpler as a column flag. */
        public CreateTable primaryKey(final String... columnNames) {
            for (final String column : columnNames) {
                primaryKey.add(Identifiers.requireValid(column, "column"));
            }
            return this;
        }

        @Override
        public SqlQuery compile(final SqlDialect dialect) {
            Preconditions.checkState(!columns.isEmpty(), "A table needs at least one column");
            final StringBuilder sql = new StringBuilder(128).append("CREATE TABLE ");
            if (ifNotExists) {
                sql.append("IF NOT EXISTS ");
            }
            sql.append(dialect.identifier(table)).append(" (");
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                columns.get(i).render(dialect, sql);
            }
            final List<String> keys = effectivePrimaryKey();
            if (!keys.isEmpty()) {
                sql.append(", PRIMARY KEY (");
                for (int i = 0; i < keys.size(); i++) {
                    if (i > 0) {
                        sql.append(", ");
                    }
                    sql.append(dialect.identifier(keys.get(i)));
                }
                sql.append(')');
            }
            sql.append(')');
            return SqlQuery.of(sql.toString());
        }

        private List<String> effectivePrimaryKey() {
            if (!primaryKey.isEmpty()) {
                return primaryKey;
            }
            final List<String> declared = new ArrayList<>(1);
            for (final ColumnDefinition column : columns) {
                if (column.isPrimaryKey()) {
                    declared.add(column.name());
                }
            }
            return declared;
        }
    }

    /**
     * Fluent {@code CREATE INDEX} builder.
     *
     * <p>Index the columns your plugin actually filters and sorts on. For Minecraft data that
     * usually means {@code uuid} (often already the primary key), {@code player_name} for lookup
     * commands, {@code last_seen} for pruning, and {@code server_id} on a shared network database.
     */
    public static final class CreateIndex implements SqlStatement {

        private final String name;
        private final List<String> columns = new ArrayList<>(2);
        private String table;
        private boolean unique;
        private boolean ifNotExists = true;

        private CreateIndex(final String name) {
            this.name = Identifiers.requireValid(name, "index");
        }

        /** Sets the table to index. */
        public CreateIndex on(final String table) {
            this.table = Identifiers.requireValid(table, "table");
            return this;
        }

        /** Sets the indexed columns, in order. */
        public CreateIndex columns(final String... columnNames) {
            for (final String column : columnNames) {
                columns.add(Identifiers.requireValid(column, "column"));
            }
            return this;
        }

        /** Makes the index unique. */
        public CreateIndex unique() {
            this.unique = true;
            return this;
        }

        /** Emits the index unconditionally; fails if it already exists. */
        public CreateIndex alwaysCreate() {
            this.ifNotExists = false;
            return this;
        }

        @Override
        public SqlQuery compile(final SqlDialect dialect) {
            Preconditions.checkState(table != null, "on(table) is required");
            Preconditions.checkState(!columns.isEmpty(), "columns(...) is required");
            final StringBuilder sql = new StringBuilder(96).append("CREATE ");
            if (unique) {
                sql.append("UNIQUE ");
            }
            sql.append("INDEX ");
            if (ifNotExists && dialect.supportsIfNotExistsForIndex()) {
                sql.append("IF NOT EXISTS ");
            }
            sql.append(dialect.identifier(name))
                    .append(" ON ")
                    .append(dialect.identifier(table))
                    .append(" (");
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append(dialect.identifier(columns.get(i)));
            }
            sql.append(')');
            return SqlQuery.of(sql.toString());
        }

        /** @return the indexed columns */
        public List<String> columnNames() {
            return Arrays.asList(columns.toArray(new String[0]));
        }
    }
}
