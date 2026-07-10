package pt.henrique.market.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import pt.henrique.market.Market;
import pt.henrique.market.hook.ToolModsHook;
import pt.henrique.market.model.Auction;
import pt.henrique.market.service.AuctionService;
import pt.henrique.market.util.ItemBuilder;
import pt.henrique.market.util.TextUtil;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GUI for browsing active auctions.
 * Left-click to bid, right-click to buyout (if available).
 */
public class BrowseAuctionsGui implements MarketGui {

    private final Market plugin;
    private final GuiManager guiManager;
    private Inventory inventory;
    private Player player;
    private int currentPage;
    private List<Auction> auctions;

    /** Where the player navigated from — determines Back behaviour */
    private NavigationOrigin origin = NavigationOrigin.AUCTIONS_MENU;

    // Layout constants
    private static final int ITEMS_PER_PAGE = 45;
    private static final int PREV_PAGE_SLOT = 45;
    private static final int INFO_SLOT = 49;
    private static final int NEXT_PAGE_SLOT = 50;
    private static final int BACK_SLOT = 53;

    public BrowseAuctionsGui(Market plugin, GuiManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    /**
     * Opens the browse auctions GUI with origin tracking.
     */
    public void open(Player player, int page, NavigationOrigin origin) {
        this.player = player;
        this.currentPage = page;
        this.origin = origin;

        loadAndOpen();
    }

    public void open(Player player, int page) {
        this.player = player;
        this.currentPage = page;

        loadAndOpen();
    }

    private void loadAndOpen() {
        // Load auctions asynchronously
        plugin.getAuctionService().getActiveAuctions().thenAccept(loadedAuctions -> {
            this.auctions = loadedAuctions;

            Bukkit.getScheduler().runTask(plugin, () -> {
                buildGui();
                player.openInventory(inventory);
                guiManager.registerGui(player.getUniqueId(), this);
                playSound(player, plugin.getConfigManager().getClickSound());
            });
        });
    }

    private void buildGui() {
        var msgManager = plugin.getMessageManager();

        String title = msgManager.getRaw("gui-titles.browse-auctions")
                .replace("{page}", String.valueOf(currentPage + 1));
        inventory = Bukkit.createInventory(this, 54, TextUtil.colorizeToString(title));

        // Fill bottom row
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, filler);
        }

