package com.enderbyte.enderlib.item;

import com.enderbyte.enderlib.database.api.mapping.ConverterRegistry;
import com.enderbyte.enderlib.database.api.mapping.TypeConverter;
import org.bukkit.inventory.ItemStack;

/**
 * Lets the EnderLib database read and write {@link ItemStack} and {@code ItemStack[]} columns
 * (stored as binary: {@code BYTEA}, {@code BLOB}, {@code VARBINARY}).
 *
 * <pre>{@code
 * ItemConverters.register(database.converters());
 * database.updateAsync("UPDATE vaults SET contents = ? WHERE owner = ?", inventory.getContents(), uuid);
 * }</pre>
 *
 * Requires the {@code enderlib-database-api} module on the classpath; this class is only loaded
 * when called.
 */
public final class ItemConverters {

    private ItemConverters() {
    }

    public static void register(ConverterRegistry registry) {
        registry.register(new TypeConverter<ItemStack>() {
            @Override
            public Class<ItemStack> javaType() {
                return ItemStack.class;
            }

            @Override
            public Object toDatabase(ItemStack value) {
                return ItemCodec.toBytes(value);
            }

            @Override
            public ItemStack fromDatabase(Object value) {
                return ItemCodec.fromBytes(bytes(value));
            }
        });
        registry.register(new TypeConverter<ItemStack[]>() {
            @Override
            public Class<ItemStack[]> javaType() {
                return ItemStack[].class;
            }

            @Override
            public Object toDatabase(ItemStack[] value) {
                return ItemCodec.arrayToBytes(value);
            }

            @Override
            public ItemStack[] fromDatabase(Object value) {
                return ItemCodec.arrayFromBytes(bytes(value));
            }
        });
    }

    private static byte[] bytes(Object value) {
        if (value instanceof byte[] b) {
            return b;
        }
        if (value instanceof java.sql.Blob blob) {
            try {
                return blob.getBytes(1, (int) blob.length());
            } catch (java.sql.SQLException e) {
                throw new IllegalArgumentException("Could not read item blob", e);
            }
        }
        if (value instanceof String s) {
            return java.util.Base64.getDecoder().decode(s);
        }
        throw new IllegalArgumentException("Cannot read an item from " + value.getClass().getName());
    }
}
