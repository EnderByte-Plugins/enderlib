package com.enderbyte.enderlib.database.api.transaction;

/**
 * The body of a transaction that produces a value.
 *
 * <p>Runs on a database worker thread, so the blocking calls on {@link Transaction} are safe here.
 * Do not touch Minecraft API objects from inside: hop back to the server thread with the plugin
 * scheduler once the returned future completes.
 *
 * @param <T> result type
 */
@FunctionalInterface
public interface TransactionCallback<T> {

    /**
     * @param transaction the active transaction
     * @return the value to complete the future with
     * @throws Exception any failure; the transaction is rolled back and the future fails
     */
    T apply(Transaction transaction) throws Exception;
}
