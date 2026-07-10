package pt.henrique.market.service;

import org.bukkit.entity.Player;
import pt.henrique.market.Market;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service layer for managing player earnings.
 * Handles pending earnings from sales and withdrawals.
 */
public class EarningsService {

    private final Market plugin;

    public EarningsService(Market plugin) {
        this.plugin = plugin;
    }

    /**
     * Gets the total pending earnings for a player
     *
     * @param playerUuid The player's UUID
     * @return CompletableFuture with the total pending amount
     */
    public CompletableFuture<Double> getPendingEarnings(UUID playerUuid) {
        return plugin.getDatabaseManager().getPlayerPendingEarnings(playerUuid);
    }

    /**
     * Withdraws all pending earnings for a player
     *
     * @param player The player withdrawing
     * @return CompletableFuture with the withdrawal result
     */
    public CompletableFuture<WithdrawResult> withdrawAll(Player player) {
        return getPendingEarnings(player.getUniqueId())
                .thenCompose(amount -> {
                    if (amount <= 0) {
                        return CompletableFuture.completedFuture(WithdrawResult.NO_EARNINGS);
                    }

                    // Mark earnings as withdrawn in database first
                    return plugin.getDatabaseManager().withdrawAllEarnings(player.getUniqueId())
                            .thenApply(success -> {
                                if (!success) {
                                    return WithdrawResult.FAILED;
                                }

                                // Deposit to player's economy account
                                if (!plugin.getEconomyManager().deposit(player.getUniqueId(), amount)) {
                                    // Economy failed - this is a problem
                                    // The earnings are marked as withdrawn but money wasn't given
                                    plugin.getLogger().severe("Failed to deposit " + amount + " to " + player.getName() + " after marking earnings as withdrawn!");
                                    return WithdrawResult.ECONOMY_ERROR;
                                }

                                return WithdrawResult.success(amount);
                            });
                });
    }

    /**
     * Adds pending earnings for a player
     *
     * @param playerUuid The player's UUID
     * @param amount     The amount to add
     * @param source     Description of the source (e.g., "Listing #123")
     * @return CompletableFuture with the earnings ID
     */
    public CompletableFuture<Integer> addEarnings(UUID playerUuid, double amount, String source) {
        var earnings = new pt.henrique.market.model.PendingEarnings(playerUuid, amount, source);
        return plugin.getDatabaseManager().addPendingEarnings(earnings);
    }

    /**
     * Result of a withdrawal attempt
     */
    public static class WithdrawResult {
        private final boolean success;
        private final double amount;
        private final String error;

        private WithdrawResult(boolean success, double amount, String error) {
            this.success = success;
            this.amount = amount;
            this.error = error;
        }

        public static WithdrawResult success(double amount) {
            return new WithdrawResult(true, amount, null);
        }

        public static final WithdrawResult NO_EARNINGS = new WithdrawResult(false, 0, "no_earnings");
        public static final WithdrawResult FAILED = new WithdrawResult(false, 0, "failed");
        public static final WithdrawResult ECONOMY_ERROR = new WithdrawResult(false, 0, "economy_error");

        public boolean isSuccess() {
            return success;
        }

        public double getAmount() {
            return amount;
        }

        public String getError() {
            return error;
        }
    }
}

