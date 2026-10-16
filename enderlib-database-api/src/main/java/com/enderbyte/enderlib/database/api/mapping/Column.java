package com.enderbyte.enderlib.database.api.mapping;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Maps a field to a column.
 *
 * <p>Fields without this annotation are still mapped, using the field name converted from
 * {@code camelCase} to {@code snake_case} ({@code lastSeen} becomes {@code last_seen}). Annotate
 * explicitly whenever the column name does not follow that convention.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Column {

    /** @return the column name; empty means "derive from the field name" */
    String value() default "";

    /** @return whether the column participates in generated {@code INSERT} statements */
    boolean insertable() default true;

    /** @return whether the column participates in generated {@code UPDATE} statements */
    boolean updatable() default true;
}
