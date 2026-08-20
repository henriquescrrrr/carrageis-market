package pt.henrique.market.servershop.service;

import org.bukkit.inventory.ItemStack;
import pt.henrique.market.Market;
import pt.henrique.market.servershop.MaterialUtil;
import pt.henrique.market.servershop.model.SellQuote;
import pt.henrique.market.servershop.model.ShopItem;
import pt.henrique.market.servershop.model.ShopPriceEntry;

/**
 * Service for computing server shop prices.
 * Wraps ShopPriceManager and provides calculation methods for
 * total buy/sell costs including quantity and tax.
 */
public class ShopPricingService {

    private final Market plugin;

    public ShopPricingService(Market plugin) {
        this.plugin = plugin;
    }

    /**
     * Values one concrete stack for selling to the server shop.
     * <p>
     * Buying is looked up by price-row ID, but selling has to start from the item
     * the player is actually holding: two bows of the same material can be worth
     * very different amounts, and an enchanted book is worth the sum of its
     * enchantments rather than whatever single row it happens to match.
     *
     * @param stack the item being valued (never mutated)
     * @return the quote, or {@code null} when the item cannot be sold
     */
    public SellQuote quoteSell(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;

        EnchantPriceUtil enchants = plugin.getEnchantPriceUtil();
        if (enchants.isCompositeBookPricing(stack)) {
            double summed = enchants.getBookSellValue(stack);
            if (summed <= 0) return null;
            String itemId = plugin.getShopPriceManager().identifySellItem(stack);
            String name = MaterialUtil.formatName(stack.getType())
                    + " (" + EnchantPriceUtil.describeEnchants(stack) + ")";
            return new SellQuote(itemId, name, summed, true);
        }

        String itemId = plugin.getShopPriceManager().identifySellItem(stack);
        if (itemId == null || !isSellable(itemId)) return null;
        double baseSell = plugin.getShopPriceManager().getSellPrice(itemId);
        if (baseSell <= 0) return null;

        ShopItem shopItem = plugin.getShopPriceManager().getShopItem(itemId);
        String name = shopItem != null ? shopItem.getDisplayName() : MaterialUtil.formatName(stack.getType());
        return new SellQuote(itemId, name, baseSell, false);
    }

    public ShopPriceEntry getPriceEntry(String itemId) {
        return plugin.getShopPriceManager().getPriceEntry(itemId);
    }

    /** Calculates total buy cost for a quantity. Returns -1 if not buyable. */
    public double getTotalBuyPrice(String itemId, int quantity) {
        double unitPrice = plugin.getShopPriceManager().getBuyPrice(itemId);
        if (unitPrice < 0) return -1;
        return Math.round(unitPrice * quantity * 100.0) / 100.0;
    }

    /** Calculates total sell value for a quantity (before tax). Returns -1 if not sellable. */
    public double getTotalSellPrice(String itemId, int quantity) {
        double unitPrice = plugin.getShopPriceManager().getSellPrice(itemId);
        if (unitPrice < 0) return -1;
        return Math.round(unitPrice * quantity * 100.0) / 100.0;
    }

    /** Calculates the tax amount for a sell transaction. */
    public double calculateTax(double totalSellPrice) {
        double taxRate = plugin.getConfig().getDouble("server-shop.sell-tax", 0.0);
        if (taxRate <= 0) return 0;
        return Math.round(totalSellPrice * (taxRate / 100.0) * 100.0) / 100.0;
    }

    /** Calculates the net amount the player receives after tax. */
    public double getNetSellPrice(double totalSellPrice) {
        return totalSellPrice - calculateTax(totalSellPrice);
    }

    public double getSellTaxRate() {
        return plugin.getConfig().getDouble("server-shop.sell-tax", 0.0);
    }

    public boolean isBuyable(String itemId) { return plugin.getShopPriceManager().isBuyable(itemId); }
    public boolean isSellable(String itemId) { return plugin.getShopPriceManager().isSellable(itemId); }
}
