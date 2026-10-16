package com.enderbyte.enderlib.database.api.repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The minimal repository contract.
 *
 * <p>Entirely optional: the query builder and raw SQL remain first-class, and a plugin that prefers
 * hand-written access never has to see this interface. It exists because the same four operations
 * are written in every plugin.
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
public interface Repository<T, ID> {

    /**
     * @param id primary key
     * @return the entity, or empty
     */
    CompletableFuture<Optional<T>> findById(ID id);

    /**
     * @return every row. Only reasonable for small tables; use a paged or filtered query otherwise
     */
    CompletableFuture<List<T>> findAll();

    /**
     * Inserts the entity, or updates it when the key already exists.
     *
     * @param entity entity to persist
     * @return completion signal
     */
    CompletableFuture<Void> save(T entity);

    /**
     * @param entity entity to remove
     * @return completion signal
     */
    CompletableFuture<Void> delete(T entity);
}
