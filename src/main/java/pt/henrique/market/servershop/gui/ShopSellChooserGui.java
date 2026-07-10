package pt.henrique.market.servershop.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import pt.henrique.market.Market;
import pt.henrique.market.config.MessageManager;
import pt.henrique.market.gui.MarketGui;
import pt.henrique.market.servershop.model.ShopItem;
import pt.henrique.market.servershop.model.ShopPriceEntry;
import pt.henrique.market.util.ItemBuilder;
import pt.henrique.market.util.SoundUtil;
import pt.henrique.market.util.TextUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * "Where do you want to sell?" chooser GUI.
 * Presented after the player selects an item and quantity for selling.
 * Options:
 *   1) Sell to Server (instant payout)
 *   2) Sell on Community Market (create listing)
 *   3) Auction / Bid it (create auction)
 *
 * Layout (27 slots / 3 rows):
 *   Row 0: Filler
 *   Row 1: [ Server ] [ Community ] [ Auction ]
 *   Row 2: [filler...] [Back]
 */
public class ShopSellChooserGui implements MarketGui {

    private final Market plugin;
    private final ShopGuiManager shopGuiManager;
    private final String itemId;
    private final int quantity;
    private Inventory inventory;

    private static final int SLOT_SERVER = 11;
    private static final int SLOT_COMMUNITY = 13;
    private static final int SLOT_AUCTION = 15;
    private static final int SLOT_BACK = 26;

    public ShopSellChooserGui(Market plugin, ShopGuiManager shopGuiManager, String itemId, int quantity) {
        this.plugin = plugin;
        this.shopGuiManager = shopGuiManager;
        this.itemId = itemId;
        this.quantity = quantity;
    }

    public void open(Player player) {
        MessageManager msg = plugin.getMessageManager();
        String lang = msg.getPlayerLanguage(player);
        String title = msg.getRaw(lang, "servershop.titles.sell-chooser");
        inventory = Bukkit.createInventory(this, 27, TextUtil.colorize(title));

        var fillerItem = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 27; i++) inventory.setItem(i, fillerItem);

        ShopItem shopItem = plugin.getShopPriceManager().getShopItem(itemId);
        ShopPriceEntry entry = plugin.getShopPriceManager().getPriceEntry(itemId);
        String itemName = shopItem != null ? shopItem.getDisplayName() : itemId;

        // Server Market option
        if (entry != null && entry.sellEnabled() && entry.sellPrice() > 0) {
            double priceEach = entry.sellPrice();
            double total = Math.round(priceEach * quantity * 100.0) / 100.0;
            List<String> lore = new ArrayList<>(msg.getList(lang, "servershop.lore.sell-chooser-server"));
            lore.replaceAll(l -> l.replace("{quantity}", String.valueOf(quantity))
                    .replace("{price_each}", msg.formatCurrency(priceEach))
                    .replace("{total}", msg.formatCurrency(total)));
            inventory.setItem(SLOT_SERVER, new ItemBuilder(Material.EMERALD_BLOCK)
                    .name(msg.getRaw(lang, "servershop.buttons.sell-to-server"))
                    .lore(lore).build());
        } else {
            inventory.setItem(SLOT_SERVER, new ItemBuilder(Material.GRAY_CONCRETE)
                    .name("&7&lServer Shop")
                    .lore("&cThis item cannot be sold", "&cto the server.").build());
        }

        // Community Market option
        if (player.hasPermission("market.sell")) {
            List<String> lore = new ArrayList<>(msg.getList(lang, "servershop.lore.sell-chooser-community"));
            lore.replaceAll(l -> l.replace("{quantity}", String.valueOf(quantity)));
            inventory.setItem(SLOT_COMMUNITY, new ItemBuilder(Material.CHEST)
                    .name(msg.getRaw(lang, "servershop.buttons.sell-to-community"))
                    .lore(lore).build());
        } else {
            inventory.setItem(SLOT_COMMUNITY, new ItemBuilder(Material.GRAY_CONCRETE)
                    .name("&7&lCommunity Market")
                    .lore("&cYou don't have permission.").build());
        }

        // Auction option
        if (player.hasPermission("market.auction")) {
            List<String> lore = new ArrayList<>(msg.getList(lang, "servershop.lore.sell-chooser-auction"));
            lore.replaceAll(l -> l.replace("{quantity}", String.valueOf(quantity)));
            inventory.setItem(SLOT_AUCTION, new ItemBuilder(Material.GOLD_INGOT)
                    .name(msg.getRaw(lang, "servershop.buttons.sell-to-auction"))
                    .lore(lore).build());
        } else {
            inventory.setItem(SLOT_AUCTION, new ItemBuilder(Material.GRAY_CONCRETE)
                    .name("&7&lAuction")
                    .lore("&cYou don't have permission.").build());
        }

        // Back button (BARRIER at bottom-right)
        inventory.setItem(SLOT_BACK, new ItemBuilder(Material.BARRIER)
                .name(msg.getRaw(lang, "servershop.buttons.back")).build());

        plugin.getGuiManager().registerGui(player.getUniqueId(), this);
        shopGuiManager.registerGui(player.getUniqueId(), this);
        player.openInventory(inventory);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventory) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();

        switch (slot) {
            case SLOT_SERVER -> {
                ShopPriceEntry entry = plugin.getShopPriceManager().getPriceEntry(itemId);
                if (entry != null && entry.sellEnabled() && entry.sellPrice() > 0) {
                    SoundUtil.playClickSound(player);
                    shopGuiManager.openSellOnlyDetail(player, itemId);
                } else {
                    SoundUtil.playErrorSound(player);
                }
            }
            case SLOT_COMMUNITY -> {
                if (player.hasPermission("market.sell")) {
                    SoundUtil.playClickSound(player);
                    // Open the create listing flow - the community market listing creation
                    plugin.getGuiManager().openCreateListing(player);
                } else {
                    SoundUtil.playErrorSound(player);
                }
            }
            case SLOT_AUCTION -> {
                if (player.hasPermission("market.auction")) {
                    SoundUtil.playClickSound(player);
                    plugin.getGuiManager().openCreateAuction(player);
                } else {
                    SoundUtil.playErrorSound(player);
                }
            }
            case SLOT_BACK -> {
                SoundUtil.playClickSound(player);
                shopGuiManager.openSellInventory(player);
            }
        }
    }

    @Override public GuiType getType() { return GuiType.SHOP_SELL_CHOOSER; }
    @Override public @NotNull Inventory getInventory() { return inventory; }
}

