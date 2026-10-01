package pt.henrique.market.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pt.henrique.market.Market;
import pt.henrique.market.model.ClaimItem;
import pt.henrique.market.model.Listing;
import pt.henrique.market.model.PendingEarnings;
import pt.henrique.market.model.SaleNotification;
import pt.henrique.market.util.InventoryUtil;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service layer for managing fixed-price market listings.
 * Handles creation, purchase, cancellation, and expiration of listings.
 */
public class ListingService {

    private final Market plugin;

    // Simple cache for active listings
    private List<Listing> cachedListings;
    private long cacheExpiry = 0;

    // Track pending operations to prevent double-purchases
    private final Map<Integer, Boolean> pendingPurchases = new ConcurrentHashMap<>();

    public ListingService(Market plugin) {
        this.plugin = plugin;
    }

    /**
     * Creates a new listing for a player
     *
     * @param player      The seller
     * @param item        The item to sell
     * @param amount      Amount of items
     * @param price       Total price for all items
     * @param durationHours Duration in hours
     * @return CompletableFuture with the listing ID or -1 if failed
     */
    public CompletableFuture<Integer> createListing(Player player, ItemStack item, int amount, double price, int durationHours) {
        // Validate
        if (item == null || item.getType().isAir()) {
            return CompletableFuture.completedFuture(-1);
        }

        // Last gate before persistence: applies the blacklist to the payload too,
        // so a banned material cannot enter a listing inside a container.
        if (!plugin.getTransactionService().validateItem(item).isValid()) {
            return CompletableFuture.completedFuture(-1);
        }

        // Create listing object
        Listing listing = new Listing(
                player.getUniqueId(),
                player.getName(),
                item.clone(),
                amount,
                price,
                Instant.now().plusSeconds(durationHours * 3600L)
        );

        // Save to database
        return plugin.getDatabaseManager().createListing(listing)
                .thenApply(id -> {
                    if (id > 0) {
                        invalidateCache();
                        // Update cooldown
                        plugin.getDatabaseManager().updateLastListingTime(player.getUniqueId());
                    }
                    return id;
                });
    }

    /**
     * Gets all active listings with caching
     */
    public CompletableFuture<List<Listing>> getActiveListings() {
        long now = System.currentTimeMillis();

        // Return cached if still valid
        if (cachedListings != null && now < cacheExpiry) {
            return CompletableFuture.completedFuture(cachedListings);
        }

        return plugin.getDatabaseManager().getActiveListings()
                .thenApply(listings -> {
                    cachedListings = listings;
                    cacheExpiry = System.currentTimeMillis() +
                            (plugin.getConfigManager().getCacheDuration() * 1000L);
                    return listings;
                });
    }

    /**
     * Gets a specific listing by ID
     */
    public CompletableFuture<Optional<Listing>> getListing(int id) {
        return plugin.getDatabaseManager().getListing(id);
    }

    /**
     * Gets all listings for a specific player
     */
    public CompletableFuture<List<Listing>> getPlayerListings(UUID playerUuid) {
        return plugin.getDatabaseManager().getPlayerListings(playerUuid);
    }

    /**
     * Counts active listings for a player
     */
    public CompletableFuture<Integer> countPlayerListings(UUID playerUuid) {
        return plugin.getDatabaseManager().countPlayerListings(playerUuid);
    }

    /**
     * Checks if a player can create a new listing (not at limit and no cooldown)
     */
    public CompletableFuture<Boolean> canCreateListing(UUID playerUuid) {
        int maxListings = plugin.getConfigManager().getMaxListingsPerPlayer();

        return countPlayerListings(playerUuid)
                .thenCompose(count -> {
                    if (count >= maxListings) {
                        return CompletableFuture.completedFuture(false);
                    }

                    int cooldown = plugin.getConfigManager().getListingCooldown();
                    if (cooldown <= 0) {
                        return CompletableFuture.completedFuture(true);
                    }

                    return plugin.getDatabaseManager().getLastListingTime(playerUuid)
                            .thenApply(lastTime -> {
                                if (lastTime.isEmpty()) return true;
                                return Instant.now().isAfter(lastTime.get().plusSeconds(cooldown));
                            });
                });
    }

    /**
     * Calculates the remaining cooldown time in seconds
     */
    public CompletableFuture<Long> getRemainingCooldown(UUID playerUuid) {
        int cooldown = plugin.getConfigManager().getListingCooldown();
        if (cooldown <= 0) {
            return CompletableFuture.completedFuture(0L);
        }

        return plugin.getDatabaseManager().getLastListingTime(playerUuid)
                .thenApply(lastTime -> {
                    if (lastTime.isEmpty()) return 0L;
                    long remaining = lastTime.get().plusSeconds(cooldown).getEpochSecond() - Instant.now().getEpochSecond();
                    return Math.max(0, remaining);
                });
    }

