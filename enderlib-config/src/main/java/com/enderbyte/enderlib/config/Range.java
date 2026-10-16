package com.enderbyte.enderlib.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validation: a numeric value must lie within [min, max].
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
public @interface Range {
    double min() default -Double.MAX_VALUE;
    double max() default Double.MAX_VALUE;
}
