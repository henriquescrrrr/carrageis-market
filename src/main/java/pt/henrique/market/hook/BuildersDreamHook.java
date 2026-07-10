package pt.henrique.market.hook;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Reflection-based hook for the BuildersDream plugin.
 * Detects "dream items" (creative-mode items) so they can be rejected
 * from all sell/listing/auction flows.
 * <p>
 * If BuildersDream is not installed, {@link #isDreamItem(ItemStack)} always returns false.
 */
public class BuildersDreamHook {

    private Object apiInstance;
    private Method isDreamItemMethod;
    private boolean available;
    private Logger logger;

    public void init(Logger logger) {
        this.logger = logger;
        try {
            if (Bukkit.getPluginManager().getPlugin("BuildersDream") == null) {
                available = false;
                return;
            }
            Class<?> apiClass = Class.forName("pt.henrique.buildersdream.api.BuildersDreamApi");
            var reg = Bukkit.getServicesManager().getRegistration(apiClass);
            if (reg != null && reg.getProvider() != null) {
                apiInstance = reg.getProvider();
                isDreamItemMethod = apiInstance.getClass().getMethod("isDreamItem", ItemStack.class);
                available = true;
                logger.info("BuildersDream hook enabled — dream items will be rejected from market.");
            }
        } catch (Exception e) {
            available = false;
        }
    }

    /**
     * Returns {@code true} if the given item is a BuildersDream creative item.
     * Always returns {@code false} if BuildersDream is not installed.
     * <p>
     * When BuildersDream IS installed but the API call fails, this <b>fails closed</b>
     * (returns {@code true} = treat as a dream item, rejecting it from the market)
     * rather than open: dream items are free creative-mode items, so a broken hook
     * must not become a money printer.
     */
    public boolean isDreamItem(ItemStack item) {
        if (!available || item == null) return false;
        try {
            return (boolean) isDreamItemMethod.invoke(apiInstance, item);
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("BuildersDream isDreamItem check failed; treating as dream item (fail-closed): " + e.getMessage());
            }
            return true;
        }
    }

    public boolean isAvailable() {
        return available;
    }
}

