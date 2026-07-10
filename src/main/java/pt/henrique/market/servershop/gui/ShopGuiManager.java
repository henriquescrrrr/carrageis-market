package pt.henrique.market.servershop.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pt.henrique.market.Market;
import pt.henrique.market.servershop.MultiSellSession;
import pt.henrique.market.servershop.model.ShopCategory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Central manager for all server shop GUI instances.
 * Tracks open GUIs per player and provides methods to open each shop screen.
 * Works alongside the existing GuiManager for Market GUIs.
 */
public class ShopGuiManager {

    private final Market plugin;
    private final Map<UUID, Object> openShopGuis = new HashMap<>();
    /** Per-player multi-select sell sessions (selection + chosen percentage). */
    private final Map<UUID, MultiSellSession> multiSellSessions = new HashMap<>();

    public ShopGuiManager(Market plugin) {
        this.plugin = plugin;
    }

    public void openMainMenu(Player player) {
        new ShopMainMenuGui(plugin, this).open(player);
    }

    public void openCategory(Player player, ShopCategory category, int page) {
        new ShopCategoryGui(plugin, this, category, page).open(player);
    }

    public void openItemDetail(Player player, String itemId) {
        new ShopItemDetailGui(plugin, this, itemId).open(player);
    }

    public void openSellOnlyDetail(Player player, String itemId) {
        new ShopItemDetailGui(plugin, this, itemId, true).open(player);
    }

    public void openSellChooser(Player player, String itemId, int quantity) {
        new ShopSellChooserGui(plugin, this, itemId, quantity).open(player);
    }

    public void openSearchResults(Player player, String query, int page) {
        new ShopSearchResultsGui(plugin, this, query, page).open(player);
    }

    public void openSellInventory(Player player) {
        new ShopSellInventoryGui(plugin, this).open(player);
    }

    public void openSellAllConfirm(Player player) {
        new SellAllConfirmGui(plugin, this).open(player);
    }

    public void openMultiSellQuantity(Player player) {
        new ShopMultiSellQuantityGui(plugin, this).open(player);
    }

    public void openMultiSellConfirm(Player player) {
        new MultiSellConfirmGui(plugin, this).open(player);
    }

    public void openShulkerSellChooser(Player player, int playerSlot) {
        new ShulkerSellChooserGui(plugin, this, playerSlot).open(player);
    }

    /**
     * Returns the player's multi-select sell session, creating one (seeded with
     * the configured default percentage) on first access.
     */
    public MultiSellSession getMultiSellSession(UUID playerUuid) {
        return multiSellSessions.computeIfAbsent(playerUuid, k -> {
            MultiSellSession session = new MultiSellSession();
            session.setPercent(plugin.getConfig().getInt("sell-inventory.multi-select.default-percent", 100));
            return session;
        });
    }

    /** Discards the player's multi-select session entirely. */
    public void clearMultiSellSession(UUID playerUuid) {
        multiSellSessions.remove(playerUuid);
    }

    public void registerGui(UUID playerUuid, Object gui) {
        openShopGuis.put(playerUuid, gui);
    }

    public void unregisterGui(UUID playerUuid) {
        openShopGuis.remove(playerUuid);
    }

    public void closeAllGuis() {
        for (UUID uuid : new HashMap<>(openShopGuis).keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.closeInventory();
            }
        }
        openShopGuis.clear();
        multiSellSessions.clear();
    }
}