    /**
     * Attempts to purchase a listing.
     * This is an atomic operation that prevents double-purchases.
     *
     * @param listingId The listing to purchase
     * @param buyer     The buyer
     * @return CompletableFuture with success status
     */
    public CompletableFuture<PurchaseResult> purchaseListing(int listingId, Player buyer) {
        // Check if already processing this listing
        if (pendingPurchases.putIfAbsent(listingId, true) != null) {
            return CompletableFuture.completedFuture(PurchaseResult.ALREADY_PROCESSING);
        }

        try {
            return getListing(listingId)
                    .thenCompose(optListing -> {
                        if (optListing.isEmpty()) {
                            pendingPurchases.remove(listingId);
                            return CompletableFuture.completedFuture(PurchaseResult.NOT_FOUND);
                        }

                        Listing listing = optListing.get();

                        // Can't buy own listing
                        if (listing.getSellerUuid().equals(buyer.getUniqueId())) {
                            pendingPurchases.remove(listingId);
                            return CompletableFuture.completedFuture(PurchaseResult.OWN_LISTING);
                        }

                        // Check buyer funds
                        if (!plugin.getEconomyManager().has(buyer.getUniqueId(), listing.getPrice())) {
                            pendingPurchases.remove(listingId);
                            return CompletableFuture.completedFuture(PurchaseResult.INSUFFICIENT_FUNDS);
                        }

                        // Attempt atomic purchase in DB
                        return plugin.getDatabaseManager().purchaseListing(listingId, buyer.getUniqueId(), buyer.getName())
                                .thenApply(success -> {
                                    if (!success) {
                                        pendingPurchases.remove(listingId);
                                        return PurchaseResult.ALREADY_SOLD;
                                    }

                                    // Withdraw from buyer
                                    if (!plugin.getEconomyManager().withdraw(buyer.getUniqueId(), listing.getPrice())) {
                                        // Rollback DB change
                                        plugin.getDatabaseManager().updateListingStatus(listingId, Listing.ListingStatus.ACTIVE);
                                        pendingPurchases.remove(listingId);
                                        return PurchaseResult.ECONOMY_ERROR;
                                    }

                                    // Calculate seller earnings after tax
                                    double tax = plugin.getConfigManager().getMarketTax();
                                    double sellerEarnings = listing.getPrice() * (1 - tax / 100);

                                    // Deposit directly to seller's economy account
                                    String itemName = listing.getItem().getType().name();
                                    String formattedPrice = plugin.getMessageManager().formatCurrency(sellerEarnings);
                                    plugin.getEconomyManager().depositCents(
                                            listing.getSellerUuid(),
                                            pt.henrique.market.economy.EconomyManager.toCents(sellerEarnings),
                                            "Market: Sale of " + itemName
                                    ).thenAccept(depositSuccess -> {
                                        if (!depositSuccess) {
                                            // Fallback: store as pending earnings if deposit fails
                                            plugin.getLogger().warning("Failed to deposit " + sellerEarnings
                                                    + " to " + listing.getSellerUuid() + " for Listing #" + listingId
                                                    + ". Storing as pending earnings.");
                                            PendingEarnings earnings = new PendingEarnings(
                                                    listing.getSellerUuid(),
                                                    sellerEarnings,
                                                    "Listing #" + listingId
                                            );
                                            plugin.getDatabaseManager().addPendingEarnings(earnings);
                                        }
                                    });

                                    // Give item to buyer or add to claim storage
                                    Bukkit.getScheduler().runTask(plugin, () -> {
                                        ItemStack item = listing.getItem().clone();
                                        item.setAmount(listing.getAmount());
                                        List<ItemStack> leftovers = InventoryUtil.giveItemSplit(buyer, item);

                                        if (!leftovers.isEmpty()) {
                                            // Couldn't fit in inventory, add to claim storage
                                            for (ItemStack leftover : leftovers) {
                                                plugin.getDatabaseManager().addClaimItem(new ClaimItem(
                                                        buyer.getUniqueId(),
                                                        leftover,
                                                        ClaimItem.ClaimReason.PURCHASE_FULL_INVENTORY,
                                                        "Listing #" + listingId
                                                ));
                                            }
                                            buyer.sendMessage(plugin.getMessageManager().getPrefixedP(buyer, "messages.claim-inventory-full"));
                                        }
                                    });

                                    // Notify seller if online, or store notification for when they join
                                    if (plugin.getConfigManager().isNotifyOnSale()) {
                                        Player seller = Bukkit.getPlayer(listing.getSellerUuid());
                                        if (seller != null && seller.isOnline()) {
                                            Map<String, String> placeholders = Map.of(
                                                    "item", itemName,
                                                    "amount", String.valueOf(listing.getAmount()),
                                                    "buyer", buyer.getName(),
                                                    "price", formattedPrice
                                            );
                                            seller.sendMessage(plugin.getMessageManager().getPrefixedP(seller, "messages.listing-sold", placeholders));
                                        } else {
                                            // Seller is offline — store notification for delivery on join
                                            SaleNotification notification = new SaleNotification(
                                                    listing.getSellerUuid(),
                                                    itemName + " x" + listing.getAmount(),
                                                    formattedPrice,
                                                    buyer.getName()
                                            );
                                            plugin.getDatabaseManager().addSaleNotification(notification);
                                        }
                                    }

                                    invalidateCache();
                                    pendingPurchases.remove(listingId);
                                    return PurchaseResult.SUCCESS;
                                });
                    });
        } catch (Exception e) {
            pendingPurchases.remove(listingId);
            throw e;
        }
    }

