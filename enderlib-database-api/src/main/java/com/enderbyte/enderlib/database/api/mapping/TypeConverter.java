package com.enderbyte.enderlib.database.api.mapping;

/**
 * Converts one Java type to and from its database representation.
 *
 * <p>Register a converter to support a type the library does not know about — a {@code Location},
 * an {@code ItemStack} blob, a JSON document — without touching the mapper itself.
 *
 * <pre>{@code
 * registry.register(new TypeConverter<Location>() {
 *     public Class<Location> javaType()          { return Location.class; }
 *     public Object toDatabase(Location value)   { return serialize(value); }
 *     public Location fromDatabase(Object value) { return deserialize((String) value); }
 * });
 * }</pre>
 *
 * <p>Implementations must be thread-safe and must tolerate {@code null} on both sides.
 *
 * @param <J> the Java type
 */
public interface TypeConverter<J> {

    /** @return the Java type this converter handles */
    Class<J> javaType();

    /**
     * @param value the Java value, possibly {@code null}
     * @return a value the JDBC driver accepts (String, Number, byte[], java.sql/java.time type...)
     */
    Object toDatabase(J value);

    /**
     * @param value the value produced by the driver, possibly {@code null}
     * @return the Java value
     */
    J fromDatabase(Object value);
}
