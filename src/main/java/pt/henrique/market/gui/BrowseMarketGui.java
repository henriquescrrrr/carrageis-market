package pt.henrique.market.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import pt.henrique.market.Market;
import pt.henrique.market.hook.ToolModsHook;
import pt.henrique.market.model.Listing;
import pt.henrique.market.service.ListingService;
import pt.henrique.market.util.ItemBuilder;
import pt.henrique.market.util.TextUtil;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GUI for browsing the market's fixed-price listings.
 * Features pagination, and click-to-buy functionality.
 */
public class BrowseMarketGui implements MarketGui {

    private final Market plugin;
    private final GuiManager guiManager;
    private Inventory inventory;
    private Player player;
    private int currentPage;
    private List<Listing> listings;

    /** Where the player navigated from — determines Back behaviour */
    private NavigationOrigin origin = NavigationOrigin.COMMUNITY_MENU;

    // Layout constants
    private static final int ITEMS_PER_PAGE = 45;
    private static final int PREV_PAGE_SLOT = 45;
    private static final int INFO_SLOT = 49;
    private static final int NEXT_PAGE_SLOT = 50;
    private static final int BACK_SLOT = 53;
    private static final int FILTER_SLOT = 47;
    private static final int SORT_SLOT = 51;

    public BrowseMarketGui(Market plugin, GuiManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    /**
     * Opens the browse market GUI for a player with origin tracking.
     */
    public void open(Player player, int page, NavigationOrigin origin) {
        this.player = player;
        this.currentPage = page;
        this.origin = origin;

        loadAndOpen();
    }

    /**
     * Opens the browse market GUI for a player (legacy, defaults to COMMUNITY_MENU origin).
     */
    public void open(Player player, int page) {
        this.player = player;
        this.currentPage = page;
        loadAndOpen();
    }

    private void loadAndOpen() {
        // Load listings asynchronously
        plugin.getListingService().getActiveListings().thenAccept(loadedListings -> {
            this.listings = loadedListings;

            // Build GUI on main thread
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

        String title = msgManager.getRaw("gui-titles.browse-market")
                .replace("{page}", String.valueOf(currentPage + 1));
        inventory = Bukkit.createInventory(this, 54, TextUtil.colorizeToString(title));

        // Fill bottom row with glass
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, filler);
        }

        // Add listings to slots 0-44
        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, listings.size());

        for (int i = startIndex; i < endIndex; i++) {
            Listing listing = listings.get(i);
            int slot = i - startIndex;
            inventory.setItem(slot, createListingItem(listing));
        }

        // Navigation buttons
        if (currentPage > 0) {
            inventory.setItem(PREV_PAGE_SLOT, new ItemBuilder(Material.ARROW)
                    .name(msgManager.getButton("previous-page"))
                    .build());
        }

        int totalPages = (int) Math.ceil((double) listings.size() / ITEMS_PER_PAGE);
        if (currentPage < totalPages - 1) {
            inventory.setItem(NEXT_PAGE_SLOT, new ItemBuilder(Material.ARROW)
                    .name(msgManager.getButton("next-page"))
                    .build());
        }

        // Info/page indicator
        inventory.setItem(INFO_SLOT, new ItemBuilder(Material.PAPER)
                .name("&ePage " + (currentPage + 1) + "/" + Math.max(1, totalPages))
                .lore("&7Total listings: &f" + listings.size())
                .build());

        // Back button
        inventory.setItem(BACK_SLOT, new ItemBuilder(Material.BARRIER)
                .name(msgManager.getButton("back"))
                .build());
    }

