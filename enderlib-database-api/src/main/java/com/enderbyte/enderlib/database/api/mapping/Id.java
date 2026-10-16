package com.enderbyte.enderlib.database.api.mapping;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the primary key field, which repositories use for lookups, updates and deletes.
 *
 * <p>For Minecraft data this is almost always the player UUID. Composite keys are not supported:
 * they add a lot of complexity for a case that is rare in plugin schemas.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Id {

    /**
     * @return {@code true} if the database generates the value (auto-increment / identity), in
     *         which case the field is omitted from inserts and populated from the generated keys
     */
    boolean generated() default false;
}
