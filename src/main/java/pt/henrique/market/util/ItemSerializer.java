package pt.henrique.market.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * Utility class for serializing and deserializing ItemStacks to/from Base64 strings
 */
public class ItemSerializer {

    /**
     * Serializes an ItemStack to a Base64 encoded string
     *
     * @param item The ItemStack to serialize
     * @return Base64 encoded string representation
     * @throws IOException If serialization fails
     */
    public static String serialize(ItemStack item) throws IOException {
        if (item == null) {
            return null;
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {

            dataOutput.writeObject(item);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        }
    }

    /**
     * Deserializes a Base64 encoded string to an ItemStack
     *
     * @param data The Base64 encoded string
     * @return The deserialized ItemStack
     * @throws IOException If deserialization fails
     * @throws ClassNotFoundException If the class is not found
     */
    public static ItemStack deserialize(String data) throws IOException, ClassNotFoundException {
        if (data == null || data.isEmpty()) {
            return null;
        }

        byte[] bytes = Base64.getDecoder().decode(data);

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {

            return (ItemStack) dataInput.readObject();
        }
    }

    /**
     * Safely deserializes an ItemStack, returning null if any error occurs
     *
     * @param data The Base64 encoded string
     * @return The deserialized ItemStack or null if failed
     */
    public static ItemStack deserializeSafe(String data) {
        try {
            return deserialize(data);
        } catch (Exception e) {
            return null;
        }
    }
}

