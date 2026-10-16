package com.enderbyte.enderlib.database.sql;

import com.enderbyte.enderlib.database.api.Database;
import com.enderbyte.enderlib.database.api.exception.OptimisticLockException;
import com.enderbyte.enderlib.database.api.query.Condition;
import com.enderbyte.enderlib.database.api.query.Conditions;
import com.enderbyte.enderlib.database.api.query.InsertQuery;
import com.enderbyte.enderlib.database.api.query.Query;
import com.enderbyte.enderlib.database.api.query.SelectQuery;
import com.enderbyte.enderlib.database.api.query.UpdateQuery;
import com.enderbyte.enderlib.database.api.repository.CrudRepository;
import com.enderbyte.enderlib.database.core.mapping.EntityMetadata;
import com.enderbyte.enderlib.database.core.mapping.EntityMetadataRegistry;
import com.enderbyte.enderlib.database.core.mapping.FieldMapping;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Reflection-backed {@link com.enderbyte.enderlib.database.api.repository.CrudRepository} over an annotated
 * entity.
 *
 * <p>Deliberately small. It covers key lookups, filtered lookups, upserts and bulk saves — the
 * operations every plugin writes by hand — and stops there. Anything more interesting is a query,
 * and the query builder is a better tool for it than a growing set of magic method names.
 *
 * <p>Subclass it to add domain lookups:
 *
 * <pre>{@code
 * public final class PlayerRepository extends SqlRepository<PlayerData, UUID> {
 *     public PlayerRepository(Database database) {
 *         super(database, PlayerData.class, UUID.class);
 *     }
 *
 *     public CompletableFuture<Optional<PlayerData>> findByName(String name) {
 *         return findOne(Conditions.equal("name", name));
 *     }
 * }
 * }</pre>
 *
 * @param <T>  entity type
 * @param <ID> primary key type
 */
public class SqlRepository<T, ID> implements CrudRepository<T, ID> {

    private final Database database;
    private final Class<T> entityType;
    private final Class<ID> idType;
    private final EntityMetadata metadata;
    private final FieldMapping identifier;

    /**
     * @param database   the database to run against
     * @param entityType entity class annotated with {@link com.enderbyte.enderlib.database.api.mapping.Table}
     * @param idType     primary key type
     */
    public SqlRepository(final Database database, final Class<T> entityType, final Class<ID> idType) {
        this.database = database;
        this.entityType = entityType;
        this.idType = idType;
        this.metadata = EntityMetadataRegistry.get(entityType);
        this.identifier = metadata.requireIdentifier();
    }

    // -------------------------------------------------------------------------------- reading

    @Override
    public CompletableFuture<Optional<T>> findById(final ID id) {
        return database.queryFirstAsync(
                Query.selectFrom(metadata.table()).where(identifier.column(), id).limit(1),
                entityType);
    }

    @Override
    public CompletableFuture<List<T>> findAll() {
        return database.queryAsync(Query.selectFrom(metadata.table()), entityType);
    }

