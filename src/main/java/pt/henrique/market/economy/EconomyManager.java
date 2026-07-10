package pt.henrique.market.economy;

import pt.henrique.market.Market;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Manages economy operations using a dual-provider system.
 * <p>
 * Providers are tried in the order specified by {@code economy.priority} in config.
 * Supported providers:
 * <ul>
 *   <li><b>MultiBank</b> — native async API, long cents (preferred)</li>
 *   <li><b>Vault</b> — synchronous double-based API, adapted to async interface</li>
 * </ul>
 * <p>
 * All monetary values are internally in <b>cents</b> ({@code long}).
 * Legacy double-based helpers ({@link #toCents(double)}, {@link #toDouble(long)})
 * are provided for bridging with config values stored as doubles.
 */
public class EconomyManager {

    private final Market plugin;
    private EconomyProvider provider;
    private String providerName = "None";

    public EconomyManager(Market plugin) {
        this.plugin = plugin;
    }

    private boolean isDebug() {
        return plugin.getConfig().getBoolean("debug", false);
    }

    private void debug(String message) {
        if (isDebug()) {
            plugin.getLogger().info("[DEBUG-Economy] " + message);
        }
    }

    // ==================== SETUP ====================

    /**
     * Attempts to set up an economy provider based on config priority.
     * <p>
     * Config keys:
     * <ul>
     *   <li>{@code economy.mode} — "auto" (try priority list) or provider name to force</li>
     *   <li>{@code economy.priority} — ordered list e.g. ["MultiBank", "Vault"]</li>
     * </ul>
     *
     * @return true if an economy provider was found and loaded
     */
    public boolean setupEconomy() {
        plugin.getLogger().info("=== Economy Detection ===");

        String mode = plugin.getConfig().getString("economy.mode", "auto").toLowerCase();
        List<String> priority = plugin.getConfig().getStringList("economy.priority");

        // Default priority if not configured
        if (priority.isEmpty()) {
            priority = List.of("MultiBank", "Vault");
        }

        debug("Economy mode: " + mode);
        debug("Economy priority: " + priority);

        // Build available providers
        Map<String, EconomyProvider> providers = new LinkedHashMap<>();
        providers.put("multibank", new MultiBankEconomyProvider(plugin));
        providers.put("vault", new VaultEconomyProvider(plugin));

        if (mode.equals("auto")) {
            // Try each provider in priority order
            for (String name : priority) {
                String key = name.toLowerCase().trim();
                EconomyProvider candidate = providers.get(key);
                if (candidate == null) {
                    plugin.getLogger().warning("Unknown economy provider in priority list: " + name);
                    continue;
                }
                plugin.getLogger().info("Trying economy provider: " + candidate.getName() + "...");
                try {
                    if (candidate.isAvailable()) {
                        provider = candidate;
                        providerName = candidate.getName();
                        plugin.getLogger().info("Economy provider selected: " + providerName);
                        break;
                    } else {
                        plugin.getLogger().info("  -> " + candidate.getName() + " not available, trying next...");
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING,
                            "Error checking provider " + candidate.getName(), e);
                }
            }
        } else {
            // Force a specific provider
            EconomyProvider candidate = providers.get(mode);
            if (candidate == null) {
                plugin.getLogger().severe("Unknown economy mode: " + mode);
                plugin.getLogger().severe("Valid values: auto, multibank, vault");
                return false;
            }
            plugin.getLogger().info("Economy mode FORCE: " + candidate.getName());
            try {
                if (candidate.isAvailable()) {
                    provider = candidate;
                    providerName = candidate.getName();
                    plugin.getLogger().info("Economy provider selected: " + providerName);
                } else {
                    plugin.getLogger().severe(candidate.getName()
                            + " is not available but was forced via economy.mode!");
                    return false;
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Error loading forced provider " + candidate.getName(), e);
                return false;
            }
        }

        if (provider == null) {
            plugin.getLogger().severe("=== Economy Setup FAILED ===");
            plugin.getLogger().severe("No economy provider available!");
            plugin.getLogger().severe("Install MultiBank (preferred) or Vault with an economy plugin.");
            plugin.getLogger().severe("Priority tried: " + priority);
            return false;
        }

        plugin.getLogger().info("Debug mode: " + (isDebug() ? "ENABLED" : "disabled"));
        plugin.getLogger().info("=== Economy Setup Complete ===");
        return true;
    }

    /**
     * Gets the name of the active economy provider.
     */
    public String getProviderName() {
        return providerName;
    }

    /**
     * Checks if the economy manager is ready.
     */
    public boolean isReady() {
        return provider != null;
    }

    // ==================== ASYNC OPERATIONS (cents) ====================

    /**
     * Gets a player's balance in cents asynchronously.
     */
    public CompletableFuture<Long> getBalanceCents(UUID playerUuid) {
        if (provider == null) return CompletableFuture.completedFuture(0L);
        return provider.getBalanceCents(playerUuid);
    }

    /**
     * Checks if a player has at least {@code amountCents} asynchronously.
     */
    public CompletableFuture<Boolean> hasCents(UUID playerUuid, long amountCents) {
        return getBalanceCents(playerUuid).thenApply(bal -> bal >= amountCents);
    }

    /**
     * Withdraws money from a player's primary account.
     */
    public CompletableFuture<Boolean> withdrawCents(UUID playerUuid, long amountCents, String reason) {
        if (amountCents <= 0) return CompletableFuture.completedFuture(true);
        if (provider == null) return CompletableFuture.completedFuture(false);
        debug("withdrawCents: player=" + playerUuid + ", cents=" + amountCents + ", reason=" + reason);
        return provider.withdraw(playerUuid, amountCents, reason);
    }

    /**
     * Deposits money into a player's primary account.
     */
    public CompletableFuture<Boolean> depositCents(UUID playerUuid, long amountCents, String reason) {
        if (amountCents <= 0) return CompletableFuture.completedFuture(true);
        if (provider == null) return CompletableFuture.completedFuture(false);
        debug("depositCents: player=" + playerUuid + ", cents=" + amountCents + ", reason=" + reason);
        return provider.deposit(playerUuid, amountCents, reason);
    }

    /**
     * Transfers money from one player to another.
     */
    public CompletableFuture<Boolean> transferCents(UUID fromUuid, UUID toUuid, long amountCents, String reason) {
        if (amountCents <= 0) return CompletableFuture.completedFuture(true);
        if (provider == null) return CompletableFuture.completedFuture(false);
        debug("transferCents: from=" + fromUuid + ", to=" + toUuid + ", cents=" + amountCents + ", reason=" + reason);
        return provider.transfer(fromUuid, toUuid, amountCents, reason);
    }

    // ==================== SYNC BRIDGE (backwards compat / simple cases) ====================

    /**
     * Gets a player's balance synchronously (double).
     * <strong>Warning:</strong> blocks the calling thread. Prefer {@link #getBalanceCents(UUID)}.
     */
    public double getBalance(UUID playerUuid) {
        try {
            long cents = getBalanceCents(playerUuid).join();
            debug("getBalance (sync): player=" + playerUuid + ", cents=" + cents + ", double=" + toDouble(cents));
            return toDouble(cents);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get balance synchronously", e);
            return 0.0;
        }
    }

    /**
     * Checks if a player has at least the given amount (double) synchronously.
     * <strong>Warning:</strong> blocks the calling thread.
     */
    public boolean has(UUID playerUuid, double amount) {
        boolean result = getBalance(playerUuid) >= amount;
        debug("has (sync): player=" + playerUuid + ", requiredAmount=" + amount + ", result=" + result);
        return result;
    }

    /**
     * Withdraws from a player synchronously (double).
     * <strong>Warning:</strong> blocks the calling thread.
     */
    public boolean withdraw(UUID playerUuid, double amount) {
        try {
            long cents = toCents(amount);
            debug("withdraw (sync): player=" + playerUuid + ", amount=" + amount + ", cents=" + cents);
            return withdrawCents(playerUuid, cents, "Market transaction").join();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to withdraw synchronously", e);
            return false;
        }
    }

    /**
     * Deposits to a player synchronously (double).
     * <strong>Warning:</strong> blocks the calling thread.
     */
    public boolean deposit(UUID playerUuid, double amount) {
        try {
            long cents = toCents(amount);
            debug("deposit (sync): player=" + playerUuid + ", amount=" + amount + ", cents=" + cents);
            return depositCents(playerUuid, cents, "Market transaction").join();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to deposit synchronously", e);
            return false;
        }
    }

    /**
     * Transfers between players synchronously (double).
     * <strong>Warning:</strong> blocks the calling thread.
     */
    public boolean transfer(UUID fromUuid, UUID toUuid, double amount) {
        try {
            long cents = toCents(amount);
            debug("transfer (sync): from=" + fromUuid + ", to=" + toUuid + ", amount=" + amount + ", cents=" + cents);
            return transferCents(fromUuid, toUuid, cents, "Market transfer").join();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to transfer synchronously", e);
            return false;
        }
    }

    // ==================== FORMATTING ====================

    /**
     * Formats a double amount as a currency string.
     */
    public String format(double amount) {
        return plugin.getMessageManager().formatCurrency(amount);
    }

    /**
     * Formats a cents value as a currency string.
     */
    public String formatCents(long cents) {
        return format(toDouble(cents));
    }

    // ==================== CONVERSION UTILITIES ====================

    /**
     * Converts a double amount (e.g. 10.50) to cents (1050).
     * Uses HALF_UP rounding.
     */
    public static long toCents(double amount) {
        return BigDecimal.valueOf(amount)
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    /**
     * Converts cents (e.g. 1050) to a double (10.50).
     */
    public static double toDouble(long cents) {
        return cents / 100.0;
    }
}
