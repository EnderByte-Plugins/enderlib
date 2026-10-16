package com.enderbyte.enderlib.database.core.mapping;

import com.enderbyte.enderlib.database.api.exception.MappingException;
import com.enderbyte.enderlib.database.api.mapping.ConverterRegistry;
import com.enderbyte.enderlib.database.api.mapping.TypeConverter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Converts between Java values and the representations drivers accept.
 *
 * <p>Two engine differences are handled here rather than being pushed onto the user:
 *
 * <ul>
 *   <li><strong>UUIDs.</strong> PostgreSQL and H2 have a real {@code UUID} type and take the object
 *       directly. MySQL, MariaDB and SQLite do not, so the UUID is written as its 36-character
 *       canonical string. Reading accepts all three shapes — object, string, or a 16-byte array for
 *       schemas that chose {@code BINARY(16)} — so a plugin's code is identical everywhere.</li>
 *   <li><strong>Timestamps.</strong> JDBC 4.2 engines take {@code java.time} objects directly, with
 *       instants normalised to UTC. SQLite has no date type at all, so temporal values are stored as
 *       ISO-8601 text, which sorts and compares correctly as a string.</li>
 * </ul>
 *
 * <p>Thread-safe: converters are registered during startup and read from every worker thread.
 */
public final class DefaultConverterRegistry implements ConverterRegistry {

    private final Map<Class<?>, TypeConverter<?>> converters = new ConcurrentHashMap<>();
    private final boolean nativeUuid;
    private final boolean textTemporal;

    /**
     * @param nativeUuid   {@code true} when the engine has a UUID column type
     * @param textTemporal {@code true} when temporal values must be stored as ISO-8601 text (SQLite)
     */
    public DefaultConverterRegistry(final boolean nativeUuid, final boolean textTemporal) {
        this.nativeUuid = nativeUuid;
        this.textTemporal = textTemporal;
    }

    /** @return a registry with the settings of a JDBC 4.2 engine that has native UUIDs */
    public static DefaultConverterRegistry standard() {
        return new DefaultConverterRegistry(true, false);
    }

