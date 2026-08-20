package pt.henrique.market.servershop.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import pt.henrique.market.Market;
import pt.henrique.market.servershop.MultiSellSession;
import pt.henrique.market.servershop.ShulkerUtil;
import pt.henrique.market.servershop.model.SellVariant;
import pt.henrique.market.servershop.model.ShopCategory;
import pt.henrique.market.servershop.service.ShopTransactionService;
import pt.henrique.market.util.SoundUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntPredicate;

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

    /**
     * Sells whatever the player is holding: a packed shulker opens its own chooser,
     * anything else opens the sell screen for that exact item.
     *
     * @param origin where the Back button should return to
     */
    public void startSellHand(Player player, SellVariantDetailGui.Origin origin) {
        var msg = plugin.getMessageManager();
        var held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            player.sendMessage(msg.getPrefixedP(player, "servershop.messages.sell-hand-empty"));
            SoundUtil.playErrorSound(player);
            return;
        }
        if (plugin.getBuildersDreamHook() != null && plugin.getBuildersDreamHook().isDreamItem(held)) {
            player.sendMessage(msg.getPrefixedP(player, "messages.dream-item-blocked"));
            SoundUtil.playErrorSound(player);
            return;
        }

        int heldSlot = player.getInventory().getHeldItemSlot();
        if (plugin.getConfig().getBoolean("sell-inventory.shulker-sell.enabled", true)
                && ShulkerUtil.isShulkerBox(held) && ShulkerUtil.hasContents(held)) {
            SoundUtil.playClickSound(player);
            openShulkerSellChooser(player, heldSlot);
            return;
        }

        SellVariant variant = plugin.getSellVariantScanner().variantOf(held, heldSlot);
        if (variant == null) {
            player.sendMessage(msg.getPrefixedP(player, "servershop.messages.sell-hand-not-sellable"));
            SoundUtil.playErrorSound(player);
            return;
        }
        SoundUtil.playClickSound(player);
        openSellVariantDetail(player, variant, ShopTransactionService.onlySlot(heldSlot), origin);
    }

    public void openSearchResults(Player player, String query, int page) {
        new ShopSearchResultsGui(plugin, this, query, page).open(player);
    }

    public void openSellInventory(Player player) {
        new ShopSellInventoryGui(plugin, this).open(player);
    }

    /** Sell grid on a given page, optionally narrowed to one material. */
    public void openSellInventory(Player player, int page, Material filter) {
        new ShopSellInventoryGui(plugin, this, page, filter).open(player);
    }

    /** Sell screen for one specific item, drawing from the given inventory slots. */
    public void openSellVariantDetail(Player player, SellVariant variant,
                                      IntPredicate slots, SellVariantDetailGui.Origin origin) {
        new SellVariantDetailGui(plugin, this, variant, slots, origin).open(player);
    }

    /** The three ways to sell — inventory, held item, or a nearby container. */
    public void openSellModeChooser(Player player) {
        new SellModeChooserGui(plugin, this).open(player);
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
