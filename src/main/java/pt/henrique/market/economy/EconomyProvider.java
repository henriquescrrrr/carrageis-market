package pt.henrique.market.economy;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Internal economy provider abstraction.
 * Both MultiBank and Vault providers implement this interface.
 * All monetary values are in <b>cents</b> ({@code long}).
 */
public interface EconomyProvider {

    /**
     * @return the display name of this provider (e.g. "MultiBank", "Vault")
     */
    String getName();

    /**
     * @return true if this provider is available and functional
     */
    boolean isAvailable();

    /**
     * Gets a player's balance in cents.
     */
    CompletableFuture<Long> getBalanceCents(UUID playerUuid);

    /**
     * Withdraws from a player's primary account.
     *
     * @param playerUuid  the player
     * @param amountCents amount in cents
     * @param reason      transaction memo
     * @return future resolving to true on success
     */
    CompletableFuture<Boolean> withdraw(UUID playerUuid, long amountCents, String reason);

    /**
     * Deposits into a player's primary account.
     *
     * @param playerUuid  the player
     * @param amountCents amount in cents
     * @param reason      transaction memo
     * @return future resolving to true on success
     */
    CompletableFuture<Boolean> deposit(UUID playerUuid, long amountCents, String reason);

    /**
     * Transfers between two players' primary accounts.
     *
     * @param fromUuid    payer
     * @param toUuid      receiver
     * @param amountCents amount in cents
     * @param reason      transaction memo
     * @return future resolving to true on success
     */
    CompletableFuture<Boolean> transfer(UUID fromUuid, UUID toUuid, long amountCents, String reason);
}

