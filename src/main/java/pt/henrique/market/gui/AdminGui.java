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

/**
 * Admin panel GUI for moderating the marketplace.
 * Provides access to view all listings/auctions, remove items, and reload config.
 */
public class AdminGui implements MarketGui {

    private final Market plugin;
    private final GuiManager guiManager;
    private Inventory inventory;

    private static final int VIEW_LISTINGS_SLOT = 20;
    private static final int VIEW_AUCTIONS_SLOT = 24;
    private static final int RELOAD_SLOT = 40;
    private static final int BACK_SLOT = 53;

    public AdminGui(Market plugin, GuiManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    public void open(Player player) {
        if (!player.hasPermission("market.admin")) {
            player.sendMessage(plugin.getMessageManager().getPrefixedP(player, "messages.no-permission"));
            return;
        }

        var msgManager = plugin.getMessageManager();
        String title = TextUtil.colorizeToString(msgManager.getRaw("gui-titles.admin-panel"));
        inventory = Bukkit.createInventory(this, 54, title);

        buildGui();
        player.openInventory(inventory);
        guiManager.registerGui(player.getUniqueId(), this);
    }

    private void buildGui() {
        var msgManager = plugin.getMessageManager();

        // Fill with glass
        ItemStack filler = new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, filler);
        }

        // Admin panel header
        inventory.setItem(4, new ItemBuilder(Material.COMMAND_BLOCK)
                .name("&c&lAdmin Panel")
                .lore(
                        "&7Manage the marketplace.",
                        "&7Remove listings, cancel auctions,",
                        "&7and reload configuration."
                )
                .build());

        // View all listings
        inventory.setItem(VIEW_LISTINGS_SLOT, new ItemBuilder(Material.CHEST)
                .name(msgManager.getButton("admin-view-listings"))
                .lore(
                        "&7View all active listings",
                        "&7from all players.",
                        "",
                        "&cClick on items to remove them."
                )
                .build());

        // View all auctions
        inventory.setItem(VIEW_AUCTIONS_SLOT, new ItemBuilder(Material.GOLD_BLOCK)
                .name(msgManager.getButton("admin-view-auctions"))
                .lore(
                        "&7View all active auctions",
                        "&7from all players.",
                        "",
                        "&cClick on items to force-end them."
                )
                .build());

