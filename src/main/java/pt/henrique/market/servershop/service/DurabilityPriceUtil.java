package pt.henrique.market.servershop.service;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Utility for computing durability-based sell price adjustments.
 * <p>
 * <b>Rules:</b>
 * <ul>
 *   <li>Only items implementing {@link Damageable} are affected.</li>
 *   <li>Wear ratio = currentDamage / maxDamage  (0 = new, 1 = about to break).</li>
 *   <li>If the item has <b>Unbreaking U</b>, the effective wear is reduced:
 *       {@code effectiveWear = wearRatio / (U + 1)}.
 *       Rationale: Unbreaking U makes the item last ~(U+1)× longer on average,
 *       so an item with X visible damage actually represents less "real" usage.</li>
 *   <li>If the item has <b>Mending</b>, durability penalty is <em>not applied</em> at all
 *       (treat as full durability).</li>
 *   <li>The reduction is capped at 50% — the minimum payout is always 50% of base sell.</li>
 *   <li>Formula: {@code reduction = effectiveWear × 0.50}, then
 *       {@code multiplier = 1.0 − reduction}, clamped to [0.50, 1.0].</li>
 * </ul>
 */
public final class DurabilityPriceUtil {

    private DurabilityPriceUtil() {}

    /**
     * Returns a multiplier in [0.50, 1.0] to apply to the sell value.
     * Returns exactly 1.0 for items without durability, with Mending, or brand-new items.
     *
     * @param stack the item to evaluate
     * @return durability multiplier
     */
    public static double getDurabilityMultiplier(ItemStack stack) {
        if (stack == null) return 1.0;
        if (!(stack.getItemMeta() instanceof Damageable damageable)) return 1.0;

        // Mending overrides — no durability penalty
        if (stack.containsEnchantment(Enchantment.MENDING)) {
            return 1.0;
        }

        int maxDurability = stack.getType().getMaxDurability();
        if (maxDurability <= 0) return 1.0;

        int damage = damageable.getDamage();
        if (damage <= 0) return 1.0; // Brand new

        double wearRatio = (double) damage / maxDurability; // 0..1

        // Unbreaking adjustment: effectiveWear = wearRatio / (U + 1)
        int unbreakingLevel = stack.getEnchantmentLevel(Enchantment.UNBREAKING);
        double effectiveWear = (unbreakingLevel > 0)
                ? wearRatio / (unbreakingLevel + 1)
                : wearRatio;

        // Reduction capped at 50%
        double reduction = effectiveWear * 0.50;
        double multiplier = 1.0 - reduction;

        // Clamp to [0.50, 1.0]
        return Math.max(0.50, Math.min(1.0, multiplier));
    }

    /**
     * Applies the durability multiplier to a base price in cents and returns
     * the adjusted price (rounded HALF_UP to nearest cent).
     *
     * @param baseCents base sell price in cents
     * @param stack     the item
     * @return adjusted price in cents
     */
    public static long applyDurability(long baseCents, ItemStack stack) {
        double multiplier = getDurabilityMultiplier(stack);
        if (multiplier >= 1.0) return baseCents;
        return BigDecimal.valueOf(baseCents)
                .multiply(BigDecimal.valueOf(multiplier))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    /**
     * Returns true if the item is damageable (has durability).
     */
    public static boolean isDamageable(ItemStack stack) {
        return stack != null && stack.getItemMeta() instanceof Damageable
                && stack.getType().getMaxDurability() > 0;
    }

    /**
     * Returns a human-readable description of the wear state.
     */
    public static String getWearDescription(ItemStack stack) {
        if (stack == null) return "N/A";
        if (!(stack.getItemMeta() instanceof Damageable damageable)) return "N/A";

        if (stack.containsEnchantment(Enchantment.MENDING)) {
            return "None (Mending)";
        }

        int maxDurability = stack.getType().getMaxDurability();
        if (maxDurability <= 0) return "N/A";

        int damage = damageable.getDamage();
        if (damage <= 0) return "None (Brand New)";

        double multiplier = getDurabilityMultiplier(stack);
        int penaltyPercent = (int) Math.round((1.0 - multiplier) * 100);
        return "-" + penaltyPercent + "%";
    }
}

