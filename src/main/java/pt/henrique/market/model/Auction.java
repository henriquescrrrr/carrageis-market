package pt.henrique.market.model;

import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents an auction listing
 */
public class Auction {

    private int id;
    private UUID sellerUuid;
    private String sellerName;
    private ItemStack item;
    private double startPrice;
    private double currentBid;
    private UUID highestBidderUuid;
    private String highestBidderName;
    private int bidCount;
    private Double buyoutPrice; // nullable
    private Instant createdAt;
    private Instant endsAt;
    private int extensionCount;
    private AuctionStatus status;

    public Auction() {
        this.status = AuctionStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.bidCount = 0;
        this.extensionCount = 0;
        this.currentBid = 0;
    }

    public Auction(UUID sellerUuid, String sellerName, ItemStack item, double startPrice, Double buyoutPrice, Instant endsAt) {
        this();
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.item = item;
        this.startPrice = startPrice;
        this.buyoutPrice = buyoutPrice;
        this.endsAt = endsAt;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public UUID getSellerUuid() {
        return sellerUuid;
    }

    public void setSellerUuid(UUID sellerUuid) {
        this.sellerUuid = sellerUuid;
    }

    public String getSellerName() {
        return sellerName;
    }

    public void setSellerName(String sellerName) {
        this.sellerName = sellerName;
    }

    public ItemStack getItem() {
        return item;
    }

    public void setItem(ItemStack item) {
        this.item = item;
    }

    public double getStartPrice() {
        return startPrice;
    }

    public void setStartPrice(double startPrice) {
        this.startPrice = startPrice;
    }

    public double getCurrentBid() {
        return currentBid;
    }

    public void setCurrentBid(double currentBid) {
        this.currentBid = currentBid;
    }

    public UUID getHighestBidderUuid() {
        return highestBidderUuid;
    }

    public void setHighestBidderUuid(UUID highestBidderUuid) {
        this.highestBidderUuid = highestBidderUuid;
    }

    public String getHighestBidderName() {
        return highestBidderName;
    }

    public void setHighestBidderName(String highestBidderName) {
        this.highestBidderName = highestBidderName;
    }

    public int getBidCount() {
        return bidCount;
    }

    public void setBidCount(int bidCount) {
        this.bidCount = bidCount;
    }

    public Double getBuyoutPrice() {
        return buyoutPrice;
    }

    public void setBuyoutPrice(Double buyoutPrice) {
        this.buyoutPrice = buyoutPrice;
    }

    public boolean hasBuyout() {
        return buyoutPrice != null && buyoutPrice > 0;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public void setEndsAt(Instant endsAt) {
        this.endsAt = endsAt;
    }

    public int getExtensionCount() {
        return extensionCount;
    }

    public void setExtensionCount(int extensionCount) {
        this.extensionCount = extensionCount;
    }

    public void incrementExtensionCount() {
        this.extensionCount++;
    }

    public AuctionStatus getStatus() {
        return status;
    }

    public void setStatus(AuctionStatus status) {
        this.status = status;
    }

    public boolean isEnded() {
        return Instant.now().isAfter(endsAt);
    }

    public boolean isActive() {
        return status == AuctionStatus.ACTIVE && !isEnded();
    }

    public boolean hasBids() {
        return bidCount > 0;
    }

    public double getEffectivePrice() {
        return hasBids() ? currentBid : startPrice;
    }

    public double getMinimumBid(double minIncrementPercent, double minIncrementAbsolute) {
        if (!hasBids()) {
            return startPrice;
        }
        double percentIncrement = currentBid * (minIncrementPercent / 100.0);
        return currentBid + Math.max(percentIncrement, minIncrementAbsolute);
    }

    public enum AuctionStatus {
        ACTIVE,
        ENDED,
        SOLD,
        CANCELLED,
        EXPIRED,
        NO_BIDS
    }
}

