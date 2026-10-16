package com.enderbyte.enderlib.database.api.mapping;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Maps a class to a table.
 *
 * <pre>{@code
 * @Table("players")
 * public class PlayerData {
 *     @Id @Column("uuid") private UUID uuid;
 *     @Column("name") private String name;
 *     @Column("balance") private double balance;
 * }
 * }</pre>
 *
 * <p>Mapping is reflective but the reflection happens once per class: field handles are resolved on
 * first use and cached, so per-row mapping is plain field access.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Table {

    /** @return the table name */
    String value();

    /** @return an optional schema qualifier */
    String schema() default "";
}
