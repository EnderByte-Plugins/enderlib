package com.enderbyte.enderlib.database.api.repository;

import com.enderbyte.enderlib.database.api.query.Condition;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * {@link Repository} plus the operations a plugin usually needs next: existence checks, counting,
 * bulk saves and filtered lookups.
 *
 * @param <T>  entity type
 * @param <ID> primary key type
 */
public interface CrudRepository<T, ID> extends Repository<T, ID> {

    /** @return {@code true} if a row with this key exists */
    CompletableFuture<Boolean> existsById(ID id);

    /** @return the row count */
    CompletableFuture<Long> count();

    /**
     * @param id primary key
     * @return {@code true} if a row was removed
     */
    CompletableFuture<Boolean> deleteById(ID id);

    /**
     * Persists many entities in one batch, which is far cheaper than one statement each.
     *
     * @param entities entities to persist
     * @return completion signal
     */
    CompletableFuture<Void> saveAll(Collection<T> entities);

    /**
     * @param condition filter built with {@link com.enderbyte.enderlib.database.api.query.Conditions}
     * @return the first matching entity
     */
    CompletableFuture<Optional<T>> findOne(Condition condition);

    /**
     * @param condition filter
     * @return every matching entity
     */
    CompletableFuture<List<T>> find(Condition condition);

    /**
     * Paged lookup, the right way to walk a large table without holding it all in memory.
     *
     * @param condition filter, or {@code null} for all rows
     * @param limit     maximum rows
     * @param offset    rows to skip
     * @return the matching entities
     */
    CompletableFuture<List<T>> find(Condition condition, int limit, int offset);

    /**
     * @param ids primary keys
     * @return the entities that exist, in database order
     */
    CompletableFuture<List<T>> findAllById(Collection<ID> ids);
}
