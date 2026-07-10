package pt.henrique.market.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pt.henrique.market.Market;
import pt.henrique.market.model.ClaimItem;
import pt.henrique.market.util.InventoryUtil;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service layer for managing claim storage.
 * Players claim items from expired listings, won auctions, etc.
 */
public class ClaimService {

    private final Market plugin;

    public ClaimService(Market plugin) {
        this.plugin = plugin;
    }

    /**
     * Gets all pending claim items for a player
     *
     * @param playerUuid The player's UUID
     * @return CompletableFuture with list of claim items
     */
    public CompletableFuture<List<ClaimItem>> getPlayerClaimItems(UUID playerUuid) {
        return plugin.getDatabaseManager().getPlayerClaimItems(playerUuid);
    }

    /**
     * Counts pending claim items for a player
     *
     * @param playerUuid The player's UUID
     * @return CompletableFuture with count
     */
    public CompletableFuture<Integer> countPlayerClaimItems(UUID playerUuid) {
        return plugin.getDatabaseManager().countPlayerClaimItems(playerUuid);
    }

    /**
     * Claims a single item
     *
     * @param claimId The claim item ID
     * @param player  The player claiming
     * @return CompletableFuture with claim result
     */
    public CompletableFuture<ClaimResult> claimItem(int claimId, Player player) {
        return plugin.getDatabaseManager().getPlayerClaimItems(player.getUniqueId())
                .thenCompose(items -> {
                    // Find the specific item
                    ClaimItem claimItem = items.stream()
                            .filter(i -> i.getId() == claimId)
                            .findFirst()
                            .orElse(null);

                    if (claimItem == null) {
                        return CompletableFuture.completedFuture(ClaimResult.NOT_FOUND);
                    }

                    // Check inventory space
                    if (!InventoryUtil.hasSpace(player, claimItem.getItem())) {
                        return CompletableFuture.completedFuture(ClaimResult.INVENTORY_FULL);
                    }

                    // Remove from database first
                    return plugin.getDatabaseManager().removeClaimItem(claimId)
                            .thenApply(success -> {
                                if (!success) {
                                    return ClaimResult.FAILED;
                                }

                                // Give item to player on main thread
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    ItemStack leftover = InventoryUtil.giveItem(player, claimItem.getItem());
                                    if (leftover != null) {
                                        // This shouldn't happen since we checked space, but just in case
                                        plugin.getDatabaseManager().addClaimItem(new ClaimItem(
                                                player.getUniqueId(),
                                                leftover,
                                                claimItem.getReason(),
                                                claimItem.getSourceInfo()
                                        ));
                                    }
                                });

                                return ClaimResult.SUCCESS;
                            });
                });
    }

    /**
     * Claims all items for a player
     *
     * @param player The player claiming
     * @return CompletableFuture with number of items claimed
     */
    public CompletableFuture<Integer> claimAll(Player player) {
        return plugin.getDatabaseManager().getPlayerClaimItems(player.getUniqueId())
                .thenApply(items -> {
                    int claimed = 0;

                    for (ClaimItem item : items) {
                        // Check if player has space
                        if (!InventoryUtil.hasSpace(player, item.getItem())) {
                            break; // Stop claiming if inventory is full
                        }

                        // Remove from database (atomic gate — only one caller wins per id)
                        boolean removed = plugin.getDatabaseManager().removeClaimItem(item.getId()).join();
                        if (removed) {
                            // Give item on main thread. The gives are deferred to the
                            // next tick, so the earlier hasSpace() checks all saw the
                            // pre-give inventory; if the real add overflows, re-store the
                            // leftover to claim storage instead of silently destroying it.
                            final ClaimItem claimed0 = item;
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                ItemStack leftover = InventoryUtil.giveItem(player, claimed0.getItem());
                                if (leftover != null) {
                                    plugin.getDatabaseManager().addClaimItem(new ClaimItem(
                                            player.getUniqueId(),
                                            leftover,
                                            claimed0.getReason(),
                                            claimed0.getSourceInfo()
                                    ));
                                }
                            });
                            claimed++;
                        }
                    }

                    return claimed;
                });
    }

    /**
     * Adds an item to a player's claim storage
     *
     * @param playerUuid The player's UUID
     * @param item       The item to add
     * @param reason     The reason for the claim
     * @param sourceInfo Additional info about the source
     * @return CompletableFuture with the claim ID
     */
    public CompletableFuture<Integer> addClaimItem(UUID playerUuid, ItemStack item, ClaimItem.ClaimReason reason, String sourceInfo) {
        ClaimItem claimItem = new ClaimItem(playerUuid, item, reason, sourceInfo);
        return plugin.getDatabaseManager().addClaimItem(claimItem);
    }

    /**
     * Result of a claim attempt
     */
    public enum ClaimResult {
        SUCCESS,
        NOT_FOUND,
        INVENTORY_FULL,
        FAILED
    }
}

