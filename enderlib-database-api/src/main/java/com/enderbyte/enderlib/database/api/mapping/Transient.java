package com.enderbyte.enderlib.database.api.mapping;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Excludes a field from mapping.
 *
 * <p>{@code static}, {@code transient} and synthetic fields are excluded automatically; this
 * annotation covers ordinary fields that simply are not persisted, such as a cached display name.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Transient {
}
