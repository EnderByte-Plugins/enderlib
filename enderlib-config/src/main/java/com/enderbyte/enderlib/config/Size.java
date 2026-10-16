package com.enderbyte.enderlib.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validation: a string, collection or map size must lie within [min, max].
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
public @interface Size {
    int min() default 0;
    int max() default Integer.MAX_VALUE;
}
