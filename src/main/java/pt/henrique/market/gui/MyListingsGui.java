package pt.henrique.market.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import pt.henrique.market.Market;
import pt.henrique.market.model.Listing;
import pt.henrique.market.util.ItemBuilder;
import pt.henrique.market.util.TextUtil;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GUI for viewing and managing the player's own listings.
 * Click on a listing to cancel it.
 */
public class MyListingsGui implements MarketGui {

    private final Market plugin;
    private final GuiManager guiManager;
    private Inventory inventory;
    private Player player;
    private List<Listing> listings;

    /** Where the player navigated from — determines Back behaviour */
    private NavigationOrigin origin = NavigationOrigin.COMMUNITY_MENU;

    private static final int BACK_SLOT = 53;

    public MyListingsGui(Market plugin, GuiManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    /**
     * Opens the my listings GUI with origin tracking.
     */
    public void open(Player player, NavigationOrigin origin) {
        this.player = player;
        this.origin = origin;

        loadAndOpen();
    }

    public void open(Player player) {
        this.player = player;

        loadAndOpen();
    }

    private void loadAndOpen() {
        plugin.getListingService().getPlayerListings(player.getUniqueId())
                .thenAccept(loadedListings -> {
                    this.listings = loadedListings;

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        buildGui();
                        player.openInventory(inventory);
                        guiManager.registerGui(player.getUniqueId(), this);
                    });
                });
    }

    private void buildGui() {
        var msgManager = plugin.getMessageManager();

        String title = TextUtil.colorizeToString(msgManager.getRaw("gui-titles.my-listings"));
        inventory = Bukkit.createInventory(this, 54, title);

        // Fill bottom
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, filler);
        }

        // Add listings
        for (int i = 0; i < Math.min(listings.size(), 45); i++) {
            Listing listing = listings.get(i);
            inventory.setItem(i, createListingItem(listing));
        }

        // Back button
        inventory.setItem(BACK_SLOT, new ItemBuilder(Material.BARRIER)
                .name(msgManager.getButton("back"))
                .build());
    }

    private ItemStack createListingItem(Listing listing) {
        var msgManager = plugin.getMessageManager();

        ItemStack display = listing.getItem().clone();
        display.setAmount(listing.getAmount());

        String expires;
        if (listing.getExpiresAt() != null) {
            Duration remaining = Duration.between(Instant.now(), listing.getExpiresAt());
            expires = TextUtil.formatDuration(remaining);
        } else {
            expires = "Never";
        }

        String created = TextUtil.formatDuration(
                Duration.between(listing.getCreatedAt(), Instant.now())) + " ago";

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.addAll(msgManager.getLore("my-listing-info", Map.of(
                "price", msgManager.formatCurrency(listing.getPrice()),
                "amount", String.valueOf(listing.getAmount()),
                "created", created,
                "expires", expires
        )));

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

        // Click on listing to cancel
        if (slot >= 0 && slot < 45 && slot < listings.size()) {
            Listing listing = listings.get(slot);
            confirmCancel(player, listing);
        }
    }

    /**
     * Navigate back based on origin.
     */
    private void navigateBack(Player player) {
        switch (origin) {
            case COMMUNITY_MENU -> guiManager.openCommunityMenu(player);
            default -> guiManager.openMainMenu(player);
        }
    }

    private void confirmCancel(Player player, Listing listing) {
        var msgManager = plugin.getMessageManager();

        String[] info = {
                "&7Item: &f" + listing.getItem().getType().name() + " x" + listing.getAmount(),
                "&7Price: &a" + msgManager.formatCurrency(listing.getPrice()),
                "",
                "&cThis will cancel your listing.",
                "&cThe item will be moved to claim storage."
        };

        guiManager.openConfirmation(player, confirmed -> {
            if (confirmed) {
                plugin.getListingService().cancelListing(listing.getId(), player.getUniqueId(), false)
                        .thenAccept(success -> {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (success) {
                                    player.sendMessage(msgManager.getPrefixed("messages.listing-cancelled"));
                                    playSound(player, plugin.getConfigManager().getSuccessSound());
                                } else {
                                    player.sendMessage(msgManager.getPrefixed("messages.listing-not-found"));
                                    playSound(player, plugin.getConfigManager().getErrorSound());
                                }
                                open(player); // Refresh
                            });
                        });
            } else {
                open(player);
            }
        }, msgManager.getRaw("gui-titles.confirm-cancel"), info);
    }

    private void playSound(Player player, String soundName) {
        pt.henrique.market.util.SoundUtil.playSound(player, soundName);
    }

    @Override
    public GuiType getType() {
        return GuiType.MY_LISTINGS;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

