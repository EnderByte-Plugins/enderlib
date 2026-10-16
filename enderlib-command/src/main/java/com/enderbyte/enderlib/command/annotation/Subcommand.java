package com.enderbyte.enderlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a subcommand. Parameters after the optional sender parameter become arguments in order.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Subcommand {
    /**
     * The path, for example {@code "give"} or {@code "admin reset"}. Further values are aliases of the
     * last word.
     */
    String[] value();
}
