package com.enderbyte.enderlib.database.core.mapping;

import com.enderbyte.enderlib.database.api.exception.MappingException;
import com.enderbyte.enderlib.database.api.mapping.Column;
import com.enderbyte.enderlib.database.api.mapping.Id;
import com.enderbyte.enderlib.database.api.mapping.Table;
import com.enderbyte.enderlib.database.api.mapping.Transient;
import com.enderbyte.enderlib.database.api.mapping.Version;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * Everything the library needs to know about an entity class, resolved once and cached.
 *
 * <p>Both ordinary classes and records are supported. A class needs a no-argument constructor (it
 * may be private); a record is built through its canonical constructor, which lets entities stay
 * immutable.
 *
 * <p>Fields map to {@code snake_case} column names unless {@link Column} says otherwise, so the
 * common case needs only {@link Table} and {@link Id}.
 */
public final class EntityMetadata {

    private final Class<?> entityType;
    private final String table;
    private final List<FieldMapping> fields;
    private final FieldMapping identifier;
    private final FieldMapping version;
    private final Constructor<?> constructor;
    private final boolean record;

    private EntityMetadata(final Class<?> entityType,
                           final String table,
                           final List<FieldMapping> fields,
                           final FieldMapping identifier,
                           final FieldMapping version,
                           final Constructor<?> constructor,
                           final boolean record) {
        this.entityType = entityType;
        this.table = table;
        this.fields = Collections.unmodifiableList(fields);
        this.identifier = identifier;
        this.version = version;
        this.constructor = constructor;
        this.record = record;
    }

    /**
     * Reflects over an entity class.
     *
     * @param entityType the class
     * @return its metadata
     * @throws MappingException if the class is not usable as an entity
     */
    public static EntityMetadata of(final Class<?> entityType) {
        final Table table = entityType.getAnnotation(Table.class);
        if (table == null) {
            throw new MappingException(entityType.getName()
                    + " is not annotated with @Table and cannot be mapped automatically."
                    + " Annotate it, or pass an explicit RowMapper.");
        }
        final String tableName = table.schema().isEmpty()
                ? table.value()
                : table.schema() + '.' + table.value();

        return entityType.isRecord()
                ? forRecord(entityType, tableName)
                : forClass(entityType, tableName);
    }

    private static EntityMetadata forRecord(final Class<?> entityType, final String tableName) {
        final RecordComponent[] components = entityType.getRecordComponents();
        final List<FieldMapping> mappings = new ArrayList<>(components.length);
        final Class<?>[] parameterTypes = new Class<?>[components.length];
        FieldMapping identifier = null;
        FieldMapping version = null;

        for (int i = 0; i < components.length; i++) {
            final RecordComponent component = components[i];
            parameterTypes[i] = component.getType();
            final Field field;
            try {
                field = entityType.getDeclaredField(component.getName());
            } catch (final NoSuchFieldException e) {
                throw new MappingException("Record component '" + component.getName()
                        + "' has no backing field", e);
            }
            if (component.isAnnotationPresent(Transient.class) || field.isAnnotationPresent(Transient.class)) {
                throw new MappingException("@Transient is not supported on record components ("
                        + entityType.getSimpleName() + '.' + component.getName()
                        + "): every component is required by the canonical constructor");
            }
            makeAccessible(field);
            final FieldMapping mapping = mappingFor(field);
            mappings.add(mapping);
            if (mapping.isIdentifier()) {
                identifier = requireSingle(identifier, mapping, entityType, "@Id");
            }
            if (mapping.isVersion()) {
                version = requireSingle(version, mapping, entityType, "@Version");
            }
        }

        final Constructor<?> canonical;
        try {
            canonical = entityType.getDeclaredConstructor(parameterTypes);
        } catch (final NoSuchMethodException e) {
            throw new MappingException("Cannot find the canonical constructor of "
                    + entityType.getName(), e);
        }
        makeAccessible(canonical);
        return new EntityMetadata(entityType, tableName, mappings, identifier, version, canonical, true);
    }

