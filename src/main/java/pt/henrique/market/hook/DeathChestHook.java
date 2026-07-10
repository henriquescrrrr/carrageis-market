package pt.henrique.market.hook;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Reflection-based hook for the DeathChest plugin.
 * Used by the Sell Chest feature to prevent players from selling items
 * out of another player's death chest.
 * <p>
 * If DeathChest is not installed, {@link #isDeathChest(Location)} always returns false.
 */
public class DeathChestHook {

    private Object apiInstance;
    private Method isDeathChestMethod;
    private boolean available;
    private Logger logger;

    public void init(Logger logger) {
        this.logger = logger;
        available = false;

        if (Bukkit.getPluginManager().getPlugin("DeathChest") == null) {
            return;
        }

        // Strategy 1: Try the DeathChestAPI service with isDeathChest(Location)
        try {
            Class<?> apiClass = Class.forName("pt.henrique.deathchest.api.DeathChestAPI");
            var reg = Bukkit.getServicesManager().getRegistration(apiClass);
            if (reg != null) {
                Object provider = reg.getProvider();
                try {
                    Method m = provider.getClass().getMethod("isDeathChest", Location.class);
                    apiInstance = provider;
                    isDeathChestMethod = m;
                    available = true;
                    logger.info("DeathChest hook enabled (API) — sell chest will block death chests.");
                    return;
                } catch (NoSuchMethodException ignored) {
                    // API exists but doesn't have isDeathChest — try fallback
                }
            }
        } catch (Exception ignored) {
            // API class not found — try fallback
        }

        // Strategy 2: Get plugin instance → getDeathChestManager() → isChestAt / getChestAt / getChestByLocation
        try {
            Object plugin = Bukkit.getPluginManager().getPlugin("DeathChest");
            if (plugin == null) return;

            Method getManager = plugin.getClass().getMethod("getDeathChestManager");
            Object manager = getManager.invoke(plugin);
            if (manager == null) return;

            for (String methodName : new String[]{"isChestAt", "getChestAt", "getChestByLocation"}) {
                try {
                    Method m = manager.getClass().getMethod(methodName, Location.class);
                    apiInstance = manager;
                    isDeathChestMethod = m;
                    available = true;
                    logger.info("DeathChest hook enabled (manager." + methodName + ") — sell chest will block death chests.");
                    return;
                } catch (NoSuchMethodException ignored) {
                    // try next method name
                }
            }

            logger.info("DeathChest detected but no compatible API method found. Sell chest will NOT block death chests.");
        } catch (Exception e) {
            logger.info("DeathChest hook initialization failed: " + e.getMessage());
        }
    }

    /**
     * Checks if the block at the given location is a Death Chest.
     * Returns {@code false} if DeathChest is not installed or the API is unavailable.
     * <p>
     * When DeathChest IS installed but the API call fails, this <b>fails closed</b>
     * (returns {@code true} = treat as a death chest, blocking the sell) rather than
     * open: selling from a death chest can steal another player's dropped items, so a
     * broken hook must not become a theft vector.
     */
    public boolean isDeathChest(Location location) {
        if (!available || location == null) return false;
        try {
            Object result = isDeathChestMethod.invoke(apiInstance, location);
            // Handle both boolean and object return types
            if (result instanceof Boolean b) {
                return b;
            }
            // If the method returns an object (e.g., getChestAt returns DeathChestData),
            // non-null means it IS a death chest
            return result != null;
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("DeathChest check failed; treating as death chest (fail-closed): " + e.getMessage());
            }
            return true;
        }
    }

    public boolean isAvailable() {
        return available;
    }
}

