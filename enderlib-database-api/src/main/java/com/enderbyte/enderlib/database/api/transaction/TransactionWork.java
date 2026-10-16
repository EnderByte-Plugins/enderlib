package com.enderbyte.enderlib.database.api.transaction;

/**
 * The body of a transaction that produces no value.
 *
 * @see TransactionCallback
 */
@FunctionalInterface
public interface TransactionWork {

    /**
     * @param transaction the active transaction
     * @throws Exception any failure; the transaction is rolled back and the future fails
     */
    void accept(Transaction transaction) throws Exception;
}
