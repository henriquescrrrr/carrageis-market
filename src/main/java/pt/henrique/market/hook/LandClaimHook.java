package pt.henrique.market.hook;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Reflection-based hook for the LandClaim plugin.
 * Used by the Sell Chest feature to verify the player has permission
 * to access a container in a claimed area.
 * <p>
 * If LandClaim is not installed, {@link #canBuild(Player, Block)} always returns true.
 */
public class LandClaimHook {

    private Object apiInstance;
    private Method canBuildMethod;
    private boolean available;
    private Logger logger;

    public void init(Logger logger) {
        this.logger = logger;
        try {
            if (Bukkit.getPluginManager().getPlugin("LandClaim") == null) {
                available = false;
                return;
            }
            Class<?> apiClass = Class.forName("pt.henrique.landclaim.api.LandClaimApi");
            var reg = Bukkit.getServicesManager().getRegistration(apiClass);
            if (reg != null && reg.getProvider() != null) {
                apiInstance = reg.getProvider();
                canBuildMethod = apiInstance.getClass().getMethod("canBuild", Player.class, Block.class);
                available = true;
                logger.info("LandClaim hook enabled — chest sell will verify claim permissions.");
            }
        } catch (Exception e) {
            available = false;
        }
    }

    /**
     * Checks if the player can build (interact with containers) at the given block.
     * Returns {@code true} if LandClaim is not installed or if the player has permission.
     * <p>
     * When LandClaim IS installed but the API call fails, this <b>fails closed</b>
     * (returns {@code false} = deny) rather than open: this is an authorization
     * check gating whether a player may sell another player's chest contents, so a
     * broken hook must not become a theft vector.
     */
    public boolean canBuild(Player player, Block block) {
        if (!available) return true; // no LandClaim = no restrictions
        try {
            return (boolean) canBuildMethod.invoke(apiInstance, player, block);
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("LandClaim canBuild check failed; denying (fail-closed): " + e.getMessage());
            }
            return false;
        }
    }

    public boolean isAvailable() {
        return available;
    }
}

