package com.enderbyte.enderlib.database.core.mapping;

import com.enderbyte.enderlib.database.api.exception.MappingException;

import java.lang.reflect.Field;

/**
 * The link between one entity field and one column.
 *
 * <p>{@link Field#setAccessible(boolean)} is called once when the metadata is built, so per-row
 * access is a plain field read or write rather than a repeated access check.
 */
public final class FieldMapping {

    private final Field field;
    private final String column;
    private final boolean insertable;
    private final boolean updatable;
    private final boolean identifier;
    private final boolean generated;
    private final boolean version;

    FieldMapping(final Field field,
                 final String column,
                 final boolean insertable,
                 final boolean updatable,
                 final boolean identifier,
                 final boolean generated,
                 final boolean version) {
        this.field = field;
        this.column = column;
        this.insertable = insertable;
        this.updatable = updatable;
        this.identifier = identifier;
        this.generated = generated;
        this.version = version;
    }

    /** @return the column name */
    public String column() {
        return column;
    }

    /** @return the declared Java type */
    public Class<?> type() {
        return field.getType();
    }

    /** @return the field name */
    public String fieldName() {
        return field.getName();
    }

    /** @return whether the column appears in generated inserts */
    public boolean isInsertable() {
        return insertable && !generated;
    }

    /** @return whether the column appears in generated updates */
    public boolean isUpdatable() {
        return updatable && !identifier;
    }

    /** @return whether this is the primary key */
    public boolean isIdentifier() {
        return identifier;
    }

    /** @return whether the database generates the key */
    public boolean isGenerated() {
        return generated;
    }

    /** @return whether this is the optimistic-locking version counter */
    public boolean isVersion() {
        return version;
    }

    /**
     * Reads the field.
     *
     * @param entity the instance
     * @return the value
     */
    public Object read(final Object entity) {
        try {
            return field.get(entity);
        } catch (final IllegalAccessException e) {
            throw new MappingException("Cannot read field '" + field.getName()
                    + "' of " + field.getDeclaringClass().getSimpleName(), e);
        }
    }

    /**
     * Writes the field.
     *
     * @param entity the instance
     * @param value  the value
     */
    public void write(final Object entity, final Object value) {
        try {
            field.set(entity, value);
        } catch (final IllegalAccessException e) {
            throw new MappingException("Cannot write field '" + field.getName()
                    + "' of " + field.getDeclaringClass().getSimpleName(), e);
        }
    }
}