        // Add auctions
        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, auctions.size());

        for (int i = startIndex; i < endIndex; i++) {
            Auction auction = auctions.get(i);
            int slot = i - startIndex;
            inventory.setItem(slot, createAuctionItem(auction));
        }

        // Navigation
        if (currentPage > 0) {
            inventory.setItem(PREV_PAGE_SLOT, new ItemBuilder(Material.ARROW)
                    .name(msgManager.getButton("previous-page"))
                    .build());
        }

        int totalPages = (int) Math.ceil((double) auctions.size() / ITEMS_PER_PAGE);
        if (currentPage < totalPages - 1) {
            inventory.setItem(NEXT_PAGE_SLOT, new ItemBuilder(Material.ARROW)
                    .name(msgManager.getButton("next-page"))
                    .build());
        }

        inventory.setItem(INFO_SLOT, new ItemBuilder(Material.PAPER)
                .name("&ePage " + (currentPage + 1) + "/" + Math.max(1, totalPages))
                .lore("&7Total auctions: &f" + auctions.size())
                .build());

        inventory.setItem(BACK_SLOT, new ItemBuilder(Material.BARRIER)
                .name(msgManager.getButton("back"))
                .build());
    }

    private ItemStack createAuctionItem(Auction auction) {
        var msgManager = plugin.getMessageManager();

        ItemStack display = auction.getItem().clone();

        // Time remaining
        Duration remaining = Duration.between(Instant.now(), auction.getEndsAt());
        String ends = TextUtil.formatDuration(remaining);

        // Current bidder
        String bidder = auction.getHighestBidderName() != null ? auction.getHighestBidderName() : "&7None";
        String currentBid = auction.getBidCount() > 0
                ? msgManager.formatCurrency(auction.getCurrentBid())
                : msgManager.formatCurrency(auction.getStartPrice());

        List<String> lore = new ArrayList<>();
        lore.add("");
        for (String line : msgManager.getLore("auction-info", Map.of(
                "seller", auction.getSellerName(),
                "start_price", msgManager.formatCurrency(auction.getStartPrice()),
                "current_bid", currentBid,
                "bidder", bidder,
                "bid_count", String.valueOf(auction.getBidCount()),
                "ends", ends
        ))) {
            lore.add(line);
        }

        // Add buyout info if available
        if (auction.hasBuyout()) {
            lore.add("&7Buyout: &a" + msgManager.formatCurrency(auction.getBuyoutPrice()));
        }

        // Add ToolMods info if the auctioned item has mods
        List<String> modNames = ToolModsHook.getModNames(auction.getItem());
        if (!modNames.isEmpty()) {
            lore.add("");
            lore.add("§6⚙ Mods:");
            for (String mod : modNames) {
                lore.add("§7• " + mod);
            }
        }

        return new ItemBuilder(display)
                .addLore(lore)
                .build();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        if (slot == BACK_SLOT) {
            navigateBack(player);
            return;
        }

        if (slot == PREV_PAGE_SLOT && currentPage > 0) {
            open(player, currentPage - 1);
            return;
        }

        int totalPages = (int) Math.ceil((double) auctions.size() / ITEMS_PER_PAGE);
        if (slot == NEXT_PAGE_SLOT && currentPage < totalPages - 1) {
            open(player, currentPage + 1);
            return;
        }

        // Click on auction
        if (slot >= 0 && slot < ITEMS_PER_PAGE) {
            int auctionIndex = currentPage * ITEMS_PER_PAGE + slot;
            if (auctionIndex < auctions.size()) {
                Auction auction = auctions.get(auctionIndex);

                // Right-click for buyout
                if (event.getClick() == ClickType.RIGHT && auction.hasBuyout()) {
                    handleBuyout(player, auction);
                } else {
                    // Left-click for bid
                    handleBid(player, auction);
                }
            }
        }
    }

    private void handleBid(Player player, Auction auction) {
        var msgManager = plugin.getMessageManager();

        // Can't bid on own auction
        if (auction.getSellerUuid().equals(player.getUniqueId())) {
            player.sendMessage(msgManager.getPrefixed("messages.auction-own-item"));
            playSound(player, plugin.getConfigManager().getErrorSound());
            return;
        }

        // Calculate minimum bid
        double minBid = plugin.getAuctionService().calculateMinBid(auction);

        // Open number input for bid amount
        guiManager.openNumberInput(player, bidAmount -> {
            if (bidAmount <= 0) {
                guiManager.openBrowseAuctions(player, currentPage);
                return;
            }

            // Place bid
            plugin.getAuctionService().placeBid(auction.getId(), player, bidAmount)
                    .thenAccept(result -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            switch (result) {
                                case SUCCESS -> {
                                    player.sendMessage(msgManager.getPrefixed("messages.auction-bid-placed", Map.of(
                                            "amount", msgManager.formatCurrency(bidAmount),
                                            "item", auction.getItem().getType().name()
                                    )));
                                    playSound(player, plugin.getConfigManager().getSuccessSound());
                                    guiManager.openBrowseAuctions(player, currentPage);
                                }
                                case BID_TOO_LOW -> {
                                    player.sendMessage(msgManager.getPrefixed("messages.auction-bid-too-low",
                                            "min", msgManager.formatCurrency(minBid)));
                                    playSound(player, plugin.getConfigManager().getErrorSound());
                                }
                                case INSUFFICIENT_FUNDS -> {
                                    player.sendMessage(msgManager.getPrefixed("messages.auction-insufficient-funds",
                                            "price", msgManager.formatCurrency(bidAmount)));
                                    playSound(player, plugin.getConfigManager().getErrorSound());
                                }
                                default -> {
                                    player.sendMessage(msgManager.getPrefixed("messages.auction-not-found"));
                                    playSound(player, plugin.getConfigManager().getErrorSound());
                                    guiManager.openBrowseAuctions(player, currentPage);
                                }
                            }
                        });
                    });
        }, minBid, minBid, plugin.getConfigManager().getMaxPrice(),
           msgManager.getRaw("gui-titles.number-input"));
    }

    private void handleBuyout(Player player, Auction auction) {
        var msgManager = plugin.getMessageManager();

        if (auction.getSellerUuid().equals(player.getUniqueId())) {
            player.sendMessage(msgManager.getPrefixed("messages.auction-own-item"));
            playSound(player, plugin.getConfigManager().getErrorSound());
            return;
        }

        String[] info = {
                "&7Item: &f" + auction.getItem().getType().name(),
                "&7Buyout Price: &a" + msgManager.formatCurrency(auction.getBuyoutPrice()),
                "",
                "&eClick to confirm buyout!"
        };

        guiManager.openConfirmation(player, confirmed -> {
            if (confirmed) {
                plugin.getAuctionService().buyout(auction.getId(), player)
                        .thenAccept(result -> {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (result == AuctionService.BidResult.BUYOUT_SUCCESS) {
                                    player.sendMessage(msgManager.getPrefixed("messages.auction-buyout", Map.of(
                                            "item", auction.getItem().getType().name(),
                                            "price", msgManager.formatCurrency(auction.getBuyoutPrice())
                                    )));
                                    playSound(player, plugin.getConfigManager().getPurchaseSound());
                                } else {
                                    player.sendMessage(msgManager.getPrefixed("messages.auction-not-found"));
                                    playSound(player, plugin.getConfigManager().getErrorSound());
                                }
                                guiManager.openBrowseAuctions(player, currentPage);
                            });
                        });
            } else {
                guiManager.openBrowseAuctions(player, currentPage);
            }
        }, msgManager.getRaw("gui-titles.confirm-purchase"), info);
    }

    /**
     * Navigate back based on origin.
     */
    private void navigateBack(Player player) {
        switch (origin) {
            case AUCTIONS_MENU -> guiManager.openAuctionsMenu(player, NavigationOrigin.MAIN_MENU);
            case COMMUNITY_MENU -> guiManager.openCommunityMenu(player);
            default -> guiManager.openMainMenu(player);
        }
    }

    private void playSound(Player player, String soundName) {
        pt.henrique.market.util.SoundUtil.playSound(player, soundName);
    }

    @Override
    public GuiType getType() {
        return GuiType.BROWSE_AUCTIONS;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

