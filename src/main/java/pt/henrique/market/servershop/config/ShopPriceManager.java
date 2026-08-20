package pt.henrique.market.servershop.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import pt.henrique.market.Market;
import pt.henrique.market.servershop.ItemParser;
import pt.henrique.market.servershop.MaterialUtil;
import pt.henrique.market.servershop.model.ShopCategory;
import pt.henrique.market.servershop.model.ShopItem;
import pt.henrique.market.servershop.model.ShopPriceEntry;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Manages the items.yml and prices.yml configuration files for the server shop.
 * Loads categories, item assignments, and per-item price overrides.
 * Computes effective buy/sell prices using the multiplier chain:
 *   1. Per-item override (explicit buy-price / sell-price)
 *   2. Category default price + category sell-multiplier
 *   3. Global default price + global sell-multiplier
 */
public class ShopPriceManager {

    private final Market plugin;
    private FileConfiguration itemsConfig;
    private FileConfiguration pricesConfig;

    private final List<ShopCategory> categories = new ArrayList<>();
    private final Map<String, ShopCategory> itemCategoryMap = new HashMap<>();
    private final Map<String, ShopPriceEntry> priceCache = new HashMap<>();
    private final Map<String, Double> categoryDefaultPrices = new HashMap<>();
    private final Map<String, ShopItem> itemRegistry = new LinkedHashMap<>();

    // Settings from config.yml
    private double defaultSellMultiplier;
    private boolean includeSpecialItems;

    public ShopPriceManager(Market plugin) {
        this.plugin = plugin;
    }

