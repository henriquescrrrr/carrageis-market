package pt.henrique.market.economy;

import org.bukkit.Bukkit;
import pt.henrique.market.Market;
import pt.henrique.multibank.api.MultiBankApi;
import pt.henrique.multibank.economy.TransactionResult;
import pt.henrique.multibank.model.Account;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Economy provider backed by the MultiBank API.
 * All operations are natively async and use long cents — no conversion needed.
 */
public class MultiBankEconomyProvider implements EconomyProvider {

    private final Market plugin;
    private MultiBankApi api;

    // A fixed UUID used as "actor" for server-initiated transactions
    private static final UUID SERVER_ACTOR = new UUID(0, 0);

    public MultiBankEconomyProvider(Market plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "MultiBank";
    }

    @Override
    public boolean isAvailable() {
        try {
            var registration = Bukkit.getServicesManager().getRegistration(MultiBankApi.class);
            if (registration == null) {
                debug("MultiBankApi not found in ServicesManager");
                return false;
            }
            api = registration.getProvider();
            if (api == null) {
                debug("MultiBankApi registration found but provider is null");
                return false;
            }
            debug("MultiBankApi loaded successfully");
            return true;
        } catch (NoClassDefFoundError e) {
            debug("MultiBankApi class not found on classpath: " + e.getMessage());
            return false;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error checking MultiBank availability", e);
            return false;
        }
    }

    // ==================== OPERATIONS ====================

    @Override
    public CompletableFuture<Long> getBalanceCents(UUID playerUuid) {
        debug("getBalanceCents() player=" + playerUuid);
        return getPrimaryAccountId(playerUuid)
                .thenCompose(accountId -> {
                    if (accountId == null) {
                        debug("getBalanceCents() — no primary account for " + playerUuid);
                        return CompletableFuture.completedFuture(0L);
                    }
                    return api.getBalanceCents(accountId);
                })
                .thenApply(balance -> {
                    debug("getBalanceCents() — player " + playerUuid + " = " + balance + " cents");
                    return balance;
                })
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING, "MultiBank getBalanceCents failed for " + playerUuid, ex);
                    return 0L;
                });
    }

    @Override
    public CompletableFuture<Boolean> withdraw(UUID playerUuid, long amountCents, String reason) {
        debug("withdraw() player=" + playerUuid + ", cents=" + amountCents + ", reason=" + reason);
        return getPrimaryAccountId(playerUuid).thenCompose(accountId -> {
            if (accountId == null) {
                debug("withdraw() FAILED: no primary account for " + playerUuid);
                return CompletableFuture.completedFuture(false);
            }
            return api.withdraw(accountId, amountCents, reason, playerUuid)
                    .thenApply((TransactionResult result) -> {
                        boolean success = result.isSuccess();
                        debug("withdraw() result: success=" + success + ", status=" + result.status());
                        if (!success) {
                            plugin.getLogger().warning("MultiBank withdraw failed for " + playerUuid
                                    + ": status=" + result.status() + ", amount=" + amountCents);
                        }
                        return success;
                    })
                    .exceptionally(ex -> {
                        plugin.getLogger().log(Level.WARNING, "MultiBank withdraw exception: " + reason, ex);
                        return false;
                    });
        });
    }

    @Override
    public CompletableFuture<Boolean> deposit(UUID playerUuid, long amountCents, String reason) {
        debug("deposit() player=" + playerUuid + ", cents=" + amountCents + ", reason=" + reason);
        return getPrimaryAccountId(playerUuid).thenCompose(accountId -> {
            if (accountId == null) {
                debug("deposit() FAILED: no primary account for " + playerUuid);
                return CompletableFuture.completedFuture(false);
            }
            return api.deposit(accountId, amountCents, reason, SERVER_ACTOR)
                    .thenApply((TransactionResult result) -> {
                        boolean success = result.isSuccess();
                        debug("deposit() result: success=" + success + ", status=" + result.status());
                        if (!success) {
                            plugin.getLogger().warning("MultiBank deposit failed for " + playerUuid
                                    + ": status=" + result.status() + ", amount=" + amountCents);
                        }
                        return success;
                    })
                    .exceptionally(ex -> {
                        plugin.getLogger().log(Level.WARNING, "MultiBank deposit exception: " + reason, ex);
                        return false;
                    });
        });
    }

    @Override
    public CompletableFuture<Boolean> transfer(UUID fromUuid, UUID toUuid, long amountCents, String reason) {
        debug("transfer() from=" + fromUuid + ", to=" + toUuid + ", cents=" + amountCents);
        return getPrimaryAccountId(fromUuid).thenCompose(fromId -> {
            if (fromId == null) {
                debug("transfer() FAILED: no primary account for sender " + fromUuid);
                return CompletableFuture.completedFuture(false);
            }
            return getPrimaryAccountId(toUuid).thenCompose(toId -> {
                if (toId == null) {
                    debug("transfer() FAILED: no primary account for receiver " + toUuid);
                    return CompletableFuture.completedFuture(false);
                }
                return api.transfer(fromId, toId, amountCents, reason, fromUuid)
                        .thenApply((TransactionResult result) -> {
                            boolean success = result.isSuccess();
                            debug("transfer() result: success=" + success + ", status=" + result.status());
                            if (!success) {
                                plugin.getLogger().warning("MultiBank transfer failed: " + reason
                                        + " status=" + result.status());
                            }
                            return success;
                        })
                        .exceptionally(ex -> {
                            plugin.getLogger().log(Level.WARNING, "MultiBank transfer exception: " + reason, ex);
                            return false;
                        });
            });
        });
    }

    // ==================== HELPERS ====================

    private CompletableFuture<String> getPrimaryAccountId(UUID playerUuid) {
        return api.getPrimaryAccount(playerUuid)
                .thenApply((Account account) -> {
                    if (account == null) {
                        debug("getPrimaryAccountId() returned null for " + playerUuid);
                        return null;
                    }
                    String accountId = account.getAccountId();
                    debug("getPrimaryAccountId() resolved: id=" + accountId
                            + ", balance=" + account.getBalanceCents()
                            + ", frozen=" + account.isFrozen());
                    return accountId;
                })
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING,
                            "Failed to get primary account from MultiBank for " + playerUuid, ex);
                    return null;
                });
    }

    private boolean isDebug() {
        return plugin.getConfig().getBoolean("debug", false);
    }

    private void debug(String message) {
        if (isDebug()) {
            plugin.getLogger().info("[DEBUG-MultiBank] " + message);
        }
    }
}

