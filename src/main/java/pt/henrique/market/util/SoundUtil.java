package pt.henrique.market.util;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Utility class for playing sounds.
 * Handles Sound API changes gracefully for Paper 1.21+.
 */
public class SoundUtil {

    /**
     * Plays a sound to a player by name.
     *
     * @param player The player
     * @param soundName The sound name (e.g., "UI_BUTTON_CLICK" or "ui.button.click")
     * @param volume Volume (0.0 - 1.0)
     * @param pitch Pitch (0.5 - 2.0)
     */
    public static void playSound(Player player, String soundName, float volume, float pitch) {
        if (player == null || soundName == null || soundName.isEmpty()) {
            return;
        }

        try {
            Sound sound = findSound(soundName);
            if (sound != null) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        } catch (Exception ignored) {
            // Sound not found or error playing, ignore silently
        }
    }

    /**
     * Plays a sound with default volume and pitch.
     */
    public static void playSound(Player player, String soundName) {
        playSound(player, soundName, 0.5f, 1.0f);
    }

    /**
     * Finds a Sound by name using the Registry API (Paper 1.21+).
     * Supports both formats: "UI_BUTTON_CLICK" and "ui.button.click"
     */
    private static Sound findSound(String name) {
        if (name == null) return null;

        // Convert legacy format (UI_BUTTON_CLICK) to namespaced format (ui.button.click)
        String key = name.toLowerCase().replace("_", ".");

        // Try direct lookup with the converted key
        Sound sound = Registry.SOUNDS.get(NamespacedKey.minecraft(key));
        if (sound != null) {
            return sound;
        }

        // Try some common mappings for legacy sound names
        String mappedKey = mapLegacySoundName(name);
        if (mappedKey != null) {
            sound = Registry.SOUNDS.get(NamespacedKey.minecraft(mappedKey));
            if (sound != null) {
                return sound;
            }
        }

        // Fallback: try the original name as-is (lowercase)
        return Registry.SOUNDS.get(NamespacedKey.minecraft(name.toLowerCase()));
    }

    /**
     * Maps legacy enum-style sound names to their registry keys.
     */
    private static String mapLegacySoundName(String legacyName) {
        if (legacyName == null) return null;

        String upper = legacyName.toUpperCase();

        return switch (upper) {
            case "UI_BUTTON_CLICK" -> "ui.button.click";
            case "ENTITY_PLAYER_LEVELUP" -> "entity.player.levelup";
            case "ENTITY_VILLAGER_NO" -> "entity.villager.no";
            case "ENTITY_EXPERIENCE_ORB_PICKUP" -> "entity.experience_orb.pickup";
            case "BLOCK_NOTE_BLOCK_PLING" -> "block.note_block.pling";
            case "ENTITY_ITEM_PICKUP" -> "entity.item.pickup";
            case "BLOCK_CHEST_OPEN" -> "block.chest.open";
            case "BLOCK_CHEST_CLOSE" -> "block.chest.close";
            case "ENTITY_ARROW_HIT_PLAYER" -> "entity.arrow.hit_player";
            case "BLOCK_ANVIL_USE" -> "block.anvil.use";
            default -> null;
        };
    }

    /**
     * Common sounds for easy access
     */
    public static void playClickSound(Player player) {
        playSound(player, "ui.button.click", 0.5f, 1.0f);
    }

    public static void playSuccessSound(Player player) {
        playSound(player, "entity.player.levelup", 0.5f, 1.5f);
    }

    public static void playErrorSound(Player player) {
        playSound(player, "entity.villager.no", 0.5f, 1.0f);
    }

    public static void playPurchaseSound(Player player) {
        playSound(player, "entity.experience_orb.pickup", 0.5f, 1.0f);
    }
}