    private static EntityMetadata forClass(final Class<?> entityType, final String tableName) {
        final List<FieldMapping> mappings = new ArrayList<>(8);
        FieldMapping identifier = null;
        FieldMapping version = null;

        // Walk up the hierarchy so entities can share a mapped base class.
        for (Class<?> current = entityType; current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (final Field field : current.getDeclaredFields()) {
                if (field.isSynthetic()
                        || Modifier.isStatic(field.getModifiers())
                        || Modifier.isTransient(field.getModifiers())
                        || field.isAnnotationPresent(Transient.class)) {
                    continue;
                }
                makeAccessible(field);
                final FieldMapping mapping = mappingFor(field);
                mappings.add(mapping);
                if (mapping.isIdentifier()) {
                    identifier = requireSingle(identifier, mapping, entityType, "@Id");
                }
                if (mapping.isVersion()) {
                    version = requireSingle(version, mapping, entityType, "@Version");
                }
            }
        }

        final Constructor<?> constructor;
        try {
            constructor = entityType.getDeclaredConstructor();
        } catch (final NoSuchMethodException e) {
            throw new MappingException(entityType.getName()
                    + " needs a no-argument constructor to be mapped (it may be private),"
                    + " or it can be declared as a record", e);
        }
        makeAccessible(constructor);
        return new EntityMetadata(entityType, tableName, mappings, identifier, version, constructor, false);
    }

    private static FieldMapping mappingFor(final Field field) {
        final Column column = field.getAnnotation(Column.class);
        final Id id = field.getAnnotation(Id.class);
        final String columnName = column != null && !column.value().isEmpty()
                ? column.value()
                : toSnakeCase(field.getName());
        return new FieldMapping(
                field,
                columnName,
                column == null || column.insertable(),
                column == null || column.updatable(),
                id != null,
                id != null && id.generated(),
                field.isAnnotationPresent(Version.class));
    }

    private static FieldMapping requireSingle(final FieldMapping existing,
                                              final FieldMapping candidate,
                                              final Class<?> entityType,
                                              final String annotation) {
        if (existing != null) {
            throw new MappingException(entityType.getSimpleName() + " declares " + annotation
                    + " on more than one field ('" + existing.fieldName() + "' and '"
                    + candidate.fieldName() + "'); composite keys are not supported");
        }
        return candidate;
    }

    private static void makeAccessible(final java.lang.reflect.AccessibleObject member) {
        try {
            member.setAccessible(true);
        } catch (final RuntimeException e) {
            throw new MappingException("Cannot access " + member
                    + ". Open the package to the library or make the member public.", e);
        }
    }

    /** Converts {@code lastSeenAt} to {@code last_seen_at}. */
    static String toSnakeCase(final String name) {
        final StringBuilder result = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            final char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    result.append('_');
                }
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString().toLowerCase(Locale.ROOT);
    }

    public Class<?> entityType() {
        return entityType;
    }

    /** @return the table name, schema-qualified when the annotation said so */
    public String table() {
        return table;
    }

    /** @return every mapped field, in declaration order */
    public List<FieldMapping> fields() {
        return fields;
    }

    /** @return the primary key mapping */
    public Optional<FieldMapping> identifier() {
        return Optional.ofNullable(identifier);
    }

    /** @return the optimistic-locking version mapping */
    public Optional<FieldMapping> version() {
        return Optional.ofNullable(version);
    }

    /** @return the primary key mapping, or a clear failure when the entity declares none */
    public FieldMapping requireIdentifier() {
        if (identifier == null) {
            throw new MappingException(entityType.getSimpleName()
                    + " has no @Id field, so it cannot be used with a repository");
        }
        return identifier;
    }

    /** @return {@code true} if the entity is a record */
    public boolean isRecord() {
        return record;
    }

    /**
     * Builds an instance from per-field values.
     *
     * @param values supplies the value for each mapped field
     * @param <T>    entity type
     * @return the new instance
     */
    @SuppressWarnings("unchecked")
    public <T> T instantiate(final Function<FieldMapping, Object> values) {
        try {
            if (record) {
                final Object[] arguments = new Object[fields.size()];
                for (int i = 0; i < fields.size(); i++) {
                    arguments[i] = values.apply(fields.get(i));
                }
                return (T) constructor.newInstance(arguments);
            }
            final Object entity = constructor.newInstance();
            for (final FieldMapping mapping : fields) {
                final Object value = values.apply(mapping);
                if (value != null) {
                    mapping.write(entity, value);
                }
            }
            return (T) entity;
        } catch (final ReflectiveOperationException e) {
            throw new MappingException("Cannot instantiate " + entityType.getName(), e);
        } catch (final IllegalArgumentException e) {
            throw new MappingException("Type mismatch while instantiating " + entityType.getName(), e);
        }
    }
}
