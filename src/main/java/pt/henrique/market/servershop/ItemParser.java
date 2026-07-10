package pt.henrique.market.servershop;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.logging.Logger;

/**
 * Parses item identifiers from config into ItemStacks.
 * <p>
 * Supports two formats:
 * <ul>
 *   <li><b>Format A</b> (simple material): {@code DIAMOND}, {@code FIREWORK_ROCKET}</li>
 *   <li><b>Format B</b> (minecraft item string with components):
 *       {@code minecraft:firework_rocket[minecraft:fireworks={flight_duration:3}]}</li>
 * </ul>
 * <p>
 * Format B uses Paper's built-in {@code Bukkit.getItemFactory().createItemStack(String)}
 * which accepts the modern Minecraft item format including data components.
 */
public final class ItemParser {
    private ItemParser() {}
    /**
     * Result of parsing an item string.
     *
     * @param itemStack   the parsed ItemStack (1 count), or null if parsing failed
     * @param material    the base Material
     * @param id          the normalized ID used as the registry key
     * @param displayName a human-readable display name
     * @param hasComponents whether the item was defined with component data
     * @param componentString the raw component bracket section (e.g. {@code [minecraft:fireworks={flight_duration:3}]}), or null
     */
    public record ParseResult(
            ItemStack itemStack,
            Material material,
            String id,
            String displayName,
            boolean hasComponents,
            String componentString
    ) {}
    /**
     * Parses a config item string into a ParseResult.
     * <p>
     * Accepts both Format A and Format B.
     * Returns null if parsing fails (with a logged warning).
     *
     * @param raw    the raw string from config (e.g. "DIAMOND" or "minecraft:firework_rocket[...]")
     * @param logger logger for warnings
     * @return ParseResult or null on failure
     */
    public static ParseResult parse(String raw, Logger logger) {
        if (raw == null || raw.isBlank()) return null;
        String trimmed = raw.trim();
        // Detect Format B: starts with a namespace (contains ':') or has brackets
        if (isMinecraftItemString(trimmed)) {
            return parseComponentString(trimmed, logger);
        }
        // Format A: plain material name
        return parseMaterial(trimmed, logger);
    }
    /**
     * Checks whether the string looks like a modern minecraft item string
     * (e.g. "minecraft:diamond" or "minecraft:firework_rocket[...]").
     */
    public static boolean isMinecraftItemString(String s) {
        // Has namespace colon before any bracket, or starts with "minecraft:"
        if (s.contains(":")) return true;
        if (s.contains("[")) return true;
        return false;
    }
    // ==================== Format A: plain Material ====================
    private static ParseResult parseMaterial(String raw, Logger logger) {
        String upper = raw.toUpperCase(Locale.ROOT);
        try {
            Material material = Material.valueOf(upper);
            if (!material.isItem() || material.isAir()) {
                logger.warning("[ItemParser] Material is not a valid item: " + upper);
                return null;
            }
            ItemStack stack = new ItemStack(material, 1);
            String displayName = MaterialUtil.formatName(material);
            return new ParseResult(stack, material, upper, displayName, false, null);
        } catch (IllegalArgumentException e) {
            logger.warning("[ItemParser] Unknown material: " + upper);
            return null;
        }
    }
    // ==================== Format B: minecraft item string ====================
    private static ParseResult parseComponentString(String raw, Logger logger) {
        // Normalize: ensure namespace prefix
        String itemString = raw;
        if (!itemString.contains(":")) {
            // e.g. "firework_rocket[...]" -> "minecraft:firework_rocket[...]"
            itemString = "minecraft:" + itemString.toLowerCase(Locale.ROOT);
        }
        // Extract the base item ID (before the '[') for Material lookup
        String basePart;
        String componentPart = null;
        int bracketIdx = itemString.indexOf('[');
        if (bracketIdx >= 0) {
            basePart = itemString.substring(0, bracketIdx);
            componentPart = itemString.substring(bracketIdx); // includes '[' ... ']'
        } else {
            basePart = itemString;
        }
        // Resolve Material from the namespaced ID
        Material material = materialFromNamespacedId(basePart);
        if (material == null || !material.isItem() || material.isAir()) {
            logger.warning("[ItemParser] Cannot resolve material from: " + basePart + " (full: " + raw + ")");
            return null;
        }
        // Build the ItemStack via Paper API
        ItemStack stack;
        try {
            stack = Bukkit.getItemFactory().createItemStack(itemString);
        } catch (Exception e) {
            logger.warning("[ItemParser] Failed to parse item string: " + itemString + " - " + e.getMessage());
            return null;
        }
        if (stack.getType().isAir()) {
            logger.warning("[ItemParser] Item string resolved to AIR: " + itemString);
            return null;
        }
        // Build a normalized ID for the registry
        String normalizedId = normalizeId(itemString);
        String displayName = buildDisplayName(material, componentPart);
        return new ParseResult(stack, material, normalizedId, displayName, componentPart != null, componentPart);
    }
    /**
     * Resolves a Material from a namespaced ID like "minecraft:firework_rocket".
     */
    private static Material materialFromNamespacedId(String namespacedId) {
        String id = namespacedId;
        // Strip "minecraft:" prefix if present
        if (id.startsWith("minecraft:")) {
            id = id.substring("minecraft:".length());
        }
        // Convert to upper-case enum name
        String enumName = id.toUpperCase(Locale.ROOT);
        try {
            return Material.valueOf(enumName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    /**
     * Builds a normalized string ID suitable for use as a HashMap key.
     * E.g. "minecraft:firework_rocket[minecraft:fireworks={flight_duration:3}]"
     * becomes "minecraft:firework_rocket[minecraft:fireworks={flight_duration:3}]" (lowercase, trimmed).
     */
    private static String normalizeId(String itemString) {
        return itemString.toLowerCase(Locale.ROOT).trim();
    }
    /**
     * Builds a human-readable display name for a component item.
     */
    private static String buildDisplayName(Material material, String componentPart) {
        String baseName = MaterialUtil.formatName(material);
        if (componentPart == null || componentPart.isEmpty()) {
            return baseName;
        }
        // Try to extract useful info from common components
        String info = extractComponentInfo(componentPart);
        if (info != null) {
            return baseName + " " + info;
        }
        return baseName;
    }
    /**
     * Attempts to extract a short description from component data.
     * Examples:
     *   [minecraft:fireworks={flight_duration:3}] -> "(Flight 3)"
     *   [minecraft:potion_contents={potion:"minecraft:strong_healing"}] -> "(Strong Healing)"
     */
    private static String extractComponentInfo(String componentPart) {
        if (componentPart == null) return null;
        // Flight duration
        int flightIdx = componentPart.indexOf("flight_duration:");
        if (flightIdx >= 0) {
            int start = flightIdx + "flight_duration:".length();
            StringBuilder num = new StringBuilder();
            for (int i = start; i < componentPart.length(); i++) {
                char c = componentPart.charAt(i);
                if (Character.isDigit(c)) num.append(c);
                else break;
            }
            if (!num.isEmpty()) {
                return "(Flight " + num + ")";
            }
        }
        // Potion contents
        int potionIdx = componentPart.indexOf("potion:\"");
        if (potionIdx >= 0) {
            int start = potionIdx + "potion:\"".length();
            int end = componentPart.indexOf('"', start);
            if (end > start) {
                String potionId = componentPart.substring(start, end);
                // strip namespace
                if (potionId.contains(":")) potionId = potionId.substring(potionId.indexOf(':') + 1);
                return "(" + MaterialUtil.formatName(potionId) + ")";
            }
        }
        // Custom name
        int nameIdx = componentPart.indexOf("custom_name:");
        if (nameIdx >= 0) {
            int start = componentPart.indexOf('"', nameIdx);
            if (start >= 0) {
                int end = componentPart.indexOf('"', start + 1);
                if (end > start) {
                    return "(" + componentPart.substring(start + 1, end) + ")";
                }
            }
        }
        return null;
    }
    /**
     * Checks if two ItemStacks match in an EXACT manner (material + all components/meta).
     * Uses isSimilar which compares type and meta.
     */
    public static boolean exactMatch(ItemStack template, ItemStack candidate) {
        if (template == null || candidate == null) return false;
        // isSimilar ignores stack size, compares type + meta
        return template.isSimilar(candidate);
    }
    /**
     * Checks if two ItemStacks match by material only.
     */
    public static boolean materialMatch(ItemStack template, ItemStack candidate) {
        if (template == null || candidate == null) return false;
        return template.getType() == candidate.getType();
    }
}