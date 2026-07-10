package pt.henrique.market.servershop.service;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pt.henrique.market.Market;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Computes enchantment-based bonus values when selling items to the Server Market.
 * <p>
 * <b>Rule:</b> For each enchantment on the item, add a configurable percentage
 * ({@code server-shop.enchant-sell-bonus.percent}, default 5%) of the <em>store buy
 * price</em> of that enchantment (at its level) to the sell value. The total bonus
 * per item is capped by {@code server-shop.enchant-sell-bonus.cap} (currency units,
 * -1 = uncapped).
 * <p>
 * Enchantment buy prices are configured in {@code prices.yml} under
 * {@code enchant-prices:}. Each enchantment can define either:
 * <ul>
 *   <li>{@code perLevelBuyCents:} — a map of level→price (in cents)</li>
 *   <li>{@code buyCents:} — a flat price for any level</li>
 * </ul>
 * <p>
 * If no price is configured for an enchantment, its bonus is 0.
 *
 * @see DurabilityPriceUtil
 */
public class EnchantPriceUtil {

    private final Market plugin;

    /**
     * Map of enchantment key → (level → buy-price-in-cents).
     * Populated from prices.yml's enchant-prices section.
     */
    private final Map<String, Map<Integer, Long>> enchantPrices = new HashMap<>();

    /**
     * Flat price for enchantments that don't vary by level (e.g., Mending).
     * Map of enchantment key → buy-price-in-cents.
     */
    private final Map<String, Long> enchantFlatPrices = new HashMap<>();

    /** Percentage of the enchant's store buy price paid as sell bonus. */
    private double bonusPercent = 5.0;
    /** Maximum total enchant bonus per item, in cents (-1 = uncapped). */
    private long bonusCapCents = 100_000;

    public EnchantPriceUtil(Market plugin) {
        this.plugin = plugin;
    }

    /**
     * Reloads enchantment prices from prices.yml.
     */
    public void reload() {
        enchantPrices.clear();
        enchantFlatPrices.clear();

        bonusPercent = plugin.getConfig().getDouble("server-shop.enchant-sell-bonus.percent", 5.0);
        double capUnits = plugin.getConfig().getDouble("server-shop.enchant-sell-bonus.cap", 1000.0);
        bonusCapCents = capUnits < 0 ? -1 : Math.round(capUnits * 100.0);

        File pricesFile = new File(plugin.getDataFolder(), "prices.yml");
        if (!pricesFile.exists()) return;

        FileConfiguration pricesConfig = YamlConfiguration.loadConfiguration(pricesFile);

        // Load defaults from jar
        InputStream defaultStream = plugin.getResource("prices.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            pricesConfig.setDefaults(defaultConfig);
        }

        ConfigurationSection section = pricesConfig.getConfigurationSection("enchant-prices");
        if (section == null) {
            plugin.getLogger().info("[EnchantPricing] No enchant-prices section found in prices.yml");
            return;
        }

        int count = 0;
        for (String enchantKey : section.getKeys(false)) {
            ConfigurationSection enchantSection = section.getConfigurationSection(enchantKey);
            if (enchantSection == null) continue;

            String normalizedKey = enchantKey.toUpperCase();

            // Check for flat price first
            if (enchantSection.contains("buyCents")) {
                long flatPrice = enchantSection.getLong("buyCents", 0);
                if (flatPrice > 0) {
                    enchantFlatPrices.put(normalizedKey, flatPrice);
                    count++;
                }
                continue;
            }

            // Per-level pricing
            ConfigurationSection perLevel = enchantSection.getConfigurationSection("perLevelBuyCents");
            if (perLevel != null) {
                Map<Integer, Long> levelPrices = new HashMap<>();
                for (String levelStr : perLevel.getKeys(false)) {
                    try {
                        int level = Integer.parseInt(levelStr);
                        long price = perLevel.getLong(levelStr, 0);
                        if (price > 0) {
                            levelPrices.put(level, price);
                        }
                    } catch (NumberFormatException ignored) {}
                }
                if (!levelPrices.isEmpty()) {
                    enchantPrices.put(normalizedKey, levelPrices);
                    count++;
                }
            }
        }

        plugin.getLogger().info("[EnchantPricing] Loaded prices for " + count + " enchantments.");
    }