    private ItemStack createListingItem(Listing listing) {
        var msgManager = plugin.getMessageManager();

        ItemStack display = listing.getItem().clone();
        // Display only: a count above the max stack size makes the stack resolve to
        // empty since 26.1, so the icon would render as nothing. The real amount is
        // in the lore.
        display.setAmount(Math.min(listing.getAmount(), Math.max(1, display.getMaxStackSize())));

        // Calculate time remaining
        String expires;
        if (listing.getExpiresAt() != null) {
            Duration remaining = Duration.between(Instant.now(), listing.getExpiresAt());
            expires = TextUtil.formatDuration(remaining);
        } else {
            expires = "Never";
        }

        List<String> lore = new ArrayList<>();
        lore.add(""); // Empty line separator
        for (String line : msgManager.getLore("listing-info", Map.of(
                "seller", listing.getSellerName(),
                "price", msgManager.formatCurrency(listing.getPrice()),
                "amount", String.valueOf(listing.getAmount()),
                "expires", expires
        ))) {
            lore.add(line);
        }

        // Add ToolMods info if the listed item has mods
        List<String> modNames = ToolModsHook.getModNames(listing.getItem());
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

        // Bottom row navigation
        if (slot == BACK_SLOT) {
            navigateBack(player);
            return;
        }

        if (slot == PREV_PAGE_SLOT && currentPage > 0) {
            open(player, currentPage - 1);
            return;
        }

        int totalPages = (int) Math.ceil((double) listings.size() / ITEMS_PER_PAGE);
        if (slot == NEXT_PAGE_SLOT && currentPage < totalPages - 1) {
            open(player, currentPage + 1);
            return;
        }

        // Click on a listing (slots 0-44)
        if (slot >= 0 && slot < ITEMS_PER_PAGE) {
            int listingIndex = currentPage * ITEMS_PER_PAGE + slot;
            if (listingIndex < listings.size()) {
                Listing listing = listings.get(listingIndex);
                handleListingClick(player, listing);
            }
        }
    }

    private void handleListingClick(Player player, Listing listing) {
        var msgManager = plugin.getMessageManager();

        // Can't buy own listing
        if (listing.getSellerUuid().equals(player.getUniqueId())) {
            player.sendMessage(msgManager.getPrefixed("messages.listing-own-item"));
            playSound(player, plugin.getConfigManager().getErrorSound());
            return;
        }

        // Show confirmation
        double tax = plugin.getTransactionService().calculateListingTax(listing.getPrice());
        String[] info = {
                "&7Item: &f" + listing.getItem().getType().name() + " x" + listing.getAmount(),
                "&7Seller: &f" + listing.getSellerName(),
                "&7Price: &a" + msgManager.formatCurrency(listing.getPrice()),
                "",
                "&eClick to confirm purchase!"
        };

        guiManager.openConfirmation(player, confirmed -> {
            if (confirmed) {
                // Attempt purchase
                plugin.getListingService().purchaseListing(listing.getId(), player)
                        .thenAccept(result -> {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                switch (result) {
                                    case SUCCESS -> {
                                        player.sendMessage(msgManager.getPrefixed("messages.listing-purchased", Map.of(
                                                "item", listing.getItem().getType().name(),
                                                "amount", String.valueOf(listing.getAmount()),
                                                "price", msgManager.formatCurrency(listing.getPrice())
                                        )));
                                        playSound(player, plugin.getConfigManager().getPurchaseSound());
                                        guiManager.openBrowseMarket(player, currentPage);
                                    }
                                    case INSUFFICIENT_FUNDS -> {
                                        player.sendMessage(msgManager.getPrefixed("messages.listing-insufficient-funds",
                                                "price", msgManager.formatCurrency(listing.getPrice())));
                                        playSound(player, plugin.getConfigManager().getErrorSound());
                                    }
                                    case ALREADY_SOLD, NOT_FOUND -> {
                                        player.sendMessage(msgManager.getPrefixed("messages.listing-not-found"));
                                        playSound(player, plugin.getConfigManager().getErrorSound());
                                        guiManager.openBrowseMarket(player, currentPage);
                                    }
                                    default -> {
                                        player.sendMessage(msgManager.getPrefixed("messages.listing-not-found"));
                                        playSound(player, plugin.getConfigManager().getErrorSound());
                                    }
                                }
                            });
                        });
            } else {
                guiManager.openBrowseMarket(player, currentPage);
            }
        }, msgManager.getRaw("gui-titles.confirm-purchase"), info);
    }

    /**
     * Navigate back based on origin.
     */
    private void navigateBack(Player player) {
        switch (origin) {
            case COMMUNITY_MENU -> guiManager.openCommunityMenu(player);
            case AUCTIONS_MENU -> guiManager.openAuctionsMenu(player, NavigationOrigin.MAIN_MENU);
            default -> guiManager.openMainMenu(player);
        }
    }

    private void playSound(Player player, String soundName) {
        pt.henrique.market.util.SoundUtil.playSound(player, soundName);
    }

    @Override
    public GuiType getType() {
        return GuiType.BROWSE_MARKET;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

