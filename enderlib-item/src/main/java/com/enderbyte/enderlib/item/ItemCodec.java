package com.enderbyte.enderlib.item;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;

/**
 * Binary and Base64 item serialization, version-safe across Minecraft updates (it uses Paper's
 * {@link ItemStack#serializeAsBytes()}, which records the data version and upgrades on read).
 *
 * <p>Arrays keep empty slots, so an inventory round-trips slot for slot.</p>
 */
public final class ItemCodec {

    private static final int ARRAY_MAGIC = 0xE1D0_0001;

    private ItemCodec() {
    }

    public static byte[] toBytes(ItemStack stack) {
        return stack.serializeAsBytes();
    }

    public static ItemStack fromBytes(byte[] bytes) {
        return ItemStack.deserializeBytes(bytes);
    }

    public static String toBase64(ItemStack stack) {
        return Base64.getEncoder().encodeToString(toBytes(stack));
    }

    public static ItemStack fromBase64(String base64) {
        return fromBytes(Base64.getDecoder().decode(base64));
    }

    /** Serialises an array of items; {@code null} and air entries are kept as empty slots. */
    public static byte[] arrayToBytes(@Nullable ItemStack[] items) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(ARRAY_MAGIC);
            out.writeInt(items.length);
            for (ItemStack item : items) {
                if (item == null || item.isEmpty()) {
                    out.writeInt(0);
                } else {
                    byte[] data = item.serializeAsBytes();
                    out.writeInt(data.length);
                    out.write(data);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    /** Reverse of {@link #arrayToBytes}; empty slots come back as {@link ItemStack#empty()}. */
    public static ItemStack[] arrayFromBytes(byte[] data) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            if (in.readInt() != ARRAY_MAGIC) {
                throw new IllegalArgumentException("Not an EnderLib item array");
            }
            int length = in.readInt();
            ItemStack[] out = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                int size = in.readInt();
                if (size == 0) {
                    out[i] = ItemStack.empty();
                } else {
                    byte[] item = in.readNBytes(size);
                    out[i] = ItemStack.deserializeBytes(item);
                }
            }
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String arrayToBase64(@Nullable ItemStack[] items) {
        return Base64.getEncoder().encodeToString(arrayToBytes(items));
    }

    public static ItemStack[] arrayFromBase64(String base64) {
        return arrayFromBytes(Base64.getDecoder().decode(base64));
    }

    /** Snapshot of an inventory's contents. */
    public static byte[] inventoryToBytes(Inventory inventory) {
        return arrayToBytes(inventory.getContents());
    }

    /** Restores contents written by {@link #inventoryToBytes}; extra slots are ignored, missing slots cleared. */
    public static void inventoryFromBytes(Inventory inventory, byte[] data) {
        ItemStack[] items = arrayFromBytes(data);
        ItemStack[] target = new ItemStack[inventory.getSize()];
        System.arraycopy(items, 0, target, 0, Math.min(items.length, target.length));
        inventory.setContents(target);
    }
}