    /**
     * Cancels a listing and returns the item to the seller's claim storage
     *
     * @param listingId The listing to cancel
     * @param playerUuid The player attempting to cancel (must be seller or admin)
     * @param isAdmin Whether this is an admin action
     * @return CompletableFuture with success status
     */
    public CompletableFuture<Boolean> cancelListing(int listingId, UUID playerUuid, boolean isAdmin) {
        return getListing(listingId)
                .thenCompose(optListing -> {
                    if (optListing.isEmpty()) {
                        return CompletableFuture.completedFuture(false);
                    }

                    Listing listing = optListing.get();

                    // Check permission
                    if (!isAdmin && !listing.getSellerUuid().equals(playerUuid)) {
                        return CompletableFuture.completedFuture(false);
                    }

                    // Update status atomically (only succeeds if still ACTIVE) so a
                    // double-cancel — or a cancel racing a purchase — can't clone the
                    // item into claim storage or reverse an already-completed sale.
                    return plugin.getDatabaseManager().transitionListingFromActive(listingId, Listing.ListingStatus.CANCELLED)
                            .thenApply(success -> {
                                if (success) {
                                    // Return item to claim storage
                                    ItemStack item = listing.getItem().clone();
                                    item.setAmount(listing.getAmount());
                                    ClaimItem claimItem = new ClaimItem(
                                            listing.getSellerUuid(),
                                            item,
                                            ClaimItem.ClaimReason.CANCELLED_LISTING,
                                            "Listing #" + listingId
                                    );
                                    plugin.getDatabaseManager().addClaimItem(claimItem);
                                    invalidateCache();
                                }
                                return success;
                            });
                });
    }

    /**
     * Processes expired listings - moves items to claim storage
     */
    public CompletableFuture<Void> processExpiredListings() {
        return plugin.getDatabaseManager().getExpiredListings()
                .thenAccept(listings -> {
                    for (Listing listing : listings) {
                        // Update status to expired atomically — guards against two
                        // overlapping task runs (or expire racing a purchase) both
                        // moving the same item to claim storage.
                        plugin.getDatabaseManager().transitionListingFromActive(listing.getId(), Listing.ListingStatus.EXPIRED)
                                .thenAccept(success -> {
                                    if (success) {
                                        // Return item to claim storage
                                        ItemStack item = listing.getItem().clone();
                                        item.setAmount(listing.getAmount());
                                        ClaimItem claimItem = new ClaimItem(
                                                listing.getSellerUuid(),
                                                item,
                                                ClaimItem.ClaimReason.EXPIRED_LISTING,
                                                "Listing #" + listing.getId()
                                        );
                                        plugin.getDatabaseManager().addClaimItem(claimItem);

                                        // Notify seller if online
                                        if (plugin.getConfigManager().isNotifyOnExpire()) {
                                            Player seller = Bukkit.getPlayer(listing.getSellerUuid());
                                            if (seller != null && seller.isOnline()) {
                                                seller.sendMessage(plugin.getMessageManager().getPrefixedP(seller,
                                                        "messages.listing-expired",
                                                        "id", String.valueOf(listing.getId())
                                                ));
                                            }
                                        }
                                    }
                                });
                    }
                    if (!listings.isEmpty()) {
                        invalidateCache();
                    }
                });
    }

    /**
     * Invalidates the listing cache
     */
    public void invalidateCache() {
        cachedListings = null;
        cacheExpiry = 0;
    }

    /**
     * Result of a purchase attempt
     */
    public enum PurchaseResult {
        SUCCESS,
        NOT_FOUND,
        ALREADY_SOLD,
        OWN_LISTING,
        INSUFFICIENT_FUNDS,
        ECONOMY_ERROR,
        ALREADY_PROCESSING
    }
}

