package pt.henrique.market.economy;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import pt.henrique.market.Market;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Economy provider backed by Vault (optional, reflection-based).
 * <p>
 * No compile-time Vault dependency is needed — all Vault API calls use reflection.
 * This allows Market to compile and run without Vault on the classpath;
 * Vault is discovered at runtime if present.
 * <p>
 * Vault uses synchronous {@code double}-based API. This provider converts
 * between Market's internal {@code long} cents and Vault's doubles using
 * HALF_UP rounding.
 * <p>
 * Vault has no native {@code transfer()} — it is emulated as withdraw + deposit
 * with compensating rollback if the deposit leg fails.
 */
public class VaultEconomyProvider implements EconomyProvider {

    private final Market plugin;

    // Vault Economy instance and cached reflection methods
    private Object vaultEconomy;       // net.milkbowl.vault.economy.Economy
    private Method getBalanceMethod;   // getBalance(OfflinePlayer)
    private Method withdrawMethod;     // withdrawPlayer(OfflinePlayer, double)
    private Method depositMethod;      // depositPlayer(OfflinePlayer, double)
    private Method getNameMethod;      // getName()

    // EconomyResponse methods
    private Method txSuccessMethod;    // transactionSuccess()

    public VaultEconomyProvider(Market plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Vault";
    }

    @Override
    public boolean isAvailable() {
        try {
            // Check if the Vault plugin is present
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
                debug("Vault plugin not found");
                return false;
            }

            // Load the Economy class via reflection
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");

            // Get the registered service provider
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(economyClass);
            if (rsp == null) {
                debug("No Vault Economy provider registered");
                return false;
            }

            vaultEconomy = rsp.getProvider();
            if (vaultEconomy == null) {
                debug("Vault Economy registration found but provider is null");
                return false;
            }

            // Cache reflection methods
            getBalanceMethod = vaultEconomy.getClass().getMethod("getBalance", OfflinePlayer.class);
            withdrawMethod = vaultEconomy.getClass().getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            depositMethod = vaultEconomy.getClass().getMethod("depositPlayer", OfflinePlayer.class, double.class);
            getNameMethod = vaultEconomy.getClass().getMethod("getName");

            // Cache EconomyResponse.transactionSuccess()
            Class<?> responseClass = Class.forName("net.milkbowl.vault.economy.EconomyResponse");
            txSuccessMethod = responseClass.getMethod("transactionSuccess");

            String name = (String) getNameMethod.invoke(vaultEconomy);
            debug("Vault Economy loaded: " + name);
            return true;

        } catch (ClassNotFoundException e) {
            debug("Vault classes not on classpath: " + e.getMessage());
            return false;
        } catch (NoClassDefFoundError e) {
            debug("Vault class dependency missing: " + e.getMessage());
            return false;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error checking Vault availability", e);
            return false;
        }
    }

    // ==================== OPERATIONS ====================

    @Override
    public CompletableFuture<Long> getBalanceCents(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
                double balance = (double) getBalanceMethod.invoke(vaultEconomy, player);
                long cents = doubleToCents(balance);
                debug("getBalanceCents() player=" + playerUuid + ", vault=" + balance + ", cents=" + cents);
                return cents;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Vault getBalance failed for " + playerUuid, e);
                return 0L;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> withdraw(UUID playerUuid, long amountCents, String reason) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
                double amount = centsToDouble(amountCents);
                debug("withdraw() player=" + playerUuid + ", cents=" + amountCents + ", double=" + amount);

                Object response = withdrawMethod.invoke(vaultEconomy, player, amount);
                boolean success = (boolean) txSuccessMethod.invoke(response);

                debug("withdraw() result: success=" + success);
                if (!success) {
                    plugin.getLogger().warning("Vault withdraw failed for " + playerUuid);
                }
                return success;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Vault withdraw exception for " + playerUuid, e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> deposit(UUID playerUuid, long amountCents, String reason) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
                double amount = centsToDouble(amountCents);
                debug("deposit() player=" + playerUuid + ", cents=" + amountCents + ", double=" + amount);

                Object response = depositMethod.invoke(vaultEconomy, player, amount);
                boolean success = (boolean) txSuccessMethod.invoke(response);

                debug("deposit() result: success=" + success);
                if (!success) {
                    plugin.getLogger().warning("Vault deposit failed for " + playerUuid);
                }
                return success;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Vault deposit exception for " + playerUuid, e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> transfer(UUID fromUuid, UUID toUuid, long amountCents, String reason) {
        // Vault has no native transfer — emulate with withdraw + deposit
        // with compensating rollback if the deposit fails
        return CompletableFuture.supplyAsync(() -> {
            try {
                OfflinePlayer from = Bukkit.getOfflinePlayer(fromUuid);
                OfflinePlayer to = Bukkit.getOfflinePlayer(toUuid);
                double amount = centsToDouble(amountCents);

                debug("transfer() from=" + fromUuid + ", to=" + toUuid
                        + ", cents=" + amountCents + ", double=" + amount);

                // Step 1: Withdraw from sender
                Object withdrawResp = withdrawMethod.invoke(vaultEconomy, from, amount);
                boolean withdrawOk = (boolean) txSuccessMethod.invoke(withdrawResp);
                if (!withdrawOk) {
                    debug("transfer() withdraw leg FAILED");
                    return false;
                }

                // Step 2: Deposit to receiver
                Object depositResp = depositMethod.invoke(vaultEconomy, to, amount);
                boolean depositOk = (boolean) txSuccessMethod.invoke(depositResp);
                if (!depositOk) {
                    // Rollback: re-deposit to sender
                    debug("transfer() deposit leg FAILED, rolling back withdraw");
                    plugin.getLogger().warning("Vault transfer deposit failed, rolling back withdraw");
                    depositMethod.invoke(vaultEconomy, from, amount);
                    return false;
                }

                debug("transfer() SUCCESS");
                return true;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Vault transfer exception", e);
                return false;
            }
        });
    }

    // ==================== CONVERSION ====================

    private static long doubleToCents(double amount) {
        return BigDecimal.valueOf(amount)
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private static double centsToDouble(long cents) {
        return cents / 100.0;
    }

    // ==================== DEBUG ====================

    private boolean isDebug() {
        return plugin.getConfig().getBoolean("debug", false);
    }

    private void debug(String message) {
        if (isDebug()) {
            plugin.getLogger().info("[DEBUG-Vault] " + message);
        }
    }
}
