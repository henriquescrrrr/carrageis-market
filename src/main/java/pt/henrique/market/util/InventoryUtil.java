package pt.henrique.market.util;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pt.henrique.market.Market;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for inventory operations
 */
public class InventoryUtil {

    /**
     * Checks if a player has space for an item in their inventory
     *
     * @param player The player
     * @param item   The item to check
     * @return True if the player has space
     */
    public static boolean hasSpace(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return true;
        }

        // Try to add a clone and see if anything remains
        ItemStack clone = item.clone();
        Map<Integer, ItemStack> leftover = new HashMap<>();

        // Check each slot
        for (int i = 0; i < 36; i++) {
            ItemStack slot = player.getInventory().getItem(i);

            if (slot == null || slot.getType().isAir()) {
                return true;
            }

            if (slot.isSimilar(clone) && slot.getAmount() < slot.getMaxStackSize()) {
                int canAdd = slot.getMaxStackSize() - slot.getAmount();
                if (canAdd >= clone.getAmount()) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Gives an item to a player, returning any items that couldn't fit
     *
     * @param player The player
     * @param item   The item to give
     * @return Items that couldn't fit, or null if all items were added
     */
    public static ItemStack giveItem(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }

        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());

        if (leftover.isEmpty()) {
            return null;
        }

        return leftover.values().iterator().next();
    }

    /**
     * Removes a specific amount of an item from a player's inventory
     *
     * @param player   The player
     * @param item     The item type to remove
     * @param amount   The amount to remove
     * @return True if the full amount was removed
     */
    public static boolean removeItem(Player player, ItemStack item, int amount) {
        if (item == null || amount <= 0) {
            return true;
        }

        ItemStack toRemove = item.clone();
        toRemove.setAmount(amount);

        HashMap<Integer, ItemStack> notRemoved = player.getInventory().removeItem(toRemove);

        return notRemoved.isEmpty();
    }

    /**
     * Counts how many of a specific item a player has
     *
     * @param player The player
     * @param item   The item type to count
     * @return Total count
     */
    public static int countItem(Player player, ItemStack item) {
        if (item == null) {
            return 0;
        }

        int count = 0;
        for (ItemStack slot : player.getInventory().getContents()) {
            if (slot != null && slot.isSimilar(item)) {
                count += slot.getAmount();
            }
        }
        return count;
    }

    /**
     * Counts how many similar items exist in an inventory.
     * Uses strict comparison including all metadata (material, name, lore,
     * enchantments, custom model data, etc.).
     *
     * @param inventory The inventory to search
     * @param item The item to match against
     * @return Total count of matching items
     */
    public static int countSimilarItems(org.bukkit.inventory.Inventory inventory, ItemStack item) {
        if (item == null || inventory == null) {
            return 0;
        }

        int count = 0;
        for (ItemStack slot : inventory.getContents()) {
            if (slot != null && slot.isSimilar(item)) {
                count += slot.getAmount();
            }
        }
        return count;
    }

    /**
     * Gets a display name for an item, using material name if custom name is not set
     *
     * @param item The item
     * @return Display name
     */
    public static String getDisplayName(ItemStack item) {
        if (item == null) {
            return "Unknown";
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return TextUtil.stripColor(meta.getDisplayName());
        }

        return formatMaterialName(item.getType());
    }

    /**
     * Formats a material name into a readable string
     *
     * @param material The material
     * @return Formatted name (e.g., "Diamond Sword")
     */
    public static String formatMaterialName(Material material) {
        String name = material.name().replace("_", " ").toLowerCase();
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : name.toCharArray()) {
            if (c == ' ') {
                result.append(c);
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    /**
     * Determines the category of an item for filtering purposes
     *
     * @param item The item
     * @return Category name
     */
    public static ItemCategory getCategory(ItemStack item) {
        if (item == null) {
            return ItemCategory.MISC;
        }

        Material material = item.getType();
        String name = material.name();

        // Check for enchantments first
        if (item.hasItemMeta() && item.getItemMeta().hasEnchants()) {
            return ItemCategory.ENCHANTED;
        }

        // Weapons
        if (name.endsWith("_SWORD") || name.endsWith("_AXE") || name.equals("BOW") ||
            name.equals("CROSSBOW") || name.equals("TRIDENT") || name.equals("MACE")) {
            return ItemCategory.WEAPONS;
        }

        // Armor
        if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") ||
            name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS") ||
            name.equals("SHIELD") || name.equals("ELYTRA")) {
            return ItemCategory.ARMOR;
        }

        // Tools
        if (name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL") ||
            name.endsWith("_HOE") || name.equals("FISHING_ROD") ||
            name.equals("FLINT_AND_STEEL") || name.equals("SHEARS")) {
            return ItemCategory.TOOLS;
        }

        // Blocks
        if (material.isBlock()) {
            return ItemCategory.BLOCKS;
        }

        // Food
        if (material.isEdible()) {
            return ItemCategory.FOOD;
        }

        // Potions
        if (name.contains("POTION") || name.equals("DRAGON_BREATH")) {
            return ItemCategory.POTIONS;
        }

        return ItemCategory.MISC;
    }

    public enum ItemCategory {
        ALL("All Items", Material.CHEST),
        WEAPONS("Weapons", Material.DIAMOND_SWORD),
        ARMOR("Armor", Material.DIAMOND_CHESTPLATE),
        TOOLS("Tools", Material.DIAMOND_PICKAXE),
        BLOCKS("Blocks", Material.GRASS_BLOCK),
        FOOD("Food", Material.GOLDEN_APPLE),
        POTIONS("Potions", Material.POTION),
        ENCHANTED("Enchanted", Material.ENCHANTED_BOOK),
        MISC("Miscellaneous", Material.ENDER_PEARL);

        private final String displayName;
        private final Material icon;

        ItemCategory(String displayName, Material icon) {
            this.displayName = displayName;
            this.icon = icon;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Material getIcon() {
            return icon;
        }
    }
}

