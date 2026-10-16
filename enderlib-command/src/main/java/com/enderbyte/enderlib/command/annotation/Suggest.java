package com.enderbyte.enderlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom tab-completion for an argument.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface Suggest {
    /** Fixed suggestions. */
    String[] value() default {};

    /**
     * Name of a method on the command object returning {@code Collection<String>}, taking either no
     * parameters or a {@code CommandSender}.
     */
    String method() default "";
}
