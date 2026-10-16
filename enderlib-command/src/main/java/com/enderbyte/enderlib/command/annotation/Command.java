package com.enderbyte.enderlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a class as a command. Its methods annotated with {@link Default} or {@link Subcommand} become the runnable paths.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Command {
    /** The command label. */
    String value();

    String[] aliases() default {};

    String description() default "";

    /** Adds a generated {@code help [page]} subcommand. */
    boolean help() default false;
}
