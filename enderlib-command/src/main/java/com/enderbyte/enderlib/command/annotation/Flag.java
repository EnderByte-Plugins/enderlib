package com.enderbyte.enderlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A flag parameter. {@code boolean} parameters are switches; other types take a value.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface Flag {
    /** Long name: {@code --value}. */
    String value();

    /** Short name: {@code -c}; the default (NUL character) means none. */
    char shortName() default '\u0000';

    String permission() default "";
}