    @Override
    public void register(final TypeConverter<?> converter) {
        converters.put(converter.javaType(), converter);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<TypeConverter<T>> converter(final Class<T> type) {
        return Optional.ofNullable((TypeConverter<T>) converters.get(type));
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Object toDatabase(final Object value) {
        if (value == null) {
            return null;
        }
        final TypeConverter converter = converters.get(value.getClass());
        if (converter != null) {
            return converter.toDatabase(value);
        }
        if (value instanceof UUID uuid) {
            return nativeUuid ? uuid : uuid.toString();
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        if (value instanceof Instant instant) {
            return textTemporal
                    ? instant.toString()
                    : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
        }
        if (value instanceof LocalDate || value instanceof LocalDateTime || value instanceof LocalTime) {
            return textTemporal ? value.toString() : value;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return textTemporal ? offsetDateTime.toInstant().toString() : offsetDateTime;
        }
        if (value instanceof java.util.Date date && !(value instanceof java.sql.Date)) {
            return new Timestamp(date.getTime());
        }
        return value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T fromDatabase(final Object value, final Class<T> type) {
        if (value == null) {
            return null;
        }
        if (type.isInstance(value) && !type.isPrimitive()) {
            return (T) value;
        }
        final TypeConverter<T> converter = (TypeConverter<T>) converters.get(type);
        if (converter != null) {
            return converter.fromDatabase(value);
        }
        return (T) convertBuiltIn(value, type);
    }

    private Object convertBuiltIn(final Object value, final Class<?> type) {
        if (type == String.class) {
            return value instanceof byte[] bytes ? new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
                    : value.toString();
        }
        if (type == UUID.class) {
            return toUuid(value);
        }
        if (type.isEnum()) {
            return toEnum(value, type);
        }
        if (type == Boolean.class || type == boolean.class) {
            return toBoolean(value);
        }
        if (type == Integer.class || type == int.class) {
            return toNumber(value).intValue();
        }
        if (type == Long.class || type == long.class) {
            return toNumber(value).longValue();
        }
        if (type == Double.class || type == double.class) {
            return toNumber(value).doubleValue();
        }
        if (type == Float.class || type == float.class) {
            return toNumber(value).floatValue();
        }
        if (type == Short.class || type == short.class) {
            return toNumber(value).shortValue();
        }
        if (type == Byte.class || type == byte.class) {
            return toNumber(value).byteValue();
        }
        if (type == BigDecimal.class) {
            return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
        }
        if (type == BigInteger.class) {
            return value instanceof BigInteger integer ? integer : new BigInteger(value.toString());
        }
        if (type == Instant.class) {
            return toInstant(value);
        }
        if (type == LocalDateTime.class) {
            return toLocalDateTime(value);
        }
        if (type == LocalDate.class) {
            return toLocalDate(value);
        }
        if (type == LocalTime.class) {
            return toLocalTime(value);
        }
        if (type == OffsetDateTime.class) {
            final Instant instant = toInstant(value);
            return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
        }
        if (type == byte[].class) {
            return toBytes(value);
        }
        throw new MappingException("Cannot convert a " + value.getClass().getName()
                + " to " + type.getName() + ". Register a TypeConverter for this type.");
    }

    private static UUID toUuid(final Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String text) {
            try {
                return UUID.fromString(text);
            } catch (final IllegalArgumentException e) {
                throw new MappingException("Value is not a valid UUID string", e);
            }
        }
        if (value instanceof byte[] bytes) {
            if (bytes.length != 16) {
                throw new MappingException("Expected 16 bytes for a UUID but got " + bytes.length);
            }
            final ByteBuffer buffer = ByteBuffer.wrap(bytes);
            return new UUID(buffer.getLong(), buffer.getLong());
        }
        throw new MappingException("Cannot read a UUID from a " + value.getClass().getName());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object toEnum(final Object value, final Class<?> type) {
        if (type.isInstance(value)) {
            return value;
        }
        final String name = value.toString();
        try {
            return Enum.valueOf((Class<? extends Enum>) type, name);
        } catch (final IllegalArgumentException e) {
            // Tolerate ordinal storage, which older plugin schemas sometimes use.
            if (value instanceof Number number) {
                final Object[] constants = type.getEnumConstants();
                final int ordinal = number.intValue();
                if (ordinal >= 0 && ordinal < constants.length) {
                    return constants[ordinal];
                }
            }
            throw new MappingException("'" + name + "' is not a constant of " + type.getSimpleName(), e);
        }
    }

    private static boolean toBoolean(final Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        final String text = value.toString();
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "t".equalsIgnoreCase(text)
                || "yes".equalsIgnoreCase(text);
    }

    private static Number toNumber(final Object value) {
        if (value instanceof Number number) {
            return number;
        }
        if (value instanceof Boolean flag) {
            return flag ? 1 : 0;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (final NumberFormatException e) {
            throw new MappingException("Cannot read a number from '" + value.getClass().getSimpleName() + "'", e);
        }
    }

    private static Instant toInstant(final Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toInstant(ZoneOffset.UTC);
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant();
        }
        if (value instanceof Number number) {
            return Instant.ofEpochMilli(number.longValue());
        }
        final String text = value.toString();
        try {
            return Instant.parse(text);
        } catch (final DateTimeParseException e) {
            // SQLite and some legacy schemas store "yyyy-MM-dd HH:mm:ss[.SSS]" without a zone.
            try {
                return LocalDateTime.parse(text.replace(' ', 'T')).toInstant(ZoneOffset.UTC);
            } catch (final DateTimeParseException nested) {
                throw new MappingException("Cannot read an Instant from '" + text + '\'', nested);
            }
        }
    }

    private static LocalDateTime toLocalDateTime(final Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toLocalDateTime();
        }
        final String text = value.toString();
        try {
            return LocalDateTime.parse(text.replace(' ', 'T'));
        } catch (final DateTimeParseException e) {
            return LocalDateTime.ofInstant(toInstant(value), ZoneOffset.UTC);
        }
    }

    private static LocalDate toLocalDate(final Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        try {
            return LocalDate.parse(value.toString());
        } catch (final DateTimeParseException e) {
            return toLocalDateTime(value).toLocalDate();
        }
    }

    private static LocalTime toLocalTime(final Object value) {
        if (value instanceof LocalTime localTime) {
            return localTime;
        }
        if (value instanceof java.sql.Time time) {
            return time.toLocalTime();
        }
        try {
            return LocalTime.parse(value.toString());
        } catch (final DateTimeParseException e) {
            return toLocalDateTime(value).toLocalTime();
        }
    }

    private static byte[] toBytes(final Object value) {
        if (value instanceof byte[] bytes) {
            return bytes;
        }
        if (value instanceof UUID uuid) {
            return ByteBuffer.allocate(16)
                    .putLong(uuid.getMostSignificantBits())
                    .putLong(uuid.getLeastSignificantBits())
                    .array();
        }
        if (value instanceof String text) {
            return text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        throw new MappingException("Cannot read bytes from a " + value.getClass().getName());
    }
}