    @Override
    public CompletableFuture<List<T>> findAllById(final Collection<ID> ids) {
        if (ids.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return database.queryAsync(
                Query.selectFrom(metadata.table()).whereIn(identifier.column(), ids),
                entityType);
    }

    @Override
    public CompletableFuture<Optional<T>> findOne(final Condition condition) {
        return database.queryFirstAsync(
                Query.selectFrom(metadata.table()).where(condition).limit(1), entityType);
    }

    @Override
    public CompletableFuture<List<T>> find(final Condition condition) {
        return database.queryAsync(select(condition), entityType);
    }

    @Override
    public CompletableFuture<List<T>> find(final Condition condition, final int limit, final int offset) {
        return database.queryAsync(select(condition).limit(limit).offset(offset), entityType);
    }

    @Override
    public CompletableFuture<Boolean> existsById(final ID id) {
        return database.queryAsync(
                        Query.selectCount(metadata.table()).where(identifier.column(), id))
                .thenApply(result -> result.value(Long.class).orElse(0L) > 0L);
    }

    @Override
    public CompletableFuture<Long> count() {
        return database.queryAsync(Query.selectCount(metadata.table()))
                .thenApply(result -> result.value(Long.class).orElse(0L));
    }

    // -------------------------------------------------------------------------------- writing

    @Override
    public CompletableFuture<Void> save(final T entity) {
        if (metadata.version().isPresent()) {
            return saveVersioned(entity);
        }
        return database.executeAsync(upsertFor(entity)).thenApply(rows -> null);
    }

    @Override
    public CompletableFuture<Void> saveAll(final Collection<T> entities) {
        if (entities.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        if (metadata.version().isPresent()) {
            // Optimistic locking needs the affected-row count per entity, which a batch does not
            // give us reliably across drivers. Correctness wins over the round-trip saving here.
            return CompletableFuture.allOf(entities.stream()
                    .map(this::save)
                    .toArray(CompletableFuture[]::new));
        }

        final List<FieldMapping> columns = insertableFields();
        final List<List<Object>> rows = new ArrayList<>(entities.size());
        for (final T entity : entities) {
            rows.add(valuesOf(entity, columns));
        }
        // Compile one upsert through the builder so the engine's conflict syntax and identifier
        // quoting are identical to the single-entity path.
        final InsertQuery template = Query.insertInto(metadata.table())
                .columns(columns.stream().map(FieldMapping::column).toArray(String[]::new))
                .row(new Object[columns.size()])
                .onConflictUpdateAll(identifier.column());
        final String sql = template.compile(database.dialect()).sql();
        return database.batchAsync(sql, rows).thenApply(counts -> null);
    }

    @Override
    public CompletableFuture<Void> delete(final T entity) {
        return deleteById(identifierOf(entity)).thenApply(deleted -> null);
    }

    @Override
    public CompletableFuture<Boolean> deleteById(final ID id) {
        return database.deleteAsync(
                        Query.deleteFrom(metadata.table()).where(identifier.column(), id))
                .thenApply(rows -> rows > 0);
    }

    /** @return the database this repository runs against, for subclasses adding custom queries */
    protected final Database database() {
        return database;
    }

    /** @return the table name, for subclasses building their own statements */
    protected final String table() {
        return metadata.table();
    }

    /** @return the entity type */
    protected final Class<T> entityType() {
        return entityType;
    }

    /** @return the primary key type */
    protected final Class<ID> idType() {
        return idType;
    }

    private SelectQuery select(final Condition condition) {
        final SelectQuery select = Query.selectFrom(metadata.table());
        return condition == null ? select : select.where(condition);
    }

    private InsertQuery upsertFor(final T entity) {
        final InsertQuery insert = Query.insertInto(metadata.table());
        for (final FieldMapping field : insertableFields()) {
            insert.value(field.column(), field.read(entity));
        }
        return insert.onConflictUpdateAll(identifier.column());
    }

    /**
     * Saves an entity carrying a {@code @Version} field.
     *
     * <p>New rows are inserted; existing rows are updated only while the stored version still
     * matches the one that was read. When two servers save the same player at once, one of them
     * updates zero rows and is told so, instead of silently discarding the other's write.
     */
    private CompletableFuture<Void> saveVersioned(final T entity) {
        final FieldMapping versionField = metadata.version().orElseThrow();
        final Object currentVersion = versionField.read(entity);
        if (currentVersion == null || asLong(currentVersion) == 0L) {
            final InsertQuery insert = Query.insertInto(metadata.table());
            for (final FieldMapping field : insertableFields()) {
                insert.value(field.column(), field == versionField ? 1L : field.read(entity));
            }
            return database.executeAsync(insert).thenAccept(rows -> versionField.write(entity, 1L));
        }

        final long next = asLong(currentVersion) + 1L;
        final UpdateQuery update = Query.update(metadata.table());
        for (final FieldMapping field : metadata.fields()) {
            if (field.isUpdatable() && !field.isVersion()) {
                update.set(field.column(), field.read(entity));
            }
        }
        update.set(versionField.column(), next)
                .where(identifier.column(), identifierOf(entity))
                .and(Conditions.equal(versionField.column(), currentVersion));

        return database.updateAsync(update).thenAccept(rows -> {
            if (rows == 0) {
                throw new OptimisticLockException(entityType.getSimpleName() + " with id "
                        + identifierOf(entity) + " was modified by another server since it was read"
                        + " (expected version " + currentVersion + "). Re-read it and retry.");
            }
            versionField.write(entity, next);
        });
    }

    private List<FieldMapping> insertableFields() {
        final List<FieldMapping> insertable = new ArrayList<>(metadata.fields().size());
        for (final FieldMapping field : metadata.fields()) {
            if (field.isInsertable()) {
                insertable.add(field);
            }
        }
        return insertable;
    }

    private List<Object> valuesOf(final T entity, final List<FieldMapping> fields) {
        final List<Object> values = new ArrayList<>(fields.size());
        for (final FieldMapping field : fields) {
            values.add(field.read(entity));
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private ID identifierOf(final T entity) {
        return (ID) identifier.read(entity);
    }

    private static long asLong(final Object version) {
        return version instanceof Number number ? number.longValue() : 0L;
    }
}
