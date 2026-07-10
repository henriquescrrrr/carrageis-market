package pt.henrique.market.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents pending earnings that a player can withdraw
 */
public class PendingEarnings {

    private int id;
    private UUID playerUuid;
    private double amount;
    private String source; // e.g., "Listing #123", "Auction #456"
    private Instant createdAt;
    private boolean withdrawn;

    public PendingEarnings() {
        this.createdAt = Instant.now();
        this.withdrawn = false;
    }

    public PendingEarnings(UUID playerUuid, double amount, String source) {
        this();
        this.playerUuid = playerUuid;
        this.amount = amount;
        this.source = source;
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

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isWithdrawn() {
        return withdrawn;
    }

    public void setWithdrawn(boolean withdrawn) {
        this.withdrawn = withdrawn;
    }
}

