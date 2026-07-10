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
 * Community Market sub-menu GUI.
 * Provides access to community/player-to-player features:
 * browse listings, create listing, my listings,
 * and a shortcut to the Auctions area.
 *
 * Note: Claim Items and Earnings have been moved to the Main Menu.
 *
 * Layout (54 slots / 6 rows):
 *   Row 0: Filler
 *   Row 1: [ Browse Listings ] [  Create Listing  ] [  My Listings  ]
 *   Row 2: Filler
 *   Row 3: [          ] [  Auctions ➜  ] [          ]
 *   Row 4: Filler
 *   Row 5: [filler..] [ Back ] [filler..]
 *
 * Slot map:
 *   Browse Listings = slot 11
 *   Create Listing  = slot 13
 *   My Listings     = slot 15
 *   Auctions entry  = slot 31
 *   Back button     = slot 49 (bottom center)
 */
public class CommunityMenuGui implements MarketGui {

    private final Market plugin;
    private final GuiManager guiManager;
    private Inventory inventory;
    private Player player;

    // ── Slot constants ──────────────────────────────────────────────
    /** Browse player listings */
    private static final int SLOT_BROWSE_LISTINGS = 11;
    /** Create a new listing */
    private static final int SLOT_CREATE_LISTING = 13;
    /** View/manage own listings */
    private static final int SLOT_MY_LISTINGS = 15;
    /** Shortcut to Auctions area */
    private static final int SLOT_AUCTIONS = 31;
    /** Back to Main Menu */
    private static final int SLOT_BACK = 53;

    public CommunityMenuGui(Market plugin, GuiManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    /**
     * Opens the Community Market menu for a player.
     */
    public void open(Player player) {
        this.player = player;

        String title = TextUtil.colorizeToString(
                plugin.getMessageManager().getRaw("gui-titles.community-menu"));
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
        var listingCountFuture = plugin.getListingService().countPlayerListings(player.getUniqueId());

        listingCountFuture.thenAccept(listingCount -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                buildMenu(listingCount);
                player.openInventory(inventory);
                guiManager.registerGui(player.getUniqueId(), this);
                SoundUtil.playSound(player, plugin.getConfigManager().getClickSound());
            });
        });
    }

    private void buildMenu(int listingCount) {
        var msgManager = plugin.getMessageManager();
        var configManager = plugin.getConfigManager();
        String lang = msgManager.getPlayerLanguage(player);

        // ── Row 1 ───────────────────────────────────────────────────

        // Browse Listings
        inventory.setItem(SLOT_BROWSE_LISTINGS, new ItemBuilder(Material.CHEST)
                .name(msgManager.getButton(lang, "browse-market"))
                .lore(msgManager.getLore(lang, "browse-market"))
                .build());

        // Create Listing
        inventory.setItem(SLOT_CREATE_LISTING, new ItemBuilder(Material.WRITABLE_BOOK)
                .name(msgManager.getButton(lang, "create-listing"))
                .lore(msgManager.getLore(lang, "create-listing", Map.of(
                        "tax", String.valueOf(configManager.getMarketTax())
                )))
                .build());

        // My Listings
        inventory.setItem(SLOT_MY_LISTINGS, new ItemBuilder(Material.BOOK)
                .name(msgManager.getButton(lang, "my-listings"))
                .lore(msgManager.getLore(lang, "my-listings", Map.of(
                        "count", String.valueOf(listingCount),
                        "max", String.valueOf(configManager.getMaxListingsPerPlayer())
                )))
                .build());

        // ── Row 3 ───────────────────────────────────────────────────

        // Auctions shortcut (centered)
        inventory.setItem(SLOT_AUCTIONS, new ItemBuilder(Material.GOLD_INGOT)
                .name(msgManager.getButton(lang, "auctions"))
                .lore(msgManager.getLore(lang, "auctions"))
                .build());

        // ── Bottom bar ──────────────────────────────────────────────

        // Back to Main Menu
        inventory.setItem(SLOT_BACK, new ItemBuilder(Material.BARRIER)
                .name(msgManager.getButton(lang, "back"))
                .build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        if (event.getCurrentItem() == null) return;

        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        switch (slot) {
            case SLOT_BROWSE_LISTINGS -> {
                if (player.hasPermission("market.community")) {
                    guiManager.openBrowseMarket(player, 0, NavigationOrigin.COMMUNITY_MENU);
                } else {
                    noPermission(player);
                }
            }
            case SLOT_CREATE_LISTING -> {
                if (player.hasPermission("market.sell")) {
                    guiManager.openCreateListing(player);
                } else {
                    noPermission(player);
                }
            }
            case SLOT_MY_LISTINGS -> {
                if (player.hasPermission("market.community")) {
                    guiManager.openMyListings(player, NavigationOrigin.COMMUNITY_MENU);
                } else {
                    noPermission(player);
                }
            }
            case SLOT_AUCTIONS -> {
                if (player.hasPermission("market.auctions")) {
                    guiManager.openAuctionsMenu(player, NavigationOrigin.COMMUNITY_MENU);
                } else {
                    noPermission(player);
                }
            }
            case SLOT_BACK -> guiManager.openMainMenu(player);
        }
    }

    private void noPermission(Player player) {
        player.sendMessage(plugin.getMessageManager().getPrefixedP(player, "messages.no-permission"));
        SoundUtil.playSound(player, plugin.getConfigManager().getErrorSound());
    }

    @Override
    public GuiType getType() {
        return GuiType.COMMUNITY_MENU;
    }

    @Override
    public @org.jetbrains.annotations.NotNull Inventory getInventory() {
        return inventory;
    }
}