        // Reload config
        inventory.setItem(RELOAD_SLOT, new ItemBuilder(Material.REPEATING_COMMAND_BLOCK)
                .name(msgManager.getButton("admin-reload"))
                .lore(
                        "&7Reload plugin configuration",
                        "&7and language files."
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

        switch (slot) {
            case VIEW_LISTINGS_SLOT -> openAdminListings(player);
            case VIEW_AUCTIONS_SLOT -> openAdminAuctions(player);
            case RELOAD_SLOT -> {
                plugin.reload();
                player.sendMessage(plugin.getMessageManager().getPrefixedP(player, "messages.admin-reload"));
                playSound(player, plugin.getConfigManager().getSuccessSound());
            }
            case BACK_SLOT -> guiManager.openMainMenu(player);
        }
    }

    private void openAdminListings(Player player) {
        // Opens browse market but with admin remove capability
        new AdminListingsGui(plugin, guiManager).open(player, 0);
    }

    private void openAdminAuctions(Player player) {
        new AdminAuctionsGui(plugin, guiManager).open(player, 0);
    }

    private void playSound(Player player, String soundName) {
        pt.henrique.market.util.SoundUtil.playSound(player, soundName);
    }

    @Override
    public GuiType getType() {
        return GuiType.ADMIN;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    // ==================== Inner Admin GUIs ====================

    /**
     * Admin view of all listings with remove capability
     */
    private static class AdminListingsGui implements MarketGui {
        private final Market plugin;
        private final GuiManager guiManager;
        private Inventory inventory;
        private Player player;
        private int page;
        private java.util.List<pt.henrique.market.model.Listing> listings;

        public AdminListingsGui(Market plugin, GuiManager guiManager) {
            this.plugin = plugin;
            this.guiManager = guiManager;
        }

        public void open(Player player, int page) {
            this.player = player;
            this.page = page;

            plugin.getListingService().getActiveListings().thenAccept(loaded -> {
                this.listings = loaded;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    buildGui();
                    player.openInventory(inventory);
                    guiManager.registerGui(player.getUniqueId(), this);
                });
            });
        }

        private void buildGui() {
            var msgManager = plugin.getMessageManager();
            String title = TextUtil.colorizeToString(msgManager.getRaw("gui-titles.admin-listings"));
            inventory = Bukkit.createInventory(this, 54, title);

            ItemStack filler = new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name(" ").build();
            for (int i = 45; i < 54; i++) {
                inventory.setItem(i, filler);
            }

            int start = page * 45;
            int end = Math.min(start + 45, listings.size());
            for (int i = start; i < end; i++) {
                var listing = listings.get(i);
                ItemStack display = listing.getItem().clone();
                inventory.setItem(i - start, new ItemBuilder(display)
                        .addLore(java.util.List.of(
                                "",
                                "&7Seller: &f" + listing.getSellerName(),
                                "&7Price: &a" + msgManager.formatCurrency(listing.getPrice()),
                                "&7ID: &f#" + listing.getId(),
                                "",
                                "&cClick to remove"
                        ))
                        .build());
            }

            inventory.setItem(53, new ItemBuilder(Material.BARRIER)
                    .name(msgManager.getButton("back"))
                    .build());
        }

        @Override
        public void handleClick(InventoryClickEvent event) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            int slot = event.getRawSlot();

            if (slot == 53) {
                new AdminGui(plugin, guiManager).open(player);
                return;
            }

            if (slot >= 0 && slot < 45 && slot + page * 45 < listings.size()) {
                var listing = listings.get(slot + page * 45);
                plugin.getListingService().cancelListing(listing.getId(), player.getUniqueId(), true)
                        .thenAccept(success -> {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (success) {
                                    player.sendMessage(plugin.getMessageManager().getPrefixedP(player, 
                                            "messages.admin-listing-removed", "id", String.valueOf(listing.getId())));
                                }
                                open(player, page);
                            });
                        });
            }
        }

        @Override
        public GuiType getType() {
            return GuiType.ADMIN_LISTINGS;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /**
     * Admin view of all auctions with cancel capability
     */
    private static class AdminAuctionsGui implements MarketGui {
        private final Market plugin;
        private final GuiManager guiManager;
        private Inventory inventory;
        private Player player;
        private int page;
        private java.util.List<pt.henrique.market.model.Auction> auctions;

        public AdminAuctionsGui(Market plugin, GuiManager guiManager) {
            this.plugin = plugin;
            this.guiManager = guiManager;
        }

        public void open(Player player, int page) {
            this.player = player;
            this.page = page;

            plugin.getAuctionService().getActiveAuctions().thenAccept(loaded -> {
                this.auctions = loaded;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    buildGui();
                    player.openInventory(inventory);
                    guiManager.registerGui(player.getUniqueId(), this);
                });
            });
        }

        private void buildGui() {
            var msgManager = plugin.getMessageManager();
            String title = TextUtil.colorizeToString(msgManager.getRaw("gui-titles.admin-auctions"));
            inventory = Bukkit.createInventory(this, 54, title);

            ItemStack filler = new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name(" ").build();
            for (int i = 45; i < 54; i++) {
                inventory.setItem(i, filler);
            }

            int start = page * 45;
            int end = Math.min(start + 45, auctions.size());
            for (int i = start; i < end; i++) {
                var auction = auctions.get(i);
                ItemStack display = auction.getItem().clone();
                String bidder = auction.getHighestBidderName() != null ? auction.getHighestBidderName() : "None";
                inventory.setItem(i - start, new ItemBuilder(display)
                        .addLore(java.util.List.of(
                                "",
                                "&7Seller: &f" + auction.getSellerName(),
                                "&7Current Bid: &a" + msgManager.formatCurrency(auction.getCurrentBid()),
                                "&7Bidder: &f" + bidder,
                                "&7Bids: &f" + auction.getBidCount(),
                                "&7ID: &f#" + auction.getId(),
                                "",
                                "&cClick to force-end"
                        ))
                        .build());
            }

            inventory.setItem(53, new ItemBuilder(Material.BARRIER)
                    .name(msgManager.getButton("back"))
                    .build());
        }

        @Override
        public void handleClick(InventoryClickEvent event) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            int slot = event.getRawSlot();

            if (slot == 53) {
                new AdminGui(plugin, guiManager).open(player);
                return;
            }

            if (slot >= 0 && slot < 45 && slot + page * 45 < auctions.size()) {
                var auction = auctions.get(slot + page * 45);
                plugin.getAuctionService().cancelAuction(auction.getId(), player.getUniqueId(), true)
                        .thenAccept(result -> {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                player.sendMessage(plugin.getMessageManager().getPrefixedP(player, 
                                        "messages.admin-auction-cancelled", "id", String.valueOf(auction.getId())));
                                open(player, page);
                            });
                        });
            }
        }

        @Override
        public GuiType getType() {
            return GuiType.ADMIN_AUCTIONS;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}

