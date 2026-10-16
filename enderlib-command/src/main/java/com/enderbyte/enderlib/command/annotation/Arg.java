package com.enderbyte.enderlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Names an argument parameter.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface Arg {
    /** Argument name shown in usage; defaults to the parameter name (compile with {@code -parameters}). */
    String value();
}
