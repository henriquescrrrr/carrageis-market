package pt.henrique.market.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a bid on an auction
 */
public class Bid {

    private int id;
    private int auctionId;
    private UUID bidderUuid;
    private String bidderName;
    private double amount;
    private Instant createdAt;

    public Bid() {
        this.createdAt = Instant.now();
    }

    public Bid(int auctionId, UUID bidderUuid, String bidderName, double amount) {
        this();
        this.auctionId = auctionId;
        this.bidderUuid = bidderUuid;
        this.bidderName = bidderName;
        this.amount = amount;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getAuctionId() {
        return auctionId;
    }

    public void setAuctionId(int auctionId) {
        this.auctionId = auctionId;
    }

    public UUID getBidderUuid() {
        return bidderUuid;
    }

    public void setBidderUuid(UUID bidderUuid) {
        this.bidderUuid = bidderUuid;
    }

    public String getBidderName() {
        return bidderName;
    }

    public void setBidderName(String bidderName) {
        this.bidderName = bidderName;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

