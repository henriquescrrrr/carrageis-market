package pt.henrique.market.model;

import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents an item in claim storage waiting to be claimed by a player
 */
public class ClaimItem {

    private int id;
    private UUID playerUuid;
    private ItemStack item;
    private ClaimReason reason;
    private String sourceInfo; // Additional info like listing ID, auction ID, etc.
    private Instant createdAt;

    public ClaimItem() {
        this.createdAt = Instant.now();
    }

    public ClaimItem(UUID playerUuid, ItemStack item, ClaimReason reason, String sourceInfo) {
        this();
        this.playerUuid = playerUuid;
        this.item = item;
        this.reason = reason;
        this.sourceInfo = sourceInfo;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public ItemStack getItem() {
        return item;
    }

    public void setItem(ItemStack item) {
        this.item = item;
    }

    public ClaimReason getReason() {
        return reason;
    }

    public void setReason(ClaimReason reason) {
        this.reason = reason;
    }

    public String getSourceInfo() {
        return sourceInfo;
    }

    public void setSourceInfo(String sourceInfo) {
        this.sourceInfo = sourceInfo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public enum ClaimReason {
        EXPIRED_LISTING("Expired Listing"),
        CANCELLED_LISTING("Cancelled Listing"),
        WON_AUCTION("Won Auction"),
        AUCTION_NO_BIDS("Auction Ended (No Bids)"),
        CANCELLED_AUCTION("Cancelled Auction"),
        PURCHASE_FULL_INVENTORY("Purchase (Inventory Full)"),
        OUTBID_REFUND("Outbid Refund"),
        ADMIN_RETURN("Admin Return");

        private final String displayName;

        ClaimReason(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}

