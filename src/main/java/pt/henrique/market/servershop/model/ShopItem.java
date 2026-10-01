package pt.henrique.market.servershop.model;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.Registry;
import pt.henrique.market.servershop.ContainerItemUtil;

import java.util.Locale;
import java.util.Objects;

/**
 * Represents a shop item identity. Wraps either a plain Material or a
 * virtual item (enchanted book variant, potion variant, tipped arrow variant).
 * This replaces raw Material as the item key throughout the shop system.
 */
public class ShopItem {

    public enum ItemType {
        PLAIN,
        ENCHANTED_BOOK,
        POTION,
        SPLASH_POTION,
        LINGERING_POTION,
        TIPPED_ARROW,
        /** Item defined via minecraft item string with data components (Format B). */
        COMPONENT
    }

    private final String id;
    private final Material baseMaterial;
    private final ItemType type;
    private final Enchantment enchantment;
    private final int enchantmentLevel;
    private final PotionType potionType;
    private final String displayName;
    /** Template ItemStack for COMPONENT-type items (with all components baked in). Null for other types. */
    private final ItemStack templateStack;
    /** The raw component bracket string, e.g. "[minecraft:fireworks={flight_duration:3}]". Null for non-component items. */
    private final String componentString;

    private ShopItem(String id, Material baseMaterial, ItemType type,
                     Enchantment enchantment, int enchantmentLevel,
                     PotionType potionType, String displayName) {
        this(id, baseMaterial, type, enchantment, enchantmentLevel, potionType, displayName, null, null);
    }

    private ShopItem(String id, Material baseMaterial, ItemType type,
                     Enchantment enchantment, int enchantmentLevel,
                     PotionType potionType, String displayName,
                     ItemStack templateStack, String componentString) {
        this.id = id;
        this.baseMaterial = baseMaterial;
        this.type = type;
        this.enchantment = enchantment;
        this.enchantmentLevel = enchantmentLevel;
        this.potionType = potionType;
        this.displayName = displayName;
        this.templateStack = templateStack != null ? templateStack.clone() : null;
        this.componentString = componentString;
    }

    /**
     * Parses a string ID into a ShopItem.
     * Order matters — check longer prefixes first.
     */
    public static ShopItem fromId(String id) {
        String upper = id.toUpperCase(Locale.ROOT);

        if (upper.startsWith("ENCHANTED_BOOK_")) {
            return parseEnchantedBook(upper);
        }
        if (upper.startsWith("LINGERING_POTION_")) {
            String suffix = upper.substring("LINGERING_POTION_".length());
            return parsePotionItem(upper, Material.LINGERING_POTION, ItemType.LINGERING_POTION, suffix);
        }
        if (upper.startsWith("SPLASH_POTION_")) {
            String suffix = upper.substring("SPLASH_POTION_".length());
            return parsePotionItem(upper, Material.SPLASH_POTION, ItemType.SPLASH_POTION, suffix);
        }
        if (upper.startsWith("TIPPED_ARROW_")) {
            String suffix = upper.substring("TIPPED_ARROW_".length());
            return parsePotionItem(upper, Material.TIPPED_ARROW, ItemType.TIPPED_ARROW, suffix);
        }
        if (upper.startsWith("POTION_")) {
            String suffix = upper.substring("POTION_".length());
            return parsePotionItem(upper, Material.POTION, ItemType.POTION, suffix);
        }

        // Plain material
        Material material = Material.valueOf(upper);
        return fromPlainMaterial(material);
    }

    /**
     * Creates a ShopItem for a plain material (no metadata).
     */
    public static ShopItem fromPlainMaterial(Material material) {
        return new ShopItem(
                material.name(), material, ItemType.PLAIN,
                null, 0, null, formatMaterialName(material.name())
        );
    }

    /**
     * Creates a ShopItem backed by a pre-built ItemStack with data components (Format B).
     *
     * @param id              normalized config key
     * @param material        base material
     * @param displayName     human-readable name
     * @param templateStack   the fully-built template ItemStack (components already applied)
     * @param componentString the raw bracket component string from config (may be null)
     */
    public static ShopItem fromComponentItem(String id, Material material, String displayName,
                                              ItemStack templateStack, String componentString) {
        return new ShopItem(id, material, ItemType.COMPONENT,
                null, 0, null, displayName, templateStack, componentString);
    }

