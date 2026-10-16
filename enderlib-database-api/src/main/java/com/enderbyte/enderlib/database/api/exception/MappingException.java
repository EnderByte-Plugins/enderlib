package com.enderbyte.enderlib.database.api.exception;

/**
 * Thrown when a row cannot be converted to (or an entity cannot be converted from) a Java object:
 * a missing {@code @Table} annotation, an unmapped column type, an inaccessible field, or a
 * converter that rejected a value.
 */
public class MappingException extends DatabaseException {

    private static final long serialVersionUID = 1L;

    public MappingException(final String message) {
        super(message);
    }

    public MappingException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
