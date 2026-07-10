package pt.henrique.market.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a sale notification for an offline player.
 * Stored in the database and delivered when the player next joins.
 */
public class SaleNotification {

    private int id;
    private UUID sellerUuid;
    private String itemName;
    private String formattedPrice;
    private String buyerName;
    private Instant createdAt;

    public SaleNotification() {
        this.createdAt = Instant.now();
    }

    public SaleNotification(UUID sellerUuid, String itemName, String formattedPrice, String buyerName) {
        this();
        this.sellerUuid = sellerUuid;
        this.itemName = itemName;
        this.formattedPrice = formattedPrice;
        this.buyerName = buyerName;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public UUID getSellerUuid() { return sellerUuid; }
    public void setSellerUuid(UUID sellerUuid) { this.sellerUuid = sellerUuid; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getFormattedPrice() { return formattedPrice; }
    public void setFormattedPrice(String formattedPrice) { this.formattedPrice = formattedPrice; }

    public String getBuyerName() { return buyerName; }
    public void setBuyerName(String buyerName) { this.buyerName = buyerName; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

