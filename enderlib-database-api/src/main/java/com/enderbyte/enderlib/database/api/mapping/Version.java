package com.enderbyte.enderlib.database.api.mapping;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an {@code int} or {@code long} field as an optimistic-locking version counter.
 *
 * <p>Repository updates then generate {@code ... WHERE id = ? AND version = ?} and increment the
 * counter. If no row matches, another server already wrote the record and
 * {@link com.enderbyte.enderlib.database.api.exception.OptimisticLockException} is thrown instead of
 * silently overwriting their change.
 *
 * <p>This is the cheapest correct answer to "the same player is online on two servers" and needs no
 * locks, no coordination service and no long-running transactions.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Version {
}
