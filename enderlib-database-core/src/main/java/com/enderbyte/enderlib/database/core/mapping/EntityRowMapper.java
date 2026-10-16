package com.enderbyte.enderlib.database.core.mapping;

import com.enderbyte.enderlib.database.api.exception.MappingException;
import com.enderbyte.enderlib.database.api.mapping.ConverterRegistry;
import com.enderbyte.enderlib.database.api.result.Row;
import com.enderbyte.enderlib.database.api.result.RowMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps rows onto annotated entities.
 *
 * <p>Columns absent from the result are left at their default rather than failing, so a
 * {@code SELECT uuid, name} into an entity with ten fields works as a projection.
 *
 * @param <T> entity type
 */
public final class EntityRowMapper<T> implements RowMapper<T> {

    private final EntityMetadata metadata;
    private final ConverterRegistry converters;

    /**
     * @param entityType annotated entity class
     * @param converters registry used for value conversion
     */
    public EntityRowMapper(final Class<T> entityType, final ConverterRegistry converters) {
        this.metadata = EntityMetadataRegistry.get(entityType);
        this.converters = converters;
    }

    /** @return the entity metadata backing this mapper */
    public EntityMetadata metadata() {
        return metadata;
    }

    @Override
    public T map(final Row row) {
        return metadata.instantiate(field -> {
            if (!row.hasColumn(field.column())) {
                return null;
            }
            final Object raw = row.get(field.column());
            if (raw == null) {
                // Records must still receive a value for primitive components.
                return metadata.isRecord() ? defaultValue(field.type()) : null;
            }
            try {
                return converters.fromDatabase(raw, field.type());
            } catch (final MappingException e) {
                throw new MappingException("Cannot map column '" + field.column() + "' to "
                        + metadata.entityType().getSimpleName() + '.' + field.fieldName()
                        + " (" + field.type().getSimpleName() + ')', e);
            }
        });
    }

    /**
     * Converts an entity into column values, for generated inserts and updates.
     *
     * @param entity      the entity
     * @param forInsert   {@code true} for an insert, {@code false} for an update
     * @return column name to database-ready value, in field order
     */
    public Map<String, Object> toColumns(final T entity, final boolean forInsert) {
        final Map<String, Object> values = new LinkedHashMap<>();
        for (final FieldMapping field : metadata.fields()) {
            if (forInsert ? !field.isInsertable() : !field.isUpdatable()) {
                continue;
            }
            values.put(field.column(), converters.toDatabase(field.read(entity)));
        }
        return values;
    }

    private static Object defaultValue(final Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == char.class) {
            return (char) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0.0d;
        }
        if (type == float.class) {
            return 0.0f;
        }
        if (type == short.class) {
            return (short) 0;
        }
        return (byte) 0;
    }
}
