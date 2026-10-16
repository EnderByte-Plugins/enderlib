package com.enderbyte.enderlib.util;

import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataType;

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.UUID;

/**
 * {@link PersistentDataType}s Bukkit does not ship.
 */
public final class PdcTypes {

    /** UUID stored as 16 bytes. */
    public static final PersistentDataType<byte[], UUID> UUID = new PersistentDataType<>() {
        @Override
        public Class<byte[]> getPrimitiveType() {
            return byte[].class;
        }

        @Override
        public Class<java.util.UUID> getComplexType() {
            return java.util.UUID.class;
        }

        @Override
        public byte[] toPrimitive(java.util.UUID complex, PersistentDataAdapterContext context) {
            return ByteBuffer.allocate(16)
                    .putLong(complex.getMostSignificantBits())
                    .putLong(complex.getLeastSignificantBits())
                    .array();
        }

        @Override
        public java.util.UUID fromPrimitive(byte[] primitive, PersistentDataAdapterContext context) {
            ByteBuffer buf = ByteBuffer.wrap(primitive);
            return new java.util.UUID(buf.getLong(), buf.getLong());
        }
    };

    private PdcTypes() {
    }

    /** Enum stored by name; unknown names (a removed constant) read as an exception from the PDC. */
    public static <E extends Enum<E>> PersistentDataType<String, E> enumType(Class<E> type) {
        return new PersistentDataType<>() {
            @Override
            public Class<String> getPrimitiveType() {
                return String.class;
            }

            @Override
            public Class<E> getComplexType() {
                return type;
            }

            @Override
            public String toPrimitive(E complex, PersistentDataAdapterContext context) {
                return complex.name();
            }

            @Override
            public E fromPrimitive(String primitive, PersistentDataAdapterContext context) {
                return Enum.valueOf(type, primitive.toUpperCase(Locale.ROOT));
            }
        };
    }
}
