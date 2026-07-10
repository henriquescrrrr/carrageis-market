package pt.henrique.market.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import pt.henrique.market.Market;
import pt.henrique.market.util.ItemBuilder;
import pt.henrique.market.util.SoundUtil;
import pt.henrique.market.util.TextUtil;

import java.util.Map;

/**
 * Auctions sub-menu GUI.
 * Provides access to auction-related features:
 * browse auctions, create auction, my auctions, and back.
 *
 * Note: Claim Items has been moved to the Main Menu.
 *
 * The "Back" button is origin-aware:
 *   - If opened from Main Menu   → back returns to Main Menu
 *   - If opened from Community   → back returns to Community Menu
 *
 * Layout (54 slots / 6 rows):
 *   Row 0: Filler
 *   Row 1: [ Browse Auctions ] [ Create Auction ] [ My Auctions ]
 *   Row 2: Filler
 *   Row 3: Filler
 *   Row 4: Filler
 *   Row 5: [filler..] [ Back ] [filler..]
 *
 * Slot map:
 *   Browse Auctions = slot 11
 *   Create Auction  = slot 13
 *   My Auctions     = slot 15
 *   Back button     = slot 49 (bottom center)
 */
public class AuctionsMenuGui implements MarketGui {

    private final Market plugin;
    private final GuiManager guiManager;
    private Inventory inventory;
    private Player player;

    /** Where the player navigated from — determines Back behaviour */
    private NavigationOrigin origin;

    // ── Slot constants ──────────────────────────────────────────────
    /** Browse active auctions */
    private static final int SLOT_BROWSE_AUCTIONS = 11;
    /** Create a new auction */
    private static final int SLOT_CREATE_AUCTION = 13;
    /** View/manage own auctions */
    private static final int SLOT_MY_AUCTIONS = 15;
    /** Back to parent menu (origin-aware) */
    private static final int SLOT_BACK = 53;

    public AuctionsMenuGui(Market plugin, GuiManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    /**
     * Opens the Auctions menu for a player.
     *
     * @param player the player
     * @param origin where the player came from
     */
    public void open(Player player, NavigationOrigin origin) {
        this.player = player;
        this.origin = origin;

        String title = TextUtil.colorizeToString(
                plugin.getMessageManager().getRaw("gui-titles.auctions-menu"));
        inventory = Bukkit.createInventory(this, 54, title);

        // Fill background
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .name(" ")
                .build();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, filler);
        }

        // Build items async (for counts)
        buildMenuAsync();
    }

    private void buildMenuAsync() {
        var auctionCountFuture = plugin.getAuctionService().countPlayerAuctions(player.getUniqueId());

        auctionCountFuture.thenAccept(auctionCount -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                buildMenu(auctionCount);
                player.openInventory(inventory);
                guiManager.registerGui(player.getUniqueId(), this);
                SoundUtil.playSound(player, plugin.getConfigManager().getClickSound());
            });
        });
    }

    private void buildMenu(int auctionCount) {
        var msgManager = plugin.getMessageManager();
        var configManager = plugin.getConfigManager();

        // ── Row 1 ───────────────────────────────────────────────────

        // Browse Auctions
        inventory.setItem(SLOT_BROWSE_AUCTIONS, new ItemBuilder(Material.GOLD_INGOT)
                .name(msgManager.getButton("browse-auctions"))
                .lore(msgManager.getLore("browse-auctions"))
                .build());

        // Create Auction
        inventory.setItem(SLOT_CREATE_AUCTION, new ItemBuilder(Material.GOLDEN_HELMET)
                .name(msgManager.getButton("create-auction"))
                .lore(msgManager.getLore("create-auction", Map.of(
                        "tax", String.valueOf(configManager.getAuctionTax())
                )))
                .build());

        // My Auctions
        inventory.setItem(SLOT_MY_AUCTIONS, new ItemBuilder(Material.CLOCK)
                .name(msgManager.getButton("my-auctions"))
                .lore(msgManager.getLore("my-auctions", Map.of(
                        "count", String.valueOf(auctionCount),
                        "max", String.valueOf(configManager.getMaxAuctionsPerPlayer())
                )))
                .build());

        // ── Bottom bar ──────────────────────────────────────────────

        // Back (origin-aware)
        inventory.setItem(SLOT_BACK, new ItemBuilder(Material.BARRIER)
                .name(msgManager.getButton("back"))
                .build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        if (event.getCurrentItem() == null) return;

        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        switch (slot) {
            case SLOT_BROWSE_AUCTIONS -> {
                if (player.hasPermission("market.auctions")) {
                    guiManager.openBrowseAuctions(player, 0, NavigationOrigin.AUCTIONS_MENU);
                } else {
                    noPermission(player);
                }
            }
            case SLOT_CREATE_AUCTION -> {
                if (player.hasPermission("market.auction")) {
                    guiManager.openCreateAuction(player);
                } else {
                    noPermission(player);
                }
            }
            case SLOT_MY_AUCTIONS -> {
                if (player.hasPermission("market.auctions")) {
                    guiManager.openMyAuctions(player, NavigationOrigin.AUCTIONS_MENU);
                } else {
                    noPermission(player);
                }
            }
            case SLOT_BACK -> navigateBack(player);
        }
    }

    /**
     * Navigate back based on origin.
     */
    private void navigateBack(Player player) {
        switch (origin) {
            case COMMUNITY_MENU -> guiManager.openCommunityMenu(player);
            default -> guiManager.openMainMenu(player); // MAIN_MENU and any other
        }
    }

    private void noPermission(Player player) {
        player.sendMessage(plugin.getMessageManager().getPrefixedP(player, "messages.no-permission"));
        SoundUtil.playSound(player, plugin.getConfigManager().getErrorSound());
    }

    @Override
    public GuiType getType() {
        return GuiType.AUCTIONS_MENU;
    }

    @Override
    public @org.jetbrains.annotations.NotNull Inventory getInventory() {
        return inventory;
    }
}

