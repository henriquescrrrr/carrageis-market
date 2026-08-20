package pt.henrique.market.servershop.model;

/**
 * What one concrete ItemStack is worth to the server shop, before enchantment
 * bonuses, tool-mod bonuses and wear are applied.
 *
 * @param itemId      the price row this stack is charged against, also used for
 *                    transaction logging; may be a virtual row such as
 *                    {@code ENCHANTED_BOOK_MULTI}
 * @param displayName human-readable name for GUIs
 * @param baseSell    base sell price per unit
 * @param composite   {@code true} when {@code baseSell} already accounts for the
 *                    item's enchantments (enchanted books priced by summing their
 *                    enchantments), in which case the enchantment bonus must not
 *                    be added again
 */
public record SellQuote(String itemId, String displayName, double baseSell, boolean composite) {}
