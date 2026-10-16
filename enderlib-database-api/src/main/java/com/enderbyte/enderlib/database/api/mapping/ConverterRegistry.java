package com.enderbyte.enderlib.database.api.mapping;

import java.util.Optional;

/**
 * Registry of {@link TypeConverter}s used by the row mapper and the parameter binder.
 *
 * <p>Each database owns one registry, pre-populated with converters for the standard types
 * (String, the primitives and their boxes, {@code UUID}, enums, {@code Instant},
 * {@code LocalDate}, {@code LocalDateTime}, {@code byte[]}, {@code BigDecimal}). Plugins add their
 * own on top.
 *
 * <p>Implementations must be thread-safe: converters are read from every database worker thread.
 */
public interface ConverterRegistry {

    /**
     * Registers or replaces a converter.
     *
     * @param converter the converter, keyed on {@link TypeConverter#javaType()}
     */
    void register(TypeConverter<?> converter);

    /**
     * @param type Java type to convert
     * @param <T>  Java type
     * @return the converter, or empty when the type is handled directly by the driver
     */
    <T> Optional<TypeConverter<T>> converter(Class<T> type);

    /**
     * Converts a Java value into something the driver accepts. Values without a converter are
     * passed through untouched.
     *
     * @param value value to convert, may be {@code null}
     * @return the database representation
     */
    Object toDatabase(Object value);

    /**
     * Converts a driver-supplied value into the requested Java type.
     *
     * @param value raw value, may be {@code null}
     * @param type  target type
     * @param <T>   target type
     * @return the converted value
     * @throws com.enderbyte.enderlib.database.api.exception.MappingException if no conversion is possible
     */
    <T> T fromDatabase(Object value, Class<T> type);
}
