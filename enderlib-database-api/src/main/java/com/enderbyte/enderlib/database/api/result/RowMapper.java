package com.enderbyte.enderlib.database.api.result;

/**
 * Converts a {@link Row} into a domain object.
 *
 * <p>Hand-written mappers are the fastest option and are the right choice on hot paths; the
 * annotation-driven mapper is the convenient one. Both are first-class.
 *
 * @param <T> produced type
 */
@FunctionalInterface
public interface RowMapper<T> {

    /**
     * @param row the row to convert
     * @return the mapped object
     */
    T map(Row row);
}
