package com.enderbyte.enderlib.database.api.query;

import java.util.List;

/**
 * Everything a dialect needs in order to render an {@code INSERT}.
 *
 * <p>The whole statement is handed to the dialect rather than assembled centrally with an
 * engine-specific tail bolted on, because engines disagree about more than the tail: PostgreSQL and
 * SQLite append {@code ON CONFLICT}, MySQL appends {@code ON DUPLICATE KEY UPDATE}, and H2 changes
 * the leading keyword to {@code MERGE} and puts its key list before {@code VALUES}. One method per
 * engine is easier to follow than three hooks that each engine uses differently.
 *
 * @param table           target table
 * @param columns         column names, in the order the row values are given
 * @param rows            one list of values per row, each matching {@code columns}
 * @param conflictColumns the unique key that may collide, or {@code null} for a plain insert
 * @param updateColumns   columns to overwrite on collision, or {@code null} for a plain insert
 */
public record InsertSpec(String table,
                         List<String> columns,
                         List<List<Object>> rows,
                         List<String> conflictColumns,
                         List<String> updateColumns) {

    /** @return {@code true} when the statement should update an existing row instead of failing */
    public boolean isUpsert() {
        return updateColumns != null && !updateColumns.isEmpty();
    }
}