    /**
     * Gets the store buy price (in cents) for a specific enchantment at a given level.
     * Returns 0 if not configured.
     */
    public long getEnchantBuyPriceCents(Enchantment enchantment, int level) {
        String key = enchantment.getKey().getKey().toUpperCase();

        // Check flat price first
        Long flatPrice = enchantFlatPrices.get(key);
        if (flatPrice != null) return flatPrice;

        // Check per-level
        Map<Integer, Long> levelPrices = enchantPrices.get(key);
        if (levelPrices != null) {
            Long price = levelPrices.get(level);
            if (price != null) return price;

            // Fallback: try the highest configured level that's <= requested level
            long bestPrice = 0;
            for (var entry : levelPrices.entrySet()) {
                if (entry.getKey() <= level && entry.getValue() > bestPrice) {
                    bestPrice = entry.getValue();
                }
            }
            return bestPrice;
        }

        return 0;
    }

    /**
     * Computes the total enchantment bonus in cents for an item.
     * <p>
     * For each enchantment: bonus += enchantBuyPrice × (percent / 100),
     * with the item total clamped to the configured cap.
     *
     * @param stack the item to evaluate
     * @return total enchantment bonus in cents
     */
    public long getEnchantBonusCents(ItemStack stack) {
        if (stack == null) return 0;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return 0;

        Map<Enchantment, Integer> enchantments = meta.getEnchants();
        if (enchantments.isEmpty()) return 0;

        long totalBonus = 0;
        for (var entry : enchantments.entrySet()) {
            long buyPrice = getEnchantBuyPriceCents(entry.getKey(), entry.getValue());
            if (buyPrice > 0) {
                totalBonus += bonusCents(buyPrice);
            }
        }

        return applyCap(totalBonus);
    }

    private long bonusCents(long buyPriceCents) {
        return BigDecimal.valueOf(buyPriceCents)
                .multiply(BigDecimal.valueOf(bonusPercent / 100.0))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private long applyCap(long totalBonusCents) {
        if (bonusCapCents < 0) return totalBonusCents;
        return Math.min(totalBonusCents, bonusCapCents);
    }

    /**
     * Checks if any enchantment on the item has a configured price.
     */
    public boolean hasAnyPricedEnchantment(ItemStack stack) {
        if (stack == null) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        for (var entry : meta.getEnchants().entrySet()) {
            if (getEnchantBuyPriceCents(entry.getKey(), entry.getValue()) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a formatted breakdown string for each enchantment's bonus.
     * Used in the sell confirmation GUI. When the total exceeds the configured
     * cap, each line is scaled down proportionally so the lines sum to the
     * capped total actually paid.
     */
    public Map<String, Long> getEnchantBreakdown(ItemStack stack) {
        Map<String, Long> breakdown = new HashMap<>();
        if (stack == null) return breakdown;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return breakdown;

        long total = 0;
        for (var entry : meta.getEnchants().entrySet()) {
            long buyPrice = getEnchantBuyPriceCents(entry.getKey(), entry.getValue());
            if (buyPrice > 0) {
                long bonus = bonusCents(buyPrice);
                String name = formatEnchantName(entry.getKey(), entry.getValue());
                breakdown.put(name, bonus);
                total += bonus;
            }
        }

        long capped = applyCap(total);
        if (capped < total && total > 0) {
            double scale = (double) capped / total;
            breakdown.replaceAll((name, bonus) -> Math.round(bonus * scale));
        }
        return breakdown;
    }

    private String formatEnchantName(Enchantment enchantment, int level) {
        String name = enchantment.getKey().getKey().replace("_", " ");
        // Capitalize each word
        StringBuilder sb = new StringBuilder();
        for (String word : name.split(" ")) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        if (level > 1 || enchantment.getMaxLevel() > 1) {
            sb.append(" ").append(toRoman(level));
        }
        return sb.toString();
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
}