    private static ShopItem parseEnchantedBook(String id) {
        // Format: ENCHANTED_BOOK_{ENCHANTMENT_NAME}_{LEVEL}
        // Strip prefix
        String rest = id.substring("ENCHANTED_BOOK_".length());

        // Split off trailing _N for level
        int lastUnderscore = rest.lastIndexOf('_');
        if (lastUnderscore < 0) {
            throw new IllegalArgumentException("Invalid enchanted book ID: " + id);
        }

        String levelStr = rest.substring(lastUnderscore + 1);
        String enchantName = rest.substring(0, lastUnderscore);
        int level;
        try {
            level = Integer.parseInt(levelStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid enchanted book ID (bad level): " + id);
        }

        // Look up enchantment by minecraft key
        String key = enchantName.toLowerCase(Locale.ROOT);
        Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
        if (enchantment == null) {
            throw new IllegalArgumentException("Unknown enchantment: " + enchantName + " in ID: " + id);
        }

        String displayName = formatEnchantmentName(enchantName) + " " + toRoman(level);
        return new ShopItem(id, Material.ENCHANTED_BOOK, ItemType.ENCHANTED_BOOK,
                enchantment, level, null, displayName);
    }

    private static ShopItem parsePotionItem(String id, Material baseMaterial, ItemType type, String suffix) {
        PotionType potionType;
        try {
            potionType = PotionType.valueOf(suffix);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown potion type: " + suffix + " in ID: " + id);
        }

        String typePrefix = switch (type) {
            case POTION -> "Potion of ";
            case SPLASH_POTION -> "Splash Potion of ";
            case LINGERING_POTION -> "Lingering Potion of ";
            case TIPPED_ARROW -> "Arrow of ";
            default -> "";
        };
        String displayName = typePrefix + formatMaterialName(suffix);

        return new ShopItem(id, baseMaterial, type, null, 0, potionType, displayName);
    }

    /**
     * Creates an ItemStack representing this ShopItem with proper metadata.
     * Returns a BARRIER item if the material is not a valid inventory item.
     * <p>
     * The amount is clamped to the item's max stack size: since 26.1 an
     * over-full stack resolves to empty, so callers that want more than one
     * stack must ask for several stacks.
     */
    public ItemStack createItemStack(int amount) {
        if (!baseMaterial.isItem() || baseMaterial.isAir()) {
            return new ItemStack(Material.BARRIER, 1);
        }

        // COMPONENT type: clone the template stack
        if (type == ItemType.COMPONENT && templateStack != null) {
            ItemStack stack = templateStack.clone();
            stack.setAmount(clampAmount(amount, stack.getMaxStackSize()));
            return stack;
        }

        ItemStack stack = new ItemStack(baseMaterial, clampAmount(amount, baseMaterial.getMaxStackSize()));

        switch (type) {
            case ENCHANTED_BOOK -> {
                EnchantmentStorageMeta meta = (EnchantmentStorageMeta) stack.getItemMeta();
                if (meta != null) {
                    meta.addStoredEnchant(enchantment, enchantmentLevel, true);
                    stack.setItemMeta(meta);
                }
            }
            case POTION, SPLASH_POTION, LINGERING_POTION, TIPPED_ARROW -> {
                PotionMeta meta = (PotionMeta) stack.getItemMeta();
                if (meta != null) {
                    meta.setBasePotionType(potionType);
                    stack.setItemMeta(meta);
                }
            }
            default -> { /* PLAIN — no metadata needed */ }
        }

        return stack;
    }

    /**
     * Checks if a player's inventory ItemStack matches this ShopItem.
     * For COMPONENT items this does an EXACT match (isSimilar) by default.
     * Use {@link #matchesMaterialOnly(ItemStack)} for material-only matching.
     */
    public boolean matches(ItemStack stack) {
        if (stack == null || stack.getType() != baseMaterial) return false;

        return switch (type) {
            case PLAIN -> !hasSignificantMeta(stack);
            case ENCHANTED_BOOK -> {
                if (!(stack.getItemMeta() instanceof EnchantmentStorageMeta meta)) yield false;
                yield meta.hasStoredEnchant(enchantment) &&
                      meta.getStoredEnchantLevel(enchantment) == enchantmentLevel &&
                      meta.getStoredEnchants().size() == 1;
            }
            case POTION, SPLASH_POTION, LINGERING_POTION, TIPPED_ARROW -> {
                if (!(stack.getItemMeta() instanceof PotionMeta meta)) yield false;
                yield meta.getBasePotionType() == potionType;
            }
            case COMPONENT -> {
                if (templateStack == null) yield false;
                yield templateStack.isSimilar(stack);
            }
        };
    }

    /**
     * Material-only matching: returns true if the candidate has the same base material,
     * ignoring all metadata/components. Useful for MATERIAL_ONLY match mode.
     */
    public boolean matchesMaterialOnly(ItemStack stack) {
        if (stack == null) return false;
        return stack.getType() == baseMaterial;
    }

    private static int clampAmount(int amount, int maxStackSize) {
        return Math.max(1, Math.min(amount, Math.max(1, maxStackSize)));
    }

    /**
     * Meta that makes a stack something other than the plain material.
     * <p>
     * A loaded shulker box, bundle, sulfur cube bucket or crossbow has no display
     * name, no lore and no enchantments, so without the nesting test a full
     * container would compare equal to an empty one even in EXACT match mode.
     */
    private static boolean hasSignificantMeta(ItemStack stack) {
        if (ContainerItemUtil.isNestingItemWithContents(stack)) return true;
        if (!stack.hasItemMeta()) return false;
        var meta = stack.getItemMeta();
        return meta.hasDisplayName() || meta.hasLore() || meta.hasEnchants();
    }

    // ==================== Getters ====================

    public String getId() { return id; }
    public Material getBaseMaterial() { return baseMaterial; }
    public ItemType getType() { return type; }
    public Enchantment getEnchantment() { return enchantment; }
    public int getEnchantmentLevel() { return enchantmentLevel; }
    public PotionType getPotionType() { return potionType; }
    public String getDisplayName() { return displayName; }
    /** Returns the template ItemStack for COMPONENT items, or null. */
    public ItemStack getTemplateStack() { return templateStack != null ? templateStack.clone() : null; }
    /** Returns the raw component string (e.g. "[minecraft:fireworks={flight_duration:3}]"), or null. */
    public String getComponentString() { return componentString; }
    /** Whether this item was defined with data components (Format B). */
    public boolean hasComponents() { return type == ItemType.COMPONENT && componentString != null; }

    // ==================== Formatting helpers ====================

    static String formatMaterialName(String name) {
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

    private static String formatEnchantmentName(String name) {
        return formatMaterialName(name);
    }

    private static String toRoman(int num) {
        return switch (num) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(num);
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ShopItem other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        String base = "ShopItem{id=" + id + ", type=" + type;
        if (componentString != null) base += ", components=" + componentString;
        return base + "}";
    }
}
