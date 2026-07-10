package pt.henrique.market.servershop.model;

/**
 * Represents the pricing information for a single item in the server shop.
 *
 * @param itemId      The item ID this entry applies to (Material name or virtual ID like ENCHANTED_BOOK_SHARPNESS_5)
 * @param buyPrice    The price players pay to BUY from the server
 * @param sellPrice   The price players receive when SELLING to the server
 * @param buyEnabled  Whether this item can be bought from the server
 * @param sellEnabled Whether this item can be sold to the server
 */
public record ShopPriceEntry(
        String itemId,
        double buyPrice,
        double sellPrice,
        boolean buyEnabled,
        boolean sellEnabled
) {

    /**
     * Calculates the spread percentage between buy and sell price.
     * Spread = ((buyPrice - sellPrice) / buyPrice) * 100
     *
     * @return Spread percentage (0-100), or 0 if buy price is 0
     */
    public double getSpreadPercent() {
        if (buyPrice <= 0) return 0;
        return Math.round(((buyPrice - sellPrice) / buyPrice) * 1000.0) / 10.0;
    }

    /**
     * Checks if this item has any valid operation (buy or sell).
     */
    public boolean hasAnyOperation() {
        return (buyEnabled && buyPrice > 0) || (sellEnabled && sellPrice > 0);
    }
}
