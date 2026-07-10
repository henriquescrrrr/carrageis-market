package pt.henrique.market.servershop;

import org.bukkit.Material;
import pt.henrique.market.servershop.model.ShopItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Utility class for Material-related operations in the server shop.
 * Provides human-readable formatting and search capabilities.
 */
public class MaterialUtil {

    /**
     * Converts a Material enum name to a human-readable format.
     * Example: DIAMOND_SWORD -> "Diamond Sword"
     */
    public static String formatName(Material material) {
        if (material == null) return "Unknown";
        return formatName(material.name());
    }

    /**
     * Converts an underscore-separated name to a human-readable format.
     */
    public static String formatName(String name) {
        if (name == null) return "Unknown";
        String[] words = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) sb.append(" ");
            String word = words[i];
            if (word.isEmpty()) continue;
            sb.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) sb.append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    /**
     * Searches for materials matching a query string.
     * Matches against formatted name and enum name (case-insensitive, partial match).
     */
    public static List<Material> search(String query) {
        if (query == null || query.isBlank()) return List.of();
        String lowerQuery = query.toLowerCase(Locale.ROOT).trim();
        List<Material> exact = new ArrayList<>();
        List<Material> startsWith = new ArrayList<>();
        List<Material> contains = new ArrayList<>();

        for (Material material : Material.values()) {
            if (!isValidShopMaterial(material)) continue;
            String formatted = formatName(material).toLowerCase(Locale.ROOT);
            String enumName = material.name().toLowerCase(Locale.ROOT);

            if (formatted.equals(lowerQuery) || enumName.equals(lowerQuery)) {
                exact.add(material);
            } else if (formatted.startsWith(lowerQuery) || enumName.startsWith(lowerQuery)) {
                startsWith.add(material);
            } else if (formatted.contains(lowerQuery) || enumName.contains(lowerQuery)) {
                contains.add(material);
            }
        }
        List<Material> results = new ArrayList<>(exact);
        results.addAll(startsWith);
        results.addAll(contains);
        return results;
    }

    /**
     * Searches for materials matching a query string, applying locale aliases.
     */
    public static List<Material> search(String query, Map<String, String> aliases) {
        if (query == null || query.isBlank()) return List.of();
        if (aliases == null || aliases.isEmpty()) return search(query);

        String lowerQuery = query.toLowerCase(Locale.ROOT).trim();

        if (aliases.containsKey(lowerQuery)) {
            String translated = aliases.get(lowerQuery);
            List<Material> results = search(translated);
            if (!results.isEmpty()) return results;
        }

        String[] words = lowerQuery.split("\\s+");
        String[] translatedWords = new String[words.length];
        boolean anyTranslated = false;
        for (int i = 0; i < words.length; i++) {
            if (aliases.containsKey(words[i])) {
                translatedWords[i] = aliases.get(words[i]);
                anyTranslated = true;
            } else {
                translatedWords[i] = words[i];
            }
        }

        if (words.length == 1) {
            if (anyTranslated) {
                List<Material> results = search(translatedWords[0]);
                if (!results.isEmpty()) return results;
            }
            return search(words[0]);
        }

        String translatedQuery = String.join(" ", translatedWords);
        List<Material> directResults = search(translatedQuery);
        if (!directResults.isEmpty()) return directResults;

        List<Material> andResults = new ArrayList<>();
        for (Material material : Material.values()) {
            if (!isValidShopMaterial(material)) continue;
            String formatted = formatName(material).toLowerCase(Locale.ROOT);
            String enumName = material.name().toLowerCase(Locale.ROOT);
            boolean allMatch = true;
            for (String word : translatedWords) {
                if (!formatted.contains(word) && !enumName.contains(word)) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) andResults.add(material);
        }
        return andResults;
    }

    /**
     * Searches across all registered shop items (including virtual ones like enchanted books)
     * by display name and ID. Returns matching item IDs.
     */
    public static List<String> searchItems(String query, Map<String, ShopItem> registry) {
        if (query == null || query.isBlank() || registry == null) return List.of();
        String lowerQuery = query.toLowerCase(Locale.ROOT).trim();
        List<String> exact = new ArrayList<>();
        List<String> startsWith = new ArrayList<>();
        List<String> contains = new ArrayList<>();

        for (Map.Entry<String, ShopItem> entry : registry.entrySet()) {
            String itemId = entry.getKey();
            ShopItem item = entry.getValue();
            String displayName = item.getDisplayName().toLowerCase(Locale.ROOT);
            String idLower = itemId.toLowerCase(Locale.ROOT);

            if (displayName.equals(lowerQuery) || idLower.equals(lowerQuery)) {
                exact.add(itemId);
            } else if (displayName.startsWith(lowerQuery) || idLower.startsWith(lowerQuery)) {
                startsWith.add(itemId);
            } else if (displayName.contains(lowerQuery) || idLower.contains(lowerQuery)) {
                contains.add(itemId);
            }
        }
        List<String> results = new ArrayList<>(exact);
        results.addAll(startsWith);
        results.addAll(contains);
        return results;
    }

    /**
     * Searches across all registered shop items with locale alias support.
     */
    public static List<String> searchItems(String query, Map<String, ShopItem> registry, Map<String, String> aliases) {
        if (query == null || query.isBlank()) return List.of();
        if (aliases == null || aliases.isEmpty()) return searchItems(query, registry);

        String lowerQuery = query.toLowerCase(Locale.ROOT).trim();

        // Try full-phrase alias first
        if (aliases.containsKey(lowerQuery)) {
            String translated = aliases.get(lowerQuery);
            List<String> results = searchItems(translated, registry);
            if (!results.isEmpty()) return results;
        }

        // Split into words and translate each
        String[] words = lowerQuery.split("\\s+");
        String[] translatedWords = new String[words.length];
        boolean anyTranslated = false;
        for (int i = 0; i < words.length; i++) {
            if (aliases.containsKey(words[i])) {
                translatedWords[i] = aliases.get(words[i]);
                anyTranslated = true;
            } else {
                translatedWords[i] = words[i];
            }
        }

        if (words.length == 1) {
            if (anyTranslated) {
                List<String> results = searchItems(translatedWords[0], registry);
                if (!results.isEmpty()) return results;
            }
            return searchItems(words[0], registry);
        }

        // Multi-word: try translated query
        String translatedQuery = String.join(" ", translatedWords);
        List<String> directResults = searchItems(translatedQuery, registry);
        if (!directResults.isEmpty()) return directResults;

        // AND matching: every translated word must be contained in the display name or ID
        List<String> andResults = new ArrayList<>();
        for (Map.Entry<String, ShopItem> entry : registry.entrySet()) {
            String itemId = entry.getKey();
            ShopItem item = entry.getValue();
            String displayName = item.getDisplayName().toLowerCase(Locale.ROOT);
            String idLower = itemId.toLowerCase(Locale.ROOT);
            boolean allMatch = true;
            for (String word : translatedWords) {
                if (!displayName.contains(word) && !idLower.contains(word)) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) andResults.add(itemId);
        }
        return andResults;
    }

    /**
     * Checks if a material is valid for inclusion in the server shop.
     */
    public static boolean isValidShopMaterial(Material material) {
        if (material == null) return false;
        if (material.isAir()) return false;
        if (material.name().startsWith("LEGACY_")) return false;
        if (!material.isItem()) return false;
        return switch (material) {
            case COMMAND_BLOCK, CHAIN_COMMAND_BLOCK, REPEATING_COMMAND_BLOCK,
                 COMMAND_BLOCK_MINECART, BARRIER, STRUCTURE_BLOCK, STRUCTURE_VOID,
                 JIGSAW, DEBUG_STICK, LIGHT, PETRIFIED_OAK_SLAB, KNOWLEDGE_BOOK, BUNDLE -> false;
            default -> true;
        };
    }

    /**
     * Checks if a material is a "special" item that needs extra handling
     * (enchanted books, potions, tipped arrows, etc.)
     */
    public static boolean isSpecialItem(Material material) {
        if (material == null) return false;
        return switch (material) {
            case ENCHANTED_BOOK, POTION, SPLASH_POTION, LINGERING_POTION, TIPPED_ARROW,
                 SUSPICIOUS_STEW, WRITTEN_BOOK, FILLED_MAP, FIREWORK_ROCKET, FIREWORK_STAR,
                 PLAYER_HEAD, GOAT_HORN -> true;
            default -> false;
        };
    }
}
