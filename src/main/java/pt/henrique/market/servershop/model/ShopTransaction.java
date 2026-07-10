package pt.henrique.market.servershop.model;

import java.util.UUID;

/**
 * Represents a buy or sell transaction in the server shop, for logging purposes.
 *
 * @param id           Auto-increment ID (0 for new records)
 * @param playerUuid   UUID of the player
 * @param playerName   Name of the player
 * @param itemId       The item ID traded (Material name or virtual ID)
 * @param amount       Quantity traded
 * @param pricePerUnit Price per unit
 * @param totalPrice   Total transaction value
 * @param tax          Tax amount deducted (for sells)
 * @param type         BUY or SELL
 * @param timestamp    Epoch millis when the transaction occurred
 */
public record ShopTransaction(
        int id,
        UUID playerUuid,
        String playerName,
        String itemId,
        int amount,
        double pricePerUnit,
        double totalPrice,
        double tax,
        ShopTransactionType type,
        long timestamp
) {

    public enum ShopTransactionType {
        BUY,
        SELL
    }

    /** Creates a new transaction record (without ID, for insertion). */
    public static ShopTransaction create(UUID playerUuid, String playerName, String itemId,
                                          int amount, double pricePerUnit, double totalPrice,
                                          double tax, ShopTransactionType type) {
        return new ShopTransaction(0, playerUuid, playerName, itemId, amount,
                pricePerUnit, totalPrice, tax, type, System.currentTimeMillis());
    }
}
