package com.enderbyte.enderlib.config;

import java.lang.reflect.Type;

/**
 * Converts one Java type to and from the plain YAML tree (strings, numbers, booleans, lists, maps).
 *
 * <pre>{@code
 * configs.mapper().register(Money.class, TypeSerializer.of(
 *         raw -> Money.parse(raw.toString()),
 *         Money::toString));
 * }</pre>
 */
public interface TypeSerializer<T> {

    /**
     * @param raw  the YAML value (never {@code null})
     * @param type the full generic target type
     * @throws IllegalArgumentException with a human message if {@code raw} cannot be converted
     */
    T deserialize(Object raw, Type type, ConfigMapper mapper);

    /** Returns a YAML-compatible value. */
    Object serialize(T value, Type type, ConfigMapper mapper);

    static <T> TypeSerializer<T> of(java.util.function.Function<Object, T> read, java.util.function.Function<T, Object> write) {
        return new TypeSerializer<>() {
            @Override
            public T deserialize(Object raw, Type type, ConfigMapper mapper) {
                return read.apply(raw);
            }

            @Override
            public Object serialize(T value, Type type, ConfigMapper mapper) {
                return write.apply(value);
            }
        };
    }
}
