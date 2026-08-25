package me.sirius.ffa.inventory;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public final class ItemCodec {

    private ItemCodec() {
    }

    public static String encode(ItemStack[] items) {
        if (items == null) {
            return "";
        }
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {

            out.writeInt(items.length);
            for (ItemStack item : items) {
                out.writeObject(item == null || item.getType().isAir() ? null : item);
            }
            out.flush();
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException e) {
            throw new CodecException("No se pudieron serializar los ítems", e);
        }
    }

    public static ItemStack[] decode(String data) {
        if (data == null || data.isBlank()) {
            return new ItemStack[0];
        }
        try (ByteArrayInputStream bytes = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream in = new BukkitObjectInputStream(bytes)) {

            ItemStack[] items = new ItemStack[in.readInt()];
            for (int i = 0; i < items.length; i++) {
                items[i] = (ItemStack) in.readObject();
            }
            return items;
        } catch (IOException | ClassNotFoundException | IllegalArgumentException e) {
            throw new CodecException("No se pudieron leer los ítems guardados", e);
        }
    }

    public static String encodeOne(ItemStack item) {
        return encode(new ItemStack[]{item});
    }

    public static ItemStack decodeOne(String data) {
        ItemStack[] items = decode(data);
        return items.length == 0 ? null : items[0];
    }

    public static ItemStack[] copy(ItemStack[] items) {
        ItemStack[] copy = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            copy[i] = items[i] == null ? null : items[i].clone();
        }
        return copy;
    }

    public static final class CodecException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        CodecException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
