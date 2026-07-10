package pt.henrique.market.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import pt.henrique.market.Market;
import pt.henrique.market.util.ItemBuilder;
import pt.henrique.market.util.TextUtil;

import java.util.List;

/**
 * Help GUI showing how to use the marketplace.
 */
public class HelpGui implements MarketGui {

    private final Market plugin;
    private final GuiManager guiManager;
    private Inventory inventory;

    private static final int BACK_SLOT = 53;

    public HelpGui(Market plugin, GuiManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    public void open(Player player) {
        var msgManager = plugin.getMessageManager();

        String title = TextUtil.colorizeToString(msgManager.getRaw("gui-titles.help"));
        inventory = Bukkit.createInventory(this, 54, title);

        buildGui();
        player.openInventory(inventory);
        guiManager.registerGui(player.getUniqueId(), this);
    }

    private void buildGui() {
        var msgManager = plugin.getMessageManager();

        // Fill with glass
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, filler);
        }

        // Help content
        List<String> helpContent = msgManager.getList("help.content");

        // Main help book
        inventory.setItem(4, new ItemBuilder(Material.WRITTEN_BOOK)
                .name(msgManager.getRaw("help.title"))
                .build());

        // Feature explanations
        inventory.setItem(19, new ItemBuilder(Material.CHEST)
                .name("&aBrowse Market")
                .lore(
                        "&7View all fixed-price listings",
                        "&7from other players.",
                        "",
                        "&7Click on items to purchase them."
                )
                .build());

        inventory.setItem(20, new ItemBuilder(Material.GOLD_INGOT)
                .name("&6Browse Auctions")
                .lore(
                        "&7View all active auctions.",
                        "",
                        "&eLeft-click &7to place a bid",
                        "&eRight-click &7to buyout (if available)"
                )
                .build());

        inventory.setItem(21, new ItemBuilder(Material.WRITABLE_BOOK)
                .name("&eCreate Listing")
                .lore(
                        "&7Sell items at a fixed price.",
                        "",
                        "&71. Place your item in the slot",
                        "&72. Set the price",
                        "&73. Choose duration",
                        "&74. Click confirm"
                )
                .build());

        inventory.setItem(22, new ItemBuilder(Material.GOLDEN_HELMET)
                .name("&eCreate Auction")
                .lore(
                        "&7Auction items to the highest bidder.",
                        "",
                        "&71. Place your item in the slot",
                        "&72. Set starting price",
                        "&73. Optionally set buyout",
                        "&74. Choose duration",
                        "&75. Click confirm"
                )
                .build());

        inventory.setItem(23, new ItemBuilder(Material.BOOK)
                .name("&bMy Listings")
                .lore(
                        "&7View your active listings.",
                        "",
                        "&7Click on a listing to cancel it.",
                        "&7Cancelled items go to claim storage."
                )
                .build());

        inventory.setItem(24, new ItemBuilder(Material.CLOCK)
                .name("&bMy Auctions")
                .lore(
                        "&7View your active auctions.",
                        "",
                        "&7You can only cancel auctions",
                        "&7that have no bids yet."
                )
                .build());

        inventory.setItem(25, new ItemBuilder(Material.ENDER_CHEST)
                .name("&dClaim Items")
                .lore(
                        "&7Collect items waiting for you:",
                        "",
                        "&7- Expired listings",
                        "&7- Cancelled listings",
                        "&7- Won auctions",
                        "&7- Auction refunds"
                )
                .build());

        inventory.setItem(31, new ItemBuilder(Material.EMERALD)
                .name("&aEarnings")
                .lore(
                        "&7Withdraw money from sales.",
                        "",
                        "&7When you sell something, the money",
                        "&7goes to pending earnings first.",
                        "&7Withdraw it here."
                )
                .build());

        // Tax info
        inventory.setItem(40, new ItemBuilder(Material.GOLD_NUGGET)
                .name("&6Tax Information")
                .lore(
                        "&7Market Tax: &f" + plugin.getConfigManager().getMarketTax() + "%",
                        "&7Auction Tax: &f" + plugin.getConfigManager().getAuctionTax() + "%",
                        "",
                        "&7Taxes are deducted from seller earnings."
                )
                .build());

        // Back button
        inventory.setItem(BACK_SLOT, new ItemBuilder(Material.BARRIER)
                .name(msgManager.getButton("back"))
                .build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        if (slot == BACK_SLOT) {
            guiManager.openMainMenu(player);
        }
    }

    @Override
    public GuiType getType() {
        return GuiType.HELP;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

