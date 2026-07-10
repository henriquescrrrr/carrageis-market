package pt.henrique.market.model;

import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a fixed-price market listing
 */
public class Listing {

    private int id;
    private UUID sellerUuid;
    private String sellerName;
    private ItemStack item;
    private int amount;
    private double price;
    private Instant createdAt;
    private Instant expiresAt;
    private ListingStatus status;

    public Listing() {
        this.status = ListingStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public Listing(UUID sellerUuid, String sellerName, ItemStack item, int amount, double price, Instant expiresAt) {
        this();
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.item = item;
        this.amount = amount;
        this.price = price;
        this.expiresAt = expiresAt;
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

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public ListingStatus getStatus() {
        return status;
    }

    public void setStatus(ListingStatus status) {
        this.status = status;
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean isActive() {
        return status == ListingStatus.ACTIVE && !isExpired();
    }

    public enum ListingStatus {
        ACTIVE,
        SOLD,
        EXPIRED,
        CANCELLED
    }
}

