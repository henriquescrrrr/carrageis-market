package pt.henrique.market.hook;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import pt.henrique.market.economy.EconomyManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Integration hook for the ToolMods plugin.
 * <p>
 * Reads ToolMods' {@link PersistentDataContainer} keys directly — no compile-time
 * dependency on ToolMods is required. If ToolMods is not installed, all items will
 * simply have zero mods detected and zero bonus.
 * <p>
 * <b>PDC layout (ToolMods):</b>
 * <ul>
 *   <li>Namespace: {@code "toolmods"}</li>
 *   <li>Keys: {@code "mod_area_mine_3x3"}, {@code "mod_unbreakable"}, etc.</li>
 *   <li>Value: {@link PersistentDataType#BYTE}, value {@code 1} when present</li>
 *   <li>Disabled toggle: key {@code "mod_xxx_disabled"} exists if the mod is disabled,
 *       but the base key is still present — the mod still has value.</li>
 * </ul>
 * <p>
 * Each mod has a <b>purchase price</b> (what it costs in ToolMods) and a
 * <b>sell multiplier</b> (what fraction of that price is added as a sell bonus
 * when the item is sold to the Server Shop). Mods are grouped into tiers:
 * <ul>
 *   <li>Ultra (55%) — extremely rare / endgame mods</li>
 *   <li>Premium (40%) — high-end mods</li>
 *   <li>High (30%) — strong combat / utility mods</li>
 *   <li>Mid (25%) — solid mid-range mods</li>
 *   <li>QoL / Low (20%) — quality-of-life / cheap mods</li>
 * </ul>
 *
 * @see pt.henrique.market.servershop.service.EnchantPriceUtil
 */
public class ToolModsHook {

    /** The namespace used by the ToolMods plugin for all PDC keys. */
    private static final String TOOLMODS_NAMESPACE = "toolmods";

    /**
     * Default mod pricing table.
     * Key = PDC key (e.g. {@code "mod_unbreakable"}),
     * Value = {@link ModSellInfo} with purchase price (in currency units) and sell multiplier.
     */
    private static final Map<String, ModSellInfo> DEFAULT_MOD_INFO = new LinkedHashMap<>();

    static {
        // ── Ultra tier — 55% of purchase price ──────────────────────────
        DEFAULT_MOD_INFO.put("mod_unbreakable",        new ModSellInfo(250_000, 0.55));

        // ── Premium tier — 40% ──────────────────────────────────────────
        DEFAULT_MOD_INFO.put("mod_soulbound",          new ModSellInfo(100_000, 0.40));
        DEFAULT_MOD_INFO.put("mod_double_jump",        new ModSellInfo( 55_000, 0.40));
        DEFAULT_MOD_INFO.put("mod_meteor_strike",      new ModSellInfo( 50_000, 0.40));
        DEFAULT_MOD_INFO.put("mod_area_mine_5x5",      new ModSellInfo( 50_000, 0.40));
        DEFAULT_MOD_INFO.put("mod_excavator_5x5",      new ModSellInfo( 40_000, 0.40));
        DEFAULT_MOD_INFO.put("mod_graviton_pulse",     new ModSellInfo( 40_000, 0.40));

        // ── High tier — 30% ─────────────────────────────────────────────
        DEFAULT_MOD_INFO.put("mod_seismic_slam",       new ModSellInfo( 35_000, 0.30));
        DEFAULT_MOD_INFO.put("mod_harvester_5x5",      new ModSellInfo( 35_000, 0.30));
        DEFAULT_MOD_INFO.put("mod_dodger",             new ModSellInfo( 35_000, 0.30));
        DEFAULT_MOD_INFO.put("mod_sonic_boom",         new ModSellInfo( 35_000, 0.30));
        DEFAULT_MOD_INFO.put("mod_lifesteal",          new ModSellInfo( 30_000, 0.30));
        DEFAULT_MOD_INFO.put("mod_thunderstrike",      new ModSellInfo( 30_000, 0.30));
        DEFAULT_MOD_INFO.put("mod_night_owl",          new ModSellInfo( 30_000, 0.30));
        DEFAULT_MOD_INFO.put("mod_last_stand",         new ModSellInfo( 30_000, 0.30));
        DEFAULT_MOD_INFO.put("mod_vitality",           new ModSellInfo( 30_000, 0.30));
        DEFAULT_MOD_INFO.put("mod_homing_arrow",       new ModSellInfo( 25_000, 0.30));
        DEFAULT_MOD_INFO.put("mod_evoker_fangs",       new ModSellInfo( 25_000, 0.30));
        DEFAULT_MOD_INFO.put("mod_spear_evoker_fangs", new ModSellInfo( 25_000, 0.30));
        DEFAULT_MOD_INFO.put("mod_phantom_cloak",      new ModSellInfo( 25_000, 0.30));
        DEFAULT_MOD_INFO.put("mod_aqua_lung",          new ModSellInfo( 25_000, 0.30));
        // Trident / Spear mods
        DEFAULT_MOD_INFO.put("mod_poseidons_call",     new ModSellInfo( 30_000, 0.30));
        DEFAULT_MOD_INFO.put("mod_chain_lightning",    new ModSellInfo( 25_000, 0.30));
        DEFAULT_MOD_INFO.put("mod_riptide",            new ModSellInfo( 20_000, 0.30));

        // ── Mid tier — 25% ──────────────────────────────────────────────
        DEFAULT_MOD_INFO.put("mod_tremor",             new ModSellInfo( 20_000, 0.25));
        DEFAULT_MOD_INFO.put("mod_boost",              new ModSellInfo( 20_000, 0.25));
        DEFAULT_MOD_INFO.put("mod_swift",              new ModSellInfo( 20_000, 0.25));
        DEFAULT_MOD_INFO.put("mod_hunters_sight",      new ModSellInfo( 20_000, 0.25));
        DEFAULT_MOD_INFO.put("mod_frost_walker_plus",  new ModSellInfo( 20_000, 0.25));
        DEFAULT_MOD_INFO.put("mod_reflective_shield",  new ModSellInfo( 18_000, 0.25));
        DEFAULT_MOD_INFO.put("mod_thorns_aura",        new ModSellInfo( 18_000, 0.25));
        DEFAULT_MOD_INFO.put("mod_explosive_arrow",    new ModSellInfo( 18_000, 0.25));
        // Trident / Spear mods
        DEFAULT_MOD_INFO.put("mod_anchor",             new ModSellInfo( 18_000, 0.25));
        DEFAULT_MOD_INFO.put("mod_loyalty_plus",       new ModSellInfo( 15_000, 0.25));
        DEFAULT_MOD_INFO.put("mod_area_mine_3x3",      new ModSellInfo( 15_000, 0.25));
        DEFAULT_MOD_INFO.put("mod_momentum",           new ModSellInfo( 15_000, 0.25));
        DEFAULT_MOD_INFO.put("mod_aerodynamic",        new ModSellInfo( 15_000, 0.25));
        DEFAULT_MOD_INFO.put("mod_decapitator",        new ModSellInfo( 15_000, 0.25));
        DEFAULT_MOD_INFO.put("mod_telepathy",          new ModSellInfo( 12_000, 0.25));
        DEFAULT_MOD_INFO.put("mod_excavator_3x3",      new ModSellInfo( 12_000, 0.25));
        DEFAULT_MOD_INFO.put("mod_phalanx",            new ModSellInfo( 12_000, 0.25));
        DEFAULT_MOD_INFO.put("mod_safe_landing",       new ModSellInfo( 12_000, 0.25));
        DEFAULT_MOD_INFO.put("mod_spear_sweep",        new ModSellInfo( 12_000, 0.25));
        DEFAULT_MOD_INFO.put("mod_harvester_3x3",      new ModSellInfo( 10_000, 0.25));
        DEFAULT_MOD_INFO.put("mod_dash_impact",        new ModSellInfo( 10_000, 0.25));
        DEFAULT_MOD_INFO.put("mod_shield_bash",        new ModSellInfo( 10_000, 0.25));
        DEFAULT_MOD_INFO.put("mod_featherweight",      new ModSellInfo( 10_000, 0.25));

        // ── QoL / Low tier — 20% ───────────────────────────────────────
        DEFAULT_MOD_INFO.put("mod_auto_smelt",         new ModSellInfo(  8_000, 0.20));
        DEFAULT_MOD_INFO.put("mod_magnetic_shield",    new ModSellInfo(  8_000, 0.20));
        DEFAULT_MOD_INFO.put("mod_bleed",              new ModSellInfo(  8_000, 0.20));
        DEFAULT_MOD_INFO.put("mod_firework_efficiency",new ModSellInfo(  8_000, 0.20));
        DEFAULT_MOD_INFO.put("mod_experienced",        new ModSellInfo(  6_000, 0.20));
        DEFAULT_MOD_INFO.put("mod_cripple",            new ModSellInfo(  6_000, 0.20));
        DEFAULT_MOD_INFO.put("mod_path_maker",         new ModSellInfo(  5_000, 0.20));
        DEFAULT_MOD_INFO.put("mod_tiller",             new ModSellInfo(  4_000, 0.20));
        DEFAULT_MOD_INFO.put("mod_vein_miner",         new ModSellInfo(  1_600, 0.20));
        DEFAULT_MOD_INFO.put("mod_tree_feller",        new ModSellInfo(    800, 0.20));
    }

    /**
     * Effective mod info after applying config overrides.
     * Populated by {@link #reload(boolean, Map)} on startup/reload.
     * Falls back to {@link #DEFAULT_MOD_INFO} if never reloaded.
     */
    private static Map<String, ModSellInfo> effectiveModInfo = DEFAULT_MOD_INFO;

    /** Whether the ToolMods integration is enabled via config. */
    private static boolean enabled = true;

    // ──────────────────────────────────────────────────────────────────────

    /**
     * Reloads the ToolMods hook with the given enabled state and multiplier overrides.
     * <p>
     * Call this from {@code Market.onEnable()} and {@code Market.reload()}.
     *
     * @param enabled             whether the ToolMods integration is enabled
     * @param multiplierOverrides map of mod key → overridden sell multiplier
     *                            (e.g. {@code "mod_unbreakable" → 0.60}).
     *                            Pass an empty/null map to use all defaults.
     */
    public static void reload(boolean enabled, Map<String, Double> multiplierOverrides) {
        ToolModsHook.enabled = enabled;

        if (multiplierOverrides == null || multiplierOverrides.isEmpty()) {
            effectiveModInfo = DEFAULT_MOD_INFO;
            return;
        }

        Map<String, ModSellInfo> merged = new LinkedHashMap<>(DEFAULT_MOD_INFO.size());
        for (var entry : DEFAULT_MOD_INFO.entrySet()) {
            String modKey = entry.getKey();
            ModSellInfo defaultInfo = entry.getValue();

            Double overriddenMultiplier = multiplierOverrides.get(modKey);
            if (overriddenMultiplier != null) {
                merged.put(modKey, new ModSellInfo(defaultInfo.purchasePrice, overriddenMultiplier));
            } else {
                merged.put(modKey, defaultInfo);
            }
        }
        effectiveModInfo = merged;
    }

    /**
     * @return {@code true} if the ToolMods integration is enabled in config
     */
    public static boolean isEnabled() {
        return enabled;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Sell bonus calculation
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Calculates the total sell bonus <b>in cents</b> for all ToolMods on an item.
     * <p>
     * For each recognised mod key found in the item's PDC, the bonus is:
     * {@code purchasePrice × sellMultiplier} (converted to cents).
     * <p>
     * Consistent with {@link pt.henrique.market.servershop.service.EnchantPriceUtil#getEnchantBonusCents(ItemStack)}.
     *
     * @param item the item to evaluate (may be {@code null})
     * @return total mod bonus in cents, or {@code 0} if no mods found
     */
    public static long getModBonusCents(ItemStack item) {
        if (!enabled) return 0;
        if (item == null || !item.hasItemMeta()) return 0;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();

        long totalCents = 0;
        for (var entry : effectiveModInfo.entrySet()) {
            NamespacedKey key = new NamespacedKey(TOOLMODS_NAMESPACE, entry.getKey());
            if (pdc.has(key, PersistentDataType.BYTE)) {
                ModSellInfo info = entry.getValue();
                // purchasePrice is in currency units → convert to cents, apply multiplier
                long bonusCents = BigDecimal.valueOf(info.purchasePrice)
                        .multiply(BigDecimal.valueOf(100))     // → cents
                        .multiply(BigDecimal.valueOf(info.sellMultiplier))
                        .setScale(0, RoundingMode.HALF_UP)
                        .longValueExact();
                totalCents += bonusCents;
            }
        }
        return totalCents;
    }

    /**
     * Convenience wrapper: returns the mod sell bonus as a {@code double}
     * in currency units (e.g. {@code 137500.0} for an Unbreakable item).
     *
     * @param item the item to evaluate
     * @return total mod bonus in currency units
     */
    public static double calculateModSellBonus(ItemStack item) {
        return EconomyManager.toDouble(getModBonusCents(item));
    }

    // ──────────────────────────────────────────────────────────────────────
    // Breakdown for GUI display
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Returns a per-mod breakdown of sell bonuses <b>in cents</b>.
     * Map key = human-readable mod name, value = bonus in cents.
     * <p>
     * Used in the Server Shop sell detail GUI to show each mod's contribution.
     *
     * @param item the item to evaluate
     * @return ordered map of mod display name → bonus cents
     */
    public static Map<String, Long> getModBreakdown(ItemStack item) {
        Map<String, Long> breakdown = new LinkedHashMap<>();
        if (!enabled) return breakdown;
        if (item == null || !item.hasItemMeta()) return breakdown;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();

        for (var entry : effectiveModInfo.entrySet()) {
            NamespacedKey key = new NamespacedKey(TOOLMODS_NAMESPACE, entry.getKey());
            if (pdc.has(key, PersistentDataType.BYTE)) {
                ModSellInfo info = entry.getValue();
                long bonusCents = BigDecimal.valueOf(info.purchasePrice)
                        .multiply(BigDecimal.valueOf(100))
                        .multiply(BigDecimal.valueOf(info.sellMultiplier))
                        .setScale(0, RoundingMode.HALF_UP)
                        .longValueExact();
                breakdown.put(formatModName(entry.getKey()), bonusCents);
            }
        }
        return breakdown;
    }

    /**
     * Returns a list of human-readable mod display names present on the item.
     * Used in Community Market / Auction browse GUIs to show what mods the item has.
     *
     * @param item the item to evaluate
     * @return list of mod display names (e.g. "Unbreakable", "Area Mine 3x3")
     */
    public static List<String> getModNames(ItemStack item) {
        if (!enabled) return Collections.emptyList();
        if (item == null || !item.hasItemMeta()) return Collections.emptyList();
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();

        List<String> names = new ArrayList<>();
        for (String modKey : effectiveModInfo.keySet()) {
            NamespacedKey key = new NamespacedKey(TOOLMODS_NAMESPACE, modKey);
            if (pdc.has(key, PersistentDataType.BYTE)) {
                names.add(formatModName(modKey));
            }
        }
        return names;
    }

    /**
     * Checks whether the item has any recognised ToolMods mods.
     *
     * @param item the item to check
     * @return {@code true} if at least one mod is present
     */
    public static boolean hasMods(ItemStack item) {
        if (!enabled) return false;
        if (item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        for (String modKey : effectiveModInfo.keySet()) {
            if (pdc.has(new NamespacedKey(TOOLMODS_NAMESPACE, modKey), PersistentDataType.BYTE)) {
                return true;
            }
        }
        return false;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Converts a PDC mod key like {@code "mod_area_mine_3x3"} to a human-readable
     * display name like {@code "Area Mine 3x3"}.
     */
    static String formatModName(String modKey) {
        // Strip "mod_" prefix
        String raw = modKey.startsWith("mod_") ? modKey.substring(4) : modKey;

        StringBuilder sb = new StringBuilder();
        for (String word : raw.split("_")) {
            if (sb.length() > 0) sb.append(" ");
            // Keep dimension tokens like "3x3", "5x5" as-is
            if (word.matches("\\d+x\\d+")) {
                sb.append(word);
            } else {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return sb.toString();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Data record
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Holds the pricing information for a single ToolMods mod.
     *
     * @param purchasePrice  the mod's purchase price in ToolMods (currency units, not cents)
     * @param sellMultiplier fraction of the purchase price added as a sell bonus (0.0–1.0)
     */
    public record ModSellInfo(double purchasePrice, double sellMultiplier) {

        /** Returns the sell bonus in currency units. */
        public double bonus() {
            return purchasePrice * sellMultiplier;
        }

        /** Returns the sell bonus in cents. */
        public long bonusCents() {
            return BigDecimal.valueOf(purchasePrice)
                    .multiply(BigDecimal.valueOf(100))
                    .multiply(BigDecimal.valueOf(sellMultiplier))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
        }
    }

    /** Exposes the default mod info table (read-only). Useful for admin/debug commands. */
    public static Map<String, ModSellInfo> getDefaultModInfo() {
        return Collections.unmodifiableMap(DEFAULT_MOD_INFO);
    }

    /** Exposes the effective (after overrides) mod info table (read-only). */
    public static Map<String, ModSellInfo> getEffectiveModInfo() {
        return Collections.unmodifiableMap(effectiveModInfo);
    }
}