    /**
     * Reloads items.yml and prices.yml from disk and rebuilds all caches.
     */
    public void reload() {
        categories.clear();
        itemCategoryMap.clear();
        priceCache.clear();
        categoryDefaultPrices.clear();
        itemRegistry.clear();
        warnedNonItemMaterials.clear();

        // Read settings from main config
        defaultSellMultiplier = plugin.getConfig().getDouble("server-shop.default-sell-multiplier", 0.25);
        includeSpecialItems = plugin.getConfig().getBoolean("server-shop.include-special-items", false);

        // Save default files if not present
        File itemsFile = new File(plugin.getDataFolder(), "items.yml");
        if (!itemsFile.exists()) {
            plugin.saveResource("items.yml", false);
        }
        File pricesFile = new File(plugin.getDataFolder(), "prices.yml");
        if (!pricesFile.exists()) {
            plugin.saveResource("prices.yml", false);
        }

        itemsConfig = YamlConfiguration.loadConfiguration(itemsFile);
        pricesConfig = YamlConfiguration.loadConfiguration(pricesFile);

        // Load defaults from jar
        InputStream itemsDefault = plugin.getResource("items.yml");
        if (itemsDefault != null) {
            YamlConfiguration defaultItemsConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(itemsDefault, StandardCharsets.UTF_8));
            itemsConfig.setDefaults(defaultItemsConfig);
        }
        InputStream pricesDefault = plugin.getResource("prices.yml");
        if (pricesDefault != null) {
            YamlConfiguration defaultPricesConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(pricesDefault, StandardCharsets.UTF_8));
            pricesConfig.setDefaults(defaultPricesConfig);
        }

        loadCategories();
        loadItemAssignments();
        assignUnassignedMaterials();
        loadDefaultPrices();
        buildPriceCache();

        plugin.getLogger().info("[ServerShop] Loaded " + categories.size() + " categories, " +
                itemCategoryMap.size() + " items, " + priceCache.size() + " priced items.");
    }

    private void loadCategories() {
        ConfigurationSection catSection = itemsConfig.getConfigurationSection("categories");
        if (catSection == null) {
            plugin.getLogger().warning("[ServerShop] No categories section found in items.yml!");
            return;
        }

        for (String catId : catSection.getKeys(false)) {
            ConfigurationSection section = catSection.getConfigurationSection(catId);
            if (section == null) continue;

            ShopCategory category = new ShopCategory(catId);
            category.setDisplayName(section.getString("display-name", catId));
            try {
                category.setIcon(Material.valueOf(section.getString("icon", "CHEST").toUpperCase()));
            } catch (IllegalArgumentException e) {
                category.setIcon(Material.CHEST);
            }
            category.setDescription(section.getString("description", ""));
            category.setSellMultiplier(section.getDouble("sell-multiplier", defaultSellMultiplier));
            category.setBuyEnabled(section.getBoolean("buy-enabled", true));
            category.setSellEnabled(section.getBoolean("sell-enabled", true));
            categories.add(category);
        }
    }

    private final Set<String> warnedNonItemMaterials = new HashSet<>();

    // Match mode for selling: MATERIAL_ONLY or EXACT
    private String matchMode = "MATERIAL_ONLY";
    // Strict parsing: if true, log warnings for invalid items; else skip silently
    private boolean strictParsing = false;

    private void loadItemAssignments() {
        matchMode = plugin.getConfig().getString("server-shop.items.match-mode", "MATERIAL_ONLY").toUpperCase(Locale.ROOT);
        strictParsing = plugin.getConfig().getBoolean("server-shop.items.parse-strict", false);

        ConfigurationSection assignSection = itemsConfig.getConfigurationSection("items");
        if (assignSection == null) return;

        for (String catId : assignSection.getKeys(false)) {
            ShopCategory category = getCategoryById(catId);
            if (category == null) continue;

            List<String> itemNames = assignSection.getStringList(catId);
            for (String itemName : itemNames) {
                try {
                    // Check if this is a Format B item (minecraft string with components)
                    if (ItemParser.isMinecraftItemString(itemName)) {
                        ItemParser.ParseResult result = ItemParser.parse(itemName, plugin.getLogger());
                        if (result == null) {
                            if (strictParsing) {
                                plugin.getLogger().warning("[ServerShop] Strict mode: failed to parse item: " + itemName);
                            }
                            continue;
                        }
                        // Use the normalized ID as key
                        String itemId = result.id();
                        ShopItem shopItem = ShopItem.fromComponentItem(
                                itemId, result.material(), result.displayName(),
                                result.itemStack(), result.componentString());
                        itemRegistry.put(itemId, shopItem);
                        category.addItem(itemId);
                        itemCategoryMap.put(itemId, category);
                    } else {
                        // Format A: legacy material or virtual item ID (ENCHANTED_BOOK_*, POTION_*, etc.)
                        String itemId = itemName.toUpperCase(Locale.ROOT);
                        ShopItem shopItem = ShopItem.fromId(itemId);
                        // Validate that the base material is a valid inventory item
                        if (!shopItem.getBaseMaterial().isItem() || shopItem.getBaseMaterial().isAir()) {
                            if (warnedNonItemMaterials.add(itemId)) {
                                plugin.getLogger().warning("[ServerShop] Skipping non-item material in shop: " + itemId);
                            }
                            continue;
                        }
                        itemRegistry.put(itemId, shopItem);
                        category.addItem(itemId);
                        itemCategoryMap.put(itemId, category);
                    }
                } catch (IllegalArgumentException e) {
                    // Item doesn't exist in this version — skip silently unless strict
                    if (strictParsing) {
                        plugin.getLogger().warning("[ServerShop] Strict mode: invalid item '" + itemName + "': " + e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Returns the configured match mode: "MATERIAL_ONLY" or "EXACT".
     */
    public String getMatchMode() {
        return matchMode;
    }

    private void assignUnassignedMaterials() {
        ShopCategory miscCategory = getCategoryById("miscellaneous");
        if (miscCategory == null) {
            miscCategory = new ShopCategory("miscellaneous");
            miscCategory.setDisplayName("Miscellaneous");
            miscCategory.setIcon(Material.CHEST);
            miscCategory.setSellMultiplier(defaultSellMultiplier);
            categories.add(miscCategory);
        }

        for (Material material : Material.values()) {
            if (!MaterialUtil.isValidShopMaterial(material)) continue;
            if (MaterialUtil.isSpecialItem(material)) continue; // Special items must be in items.yml explicitly
            String itemId = material.name();
            if (itemCategoryMap.containsKey(itemId)) continue;

            ShopItem shopItem = ShopItem.fromPlainMaterial(material);
            itemRegistry.put(itemId, shopItem);

            ShopCategory autoCategory = autoCategorize(material);
            if (autoCategory != null) {
                autoCategory.addItem(itemId);
                itemCategoryMap.put(itemId, autoCategory);
            } else {
                miscCategory.addItem(itemId);
                itemCategoryMap.put(itemId, miscCategory);
            }
        }
    }

    private ShopCategory autoCategorize(Material material) {
        String name = material.name();

        if (name.contains("SLAB") || name.contains("STAIRS") || name.contains("WALL") ||
            name.contains("PLANKS") || name.contains("LOG") || name.contains("WOOD") ||
            name.contains("CONCRETE") || name.contains("WOOL") || name.contains("GLASS_PANE") ||
            name.contains("STAINED_GLASS") || name.contains("TERRACOTTA") || name.contains("FENCE") ||
            name.contains("BRICK") || name.contains("PILLAR") || name.contains("TILE") ||
            name.contains("CHISELED") || name.contains("POLISHED") || name.contains("CUT_") ||
            name.contains("SMOOTH_") || name.contains("STRIPPED_")) {
            return getCategoryById("building_blocks");
        }

        if (name.contains("DYE") || name.contains("BED") || name.contains("CARPET") ||
            name.contains("BANNER") || name.contains("SHULKER_BOX") || name.contains("SIGN")) {
            return getCategoryById("decoration");
        }

        if ((name.contains("DOOR") || name.contains("TRAPDOOR")) && !name.contains("IRON")) {
            return getCategoryById("building_blocks");
        }

        if (name.contains("BOAT")) {
            return getCategoryById("tools");
        }

        return null;
    }

    private void loadDefaultPrices() {
        ConfigurationSection defaultSection = pricesConfig.getConfigurationSection("default-prices");
        if (defaultSection == null) return;
        for (String catId : defaultSection.getKeys(false)) {
            categoryDefaultPrices.put(catId, defaultSection.getDouble(catId, 10.0));
        }
    }

    private void buildPriceCache() {
        ConfigurationSection overridesSection = pricesConfig.getConfigurationSection("overrides");

        for (Map.Entry<String, ShopCategory> entry : itemCategoryMap.entrySet()) {
            String itemId = entry.getKey();
            ShopCategory category = entry.getValue();

            double baseBuyPrice = categoryDefaultPrices.getOrDefault(category.getId(), 10.0);
            double sellMultiplier = category.getSellMultiplier();
            boolean buyEnabled = category.isBuyEnabled();
            boolean sellEnabled = category.isSellEnabled();

            double buyPrice = baseBuyPrice;
            double sellPrice = -1; // -1 = compute from multiplier

            if (overridesSection != null) {
                // Try exact match first, then quoted key for component items
                ConfigurationSection itemSection = findOverrideSection(overridesSection, itemId);
                if (itemSection != null) {
                    if (itemSection.contains("buy-price")) buyPrice = itemSection.getDouble("buy-price");
                    if (itemSection.contains("sell-price")) sellPrice = itemSection.getDouble("sell-price");
                    if (itemSection.contains("buy-enabled")) buyEnabled = itemSection.getBoolean("buy-enabled");
                    if (itemSection.contains("sell-enabled")) sellEnabled = itemSection.getBoolean("sell-enabled");
                }
            }

            if (sellPrice < 0) {
                sellPrice = Math.round(buyPrice * sellMultiplier * 100.0) / 100.0;
            }

            priceCache.put(itemId, new ShopPriceEntry(itemId, buyPrice, sellPrice, buyEnabled, sellEnabled));
        }
    }

    /**
     * Finds the override section for an item, trying the exact key first,
     * then falling back to quoted variants that YAML may produce for component IDs.
     */
    private ConfigurationSection findOverrideSection(ConfigurationSection overrides, String itemId) {
        // Direct key match
        if (overrides.contains(itemId)) {
            return overrides.getConfigurationSection(itemId);
        }
        // For YAML, dots in keys are path separators. Component IDs won't have dots, but
        // keys with colons/brackets are valid YAML strings when quoted.
        // Try all keys for a case-insensitive match (component IDs are lowercase)
        for (String key : overrides.getKeys(false)) {
            if (key.equalsIgnoreCase(itemId)) {
                return overrides.getConfigurationSection(key);
            }
        }
        return null;
    }

    // ==================== Public API ====================

    public ShopPriceEntry getPriceEntry(String itemId) {
        ShopPriceEntry entry = priceCache.get(itemId);
        // Special items with selling disabled: expose a sell-disabled view so every
        // consumer (GUIs, chest sell scan, shulker scan) sees a consistent state.
        if (entry != null && !includeSpecialItems && isSpecialShopItem(itemId)
                && (entry.sellEnabled() || entry.sellPrice() > 0)) {
            return new ShopPriceEntry(entry.itemId(), entry.buyPrice(), -1, entry.buyEnabled(), false);
        }
        return entry;
    }

    public double getBuyPrice(String itemId) {
        ShopPriceEntry entry = priceCache.get(itemId);
        if (entry == null || !entry.buyEnabled()) return -1;
        return entry.buyPrice();
    }

    public double getSellPrice(String itemId) {
        if (!includeSpecialItems && isSpecialShopItem(itemId)) return -1;
        ShopPriceEntry entry = priceCache.get(itemId);
        if (entry == null || !entry.sellEnabled()) return -1;
        return entry.sellPrice();
    }

    public boolean isBuyable(String itemId) {
        ShopPriceEntry entry = priceCache.get(itemId);
        return entry != null && entry.buyEnabled() && entry.buyPrice() > 0;
    }

    public boolean isSellable(String itemId) {
        if (!includeSpecialItems && isSpecialShopItem(itemId)) return false;
        ShopPriceEntry entry = priceCache.get(itemId);
        return entry != null && entry.sellEnabled() && entry.sellPrice() > 0;
    }

    /**
     * True for virtual "special" items (enchanted books, potions, tipped arrows).
     * When {@code server-shop.include-special-items} is false these cannot be
     * SOLD to the server shop (renewable villager/brewing supply makes any fixed
     * sell price exploitable); buying them remains allowed.
     */
    private boolean isSpecialShopItem(String itemId) {
        ShopItem item = itemRegistry.get(itemId);
        if (item != null) {
            return switch (item.getType()) {
                case ENCHANTED_BOOK, POTION, SPLASH_POTION, LINGERING_POTION, TIPPED_ARROW -> true;
                default -> false;
            };
        }
        String upper = itemId.toUpperCase(Locale.ROOT);
        return upper.startsWith("ENCHANTED_BOOK_")
                || upper.startsWith("POTION_")
                || upper.startsWith("SPLASH_POTION_")
                || upper.startsWith("LINGERING_POTION_")
                || upper.startsWith("TIPPED_ARROW_");
    }

    public ShopCategory getCategoryFor(String itemId) { return itemCategoryMap.get(itemId); }

    public ShopCategory getCategoryById(String id) {
        for (ShopCategory cat : categories) {
            if (cat.getId().equals(id)) return cat;
        }
        return null;
    }

    public List<ShopCategory> getCategories() { return Collections.unmodifiableList(categories); }

    public Collection<ShopPriceEntry> getAllPriceEntries() { return Collections.unmodifiableCollection(priceCache.values()); }

    public List<String> getCategoryItems(ShopCategory category) {
        return new ArrayList<>(category.getItemIds());
    }

    public ShopItem getShopItem(String itemId) { return itemRegistry.get(itemId); }

    public Map<String, ShopItem> getItemRegistry() { return Collections.unmodifiableMap(itemRegistry); }

    /**
     * Sell price of the single-enchantment book row for {@code enchantment} at
     * {@code level} (e.g. {@code ENCHANTED_BOOK_EFFICIENCY_5}). When that exact
     * level has no priced row, the highest configured level at or below it is
     * used, mirroring the level fallback in {@code EnchantPriceUtil}.
     *
     * @return the sell price, or 0 when no row is priced or books are not sellable
     */
    public double getEnchantedBookSellPrice(Enchantment enchantment, int level) {
        if (enchantment == null || level < 1) return 0;
        String enchantKey = enchantment.getKey().getKey().toUpperCase(Locale.ROOT);
        for (int lvl = level; lvl >= 1; lvl--) {
            double price = getSellPrice("ENCHANTED_BOOK_" + enchantKey + "_" + lvl);
            if (price > 0) return price;
        }
        return 0;
    }

    /**
     * Price-row ID used to value and log a concrete stack that is being sold.
     * <p>
     * Identical to {@link #identifyItem} except for enchanted books carrying more
     * than one stored enchantment: those match no single {@code ENCHANTED_BOOK_*}
     * row, so they get the virtual {@link #MULTI_ENCHANT_BOOK_ID} and are priced
     * by summing their enchantments (see {@code ShopPricingService.quoteSell}).
     */
    public String identifySellItem(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;
        if (stack.getItemMeta() instanceof EnchantmentStorageMeta storage && storage.hasStoredEnchants()) {
            Map<Enchantment, Integer> stored = storage.getStoredEnchants();
            if (stored.size() == 1) {
                String single = identifyItem(stack);
                if (single != null) return single;
            }
            return MULTI_ENCHANT_BOOK_ID;
        }
        return identifyItem(stack);
    }

    /** Virtual price-row ID for enchanted books holding more than one enchantment. */
    public static final String MULTI_ENCHANT_BOOK_ID = "ENCHANTED_BOOK_MULTI";

    /**
     * Reverse-lookup: given a player's inventory ItemStack, find the matching ShopItem ID.
     * Returns null if no match found.
     *
     * Match behavior depends on server-shop.items.match-mode config:
     * - EXACT: component items must match exactly (isSimilar)
     * - MATERIAL_ONLY: any item of the same base material matches (components ignored)
     */
    public String identifyItem(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;

        Material mat = stack.getType();
        boolean exactMode = "EXACT".equalsIgnoreCase(matchMode);

        // First pass: try exact matching for component items (always preferred if they exist)
        for (Map.Entry<String, ShopItem> entry : itemRegistry.entrySet()) {
            ShopItem item = entry.getValue();
            if (item.getType() == ShopItem.ItemType.COMPONENT && item.getBaseMaterial() == mat) {
                if (item.matches(stack)) {
                    return entry.getKey();
                }
            }
        }

        // For special materials (enchanted books, potions etc.), check all registered items
        if (MaterialUtil.isSpecialItem(mat)) {
            for (Map.Entry<String, ShopItem> entry : itemRegistry.entrySet()) {
                if (entry.getValue().getBaseMaterial() == mat && entry.getValue().matches(stack)) {
                    return entry.getKey();
                }
            }
            return null;
        }

        // For plain materials, check by material name
        String plainId = mat.name();
        ShopItem item = itemRegistry.get(plainId);
        if (item != null) {
            if (exactMode) {
                if (item.matches(stack)) return plainId;
            } else {
                // MATERIAL_ONLY mode: material match is enough for plain items
                return plainId;
            }
        }

        // MATERIAL_ONLY fallback: check if any registered item has this material
        if (!exactMode) {
            for (Map.Entry<String, ShopItem> entry : itemRegistry.entrySet()) {
                if (entry.getValue().getBaseMaterial() == mat) {
                    return entry.getKey();
                }
            }
        }

        return null;
    }
}
