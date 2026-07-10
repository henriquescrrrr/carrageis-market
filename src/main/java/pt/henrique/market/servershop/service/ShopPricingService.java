package pt.henrique.market.servershop.service;

import pt.henrique.market.Market;
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
