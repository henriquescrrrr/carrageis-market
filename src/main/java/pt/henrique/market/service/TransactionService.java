package pt.henrique.market.service;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pt.henrique.market.Market;

import java.util.concurrent.CompletableFuture;

/**
 * Service layer for handling marketplace transactions.
 * Provides a unified interface for complex operations that
 * involve multiple services (purchases, bids, etc.)
 */
public class TransactionService {

    private final Market plugin;

    public TransactionService(Market plugin) {
        this.plugin = plugin;
    }

    /**
     * Validates an item before it can be listed or auctioned.
     * Checks material blacklist, keywords in name/lore, and other restrictions.
     *
     * @param item The item to validate
     * @return ValidationResult with success status and error message if applicable
     */
    public ValidationResult validateItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return new ValidationResult(false, "invalid-item");
        }

        // Check BuildersDream creative dream items
        if (plugin.getBuildersDreamHook() != null && plugin.getBuildersDreamHook().isDreamItem(item)) {
            return new ValidationResult(false, "blacklisted-item");
        }

        // Check material blacklist
        if (plugin.getConfigManager().isMaterialBlacklisted(item.getType())) {
            return new ValidationResult(false, "blacklisted-item");
        }

        // Check for blacklisted keywords in item name/lore
        if (item.hasItemMeta()) {
            var meta = item.getItemMeta();

            // Check display name
            if (meta.hasDisplayName()) {
                String displayName = meta.getDisplayName();
                if (plugin.getConfigManager().containsBlacklistedKeyword(displayName)) {
                    return new ValidationResult(false, "blacklisted-content");
                }
            }

            // Check lore
            if (meta.hasLore()) {
                for (String loreLine : meta.getLore()) {
                    if (plugin.getConfigManager().containsBlacklistedKeyword(loreLine)) {
                        return new ValidationResult(false, "blacklisted-content");
                    }
                }
            }
        }

        return new ValidationResult(true, null);
    }

    /**
     * Validates a price for a listing
     *
     * @param price The price to validate
     * @return true if the price is within allowed range
     */
    public boolean validateListingPrice(double price) {
        double min = plugin.getConfigManager().getMinPrice();
        double max = plugin.getConfigManager().getMaxPrice();
        return price >= min && price <= max;
    }

    /**
     * Validates a starting price for an auction
     *
     * @param price The price to validate
     * @return true if the price is within allowed range
     */
    public boolean validateAuctionStartPrice(double price) {
        double min = plugin.getConfigManager().getMinStartPrice();
        double max = plugin.getConfigManager().getMaxStartPrice();
        return price >= min && price <= max;
    }

    /**
     * Validates an auction duration
     *
     * @param hours The duration in hours
     * @return true if the duration is within allowed range
     */
    public boolean validateAuctionDuration(int hours) {
        int min = plugin.getConfigManager().getMinDurationHours();
        int max = plugin.getConfigManager().getMaxDurationHours();
        return hours >= min && hours <= max;
    }

    /**
     * Calculates the tax amount for a listing sale
     *
     * @param salePrice The sale price
     * @return The tax amount
     */
    public double calculateListingTax(double salePrice) {
        double taxPercent = plugin.getConfigManager().getMarketTax();
        return salePrice * (taxPercent / 100);
    }

    /**
     * Calculates the seller's earnings after tax for a listing
     *
     * @param salePrice The sale price
     * @return The amount the seller receives
     */
    public double calculateListingEarnings(double salePrice) {
        return salePrice - calculateListingTax(salePrice);
    }

    /**
     * Calculates the tax amount for an auction sale
     *
     * @param salePrice The final sale price
     * @return The tax amount
     */
    public double calculateAuctionTax(double salePrice) {
        double taxPercent = plugin.getConfigManager().getAuctionTax();
        return salePrice * (taxPercent / 100);
    }

    /**
     * Calculates the seller's earnings after tax for an auction
     *
     * @param salePrice The final sale price
     * @return The amount the seller receives
     */
    public double calculateAuctionEarnings(double salePrice) {
        return salePrice - calculateAuctionTax(salePrice);
    }

    /**
     * Creates a listing with full validation and item removal
     *
     * @param player        The seller
     * @param item          The item to list
     * @param amount        Amount to list
     * @param price         Total price
     * @param durationHours Duration in hours
     * @return CompletableFuture with transaction result
     */
    public CompletableFuture<TransactionResult> createListingTransaction(
            Player player, ItemStack item, int amount, double price, int durationHours) {

        // Validate item
        ValidationResult validation = validateItem(item);
        if (!validation.isValid()) {
            return CompletableFuture.completedFuture(
                    TransactionResult.failed(validation.getErrorKey()));
        }

        // Validate price
        if (!validateListingPrice(price)) {
            return CompletableFuture.completedFuture(
                    TransactionResult.failed("invalid-price"));
        }

        // Check listing limit
        return plugin.getListingService().canCreateListing(player.getUniqueId())
                .thenCompose(canCreate -> {
                    if (!canCreate) {
                        // Check if it's cooldown or limit
                        return plugin.getListingService().getRemainingCooldown(player.getUniqueId())
                                .thenApply(cooldown -> {
                                    if (cooldown > 0) {
                                        return TransactionResult.failed("listing-cooldown");
                                    }
                                    return TransactionResult.failed("listing-limit-reached");
                                });
                    }

                    // Create the listing
                    ItemStack listItem = item.clone();
                    listItem.setAmount(amount);

                    return plugin.getListingService().createListing(player, listItem, amount, price, durationHours)
                            .thenApply(id -> {
                                if (id > 0) {
                                    return TransactionResult.success(id);
                                }
                                return TransactionResult.failed("failed");
                            });
                });
    }

    /**
     * Creates an auction with full validation and item removal
     *
     * @param player        The seller
     * @param item          The item to auction
     * @param startPrice    Starting bid
     * @param buyoutPrice   Optional buyout price
     * @param durationHours Duration in hours
     * @return CompletableFuture with transaction result
     */
    public CompletableFuture<TransactionResult> createAuctionTransaction(
            Player player, ItemStack item, double startPrice, Double buyoutPrice, int durationHours) {

        // Validate item
        ValidationResult validation = validateItem(item);
        if (!validation.isValid()) {
            return CompletableFuture.completedFuture(
                    TransactionResult.failed(validation.getErrorKey()));
        }

        // Validate prices
        if (!validateAuctionStartPrice(startPrice)) {
            return CompletableFuture.completedFuture(
                    TransactionResult.failed("invalid-price"));
        }

        if (buyoutPrice != null && buyoutPrice <= startPrice) {
            return CompletableFuture.completedFuture(
                    TransactionResult.failed("invalid-price"));
        }

        // Validate duration
        if (!validateAuctionDuration(durationHours)) {
            return CompletableFuture.completedFuture(
                    TransactionResult.failed("invalid-duration"));
        }

        // Check auction limit
        return plugin.getAuctionService().canCreateAuction(player.getUniqueId())
                .thenCompose(canCreate -> {
                    if (!canCreate) {
                        return CompletableFuture.completedFuture(
                                TransactionResult.failed("auction-limit-reached"));
                    }

                    // Create the auction
                    return plugin.getAuctionService().createAuction(
                            player, item.clone(), startPrice, buyoutPrice, durationHours)
                            .thenApply(id -> {
                                if (id > 0) {
                                    return TransactionResult.success(id);
                                }
                                return TransactionResult.failed("failed");
                            });
                });
    }

    /**
     * Result of item validation
     */
    public record ValidationResult(boolean isValid, String errorKey) {
        public String getErrorKey() {
            return errorKey;
        }
    }

    /**
     * Result of a transaction
     */
    public static class TransactionResult {
        private final boolean success;
        private final int id;
        private final String errorKey;

        private TransactionResult(boolean success, int id, String errorKey) {
            this.success = success;
            this.id = id;
            this.errorKey = errorKey;
        }

        public static TransactionResult success(int id) {
            return new TransactionResult(true, id, null);
        }

        public static TransactionResult failed(String errorKey) {
            return new TransactionResult(false, -1, errorKey);
        }

        public boolean isSuccess() {
            return success;
        }

        public int getId() {
            return id;
        }

        public String getErrorKey() {
            return errorKey;
        }
    }
}

