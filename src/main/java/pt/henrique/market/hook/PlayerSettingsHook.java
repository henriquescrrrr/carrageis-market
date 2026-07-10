package pt.henrique.market.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Reflection-based hook for the PlayerSettings plugin.
 * Uses the ServicesManager to look up {@code pt.henrique.playersettings.api.PlayerSettingsApi}
 * at runtime — no compile-time dependency required.
 */
public class PlayerSettingsHook {

    private Object settingsProvider;
    private Method getLanguageMethod;
    private boolean available;

    /**
     * Attempts to initialize the hook. Safe to call even if PlayerSettings
     * is not installed — {@link #isAvailable()} will simply return false.
     */
    public void init(Logger logger) {
        try {
            if (Bukkit.getPluginManager().getPlugin("PlayerSettings") == null) {
                available = false;
                return;
            }
            Class<?> apiClass = Class.forName("pt.henrique.playersettings.api.PlayerSettingsApi");
            var reg = Bukkit.getServicesManager().getRegistration(apiClass);
            if (reg != null && reg.getProvider() != null) {
                settingsProvider = reg.getProvider();
                getLanguageMethod = settingsProvider.getClass().getMethod("getLanguage", UUID.class);
                available = true;
                logger.info("[PlayerSettings] Hook enabled — per-player language active.");
            } else {
                available = false;
                logger.info("[PlayerSettings] Plugin found but API not registered.");
            }
        } catch (Exception e) {
            available = false;
            logger.info("[PlayerSettings] Hook not available: " + e.getMessage());
        }
    }

    /**
     * Returns the language code for the given player (e.g. "en_US", "fr_FR"),
     * or {@code null} if PlayerSettings is unavailable or has no setting for this player.
     */
    public String getLanguage(Player player) {
        if (!available) return null;
        try {
            return (String) getLanguageMethod.invoke(settingsProvider, player.getUniqueId());
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isAvailable() {
        return available;
    }
}

