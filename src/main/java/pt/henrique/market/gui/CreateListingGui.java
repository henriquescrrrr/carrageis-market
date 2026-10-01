package pt.henrique.market.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import pt.henrique.market.Market;
import pt.henrique.market.util.ItemBuilder;
import pt.henrique.market.util.InventoryUtil;
import pt.henrique.market.util.SoundUtil;
import pt.henrique.market.util.TextUtil;

import java.util.List;

/**
 * GUI for setting up a new fixed-price listing.
 * This GUI is opened AFTER the player has selected an item and quantity.
 * All elements are merged: price and duration are single clickable items.
 *
 * Layout (54-slot chest):
 * ┌─────────────────────────────────────────────────────┐
 * │  .   .   .   .  INFO  .   .   .   .  │ Row 0        │
 * │  .   .   .   .  ITEM  .   .   .   .  │ Row 1: Item  │
 * │  .   .   .   .   .   .   .   .   .   │ Row 2        │
 * │  .   .  PRICE  .   .   .  DURATION   │ Row 3: Setup │
 * │  .   .   .   .   .   .   .   .   .   │ Row 4        │
 * │ BACK .   .   .  CONFIRM .   .   .   .│ Row 5: Action│
 * └─────────────────────────────────────────────────────┘
 */
public class CreateListingGui implements MarketGui {

    private final Market plugin;
    private final GuiManager guiManager;
    private Inventory inventory;
    private Player player;

    // Selected item info (from ItemSelectionGui -> QuantitySelectGui)
    private int sourceInventorySlot = -1;
    private ItemStack selectedItem = null;

    // Listing settings
    private double price;
    private int durationHours;

    // Guards against double-submit: a player spam-clicking CONFIRM would otherwise
    // create several listings from a single stack (the item is only removed after
    // the async create returns), duplicating items. Set on the main thread before
    // the async call, so subsequent clicks in the same tick are ignored.
    private boolean submitting = false;

    // ==================== LAYOUT CONSTANTS ====================
    private static final int INFO_SLOT = 4;           // Top center
    private static final int ITEM_DISPLAY_SLOT = 13;  // Center row 1

    // Row 3: Merged elements (single slot each)
    private static final int PRICE_SLOT = 29;         // Price (display + click to adjust)
    private static final int DURATION_SLOT = 33;      // Duration (display + click to cycle)

    private static final int BACK_SLOT = 45;          // Bottom-left
    private static final int CONFIRM_SLOT = 49;       // Bottom-center
    // ===========================================================

    public CreateListingGui(Market plugin, GuiManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    /**
     * Opens the listing creation GUI with a pre-selected item and quantity.
     * Called from QuantitySelectGui or ItemSelectionGui (for unstackable items).
     *
     * @param player The player
     * @param inventorySlot The slot in the player's inventory where the item is
     * @param item A clone of the selected item with the desired quantity
     */
    public void openWithItem(Player player, int inventorySlot, ItemStack item) {
        this.player = player;
        this.sourceInventorySlot = inventorySlot;
        this.selectedItem = item;
        this.durationHours = plugin.getConfigManager().getDefaultDurationHours();
        this.price = plugin.getConfigManager().getMinPrice();

        var msgManager = plugin.getMessageManager();
        String title = TextUtil.colorizeToString(msgManager.getRaw("gui-titles.create-listing"));
        inventory = Bukkit.createInventory(this, 54, title);

        buildGui();
        player.openInventory(inventory);
        guiManager.registerGui(player.getUniqueId(), this);
    }

    /**
     * @deprecated Use openWithItem instead. This opens item selection first.
     */
    public void open(Player player) {
        new ItemSelectionGui(plugin, guiManager, ItemSelectionGui.SelectionMode.LISTING).open(player);
    }

    private void buildGui() {
        var msgManager = plugin.getMessageManager();

        // Fill with glass
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, filler);
        }

        // Info panel
        inventory.setItem(INFO_SLOT, new ItemBuilder(Material.OAK_SIGN)
                .name(msgManager.getRaw("create-listing.info-title"))
                .lore(
                        msgManager.getRaw("create-listing.info-lore-1"),
                        msgManager.getRaw("create-listing.info-lore-2"),
                        "",
                        msgManager.getRaw("create-listing.tax-info").replace("{tax}", String.valueOf(plugin.getConfigManager().getMarketTax()))
                )
                .build());

        // Selected item display
        if (selectedItem != null) {
            String[] itemLoreParts = msgManager.getRaw("create-listing.item-lore").split("\\|");
            List<String> itemLore = new java.util.ArrayList<>();
            itemLore.add("");
            for (String part : itemLoreParts) {
                itemLore.add(part.replace("{amount}", String.valueOf(selectedItem.getAmount())));
            }

            inventory.setItem(ITEM_DISPLAY_SLOT, new ItemBuilder(selectedItem.clone())
                    .addLore(itemLore)
                    .build());
        }

        // Merged price element
        updatePriceElement();

        // Merged duration element
        updateDurationElement();

        // Back button
        inventory.setItem(BACK_SLOT, new ItemBuilder(Material.RED_WOOL)
                .name(msgManager.getButton("back"))
                .lore(msgManager.getRaw("create-listing.back-lore"))
                .build());

        // Confirm button
        updateConfirmButton();
    }

    /**
     * Updates the merged price element (displays current price + clickable to change)
     */
    private void updatePriceElement() {
        var msgManager = plugin.getMessageManager();
        double tax = plugin.getTransactionService().calculateListingTax(price);
        double earnings = price - tax;

        inventory.setItem(PRICE_SLOT, new ItemBuilder(Material.GOLD_INGOT)
                .name(msgManager.getRaw("create-listing.price-title").replace("{price}", msgManager.formatCurrency(price)))
                .lore(
                        "",
                        msgManager.getRaw("create-listing.price-tax")
                                .replace("{percent}", String.valueOf(plugin.getConfigManager().getMarketTax()))
                                .replace("{amount}", msgManager.formatCurrency(tax)),
                        msgManager.getRaw("create-listing.price-earnings").replace("{amount}", msgManager.formatCurrency(earnings)),
                        "",
                        msgManager.getRaw("create-listing.price-click")
                )
                .glow()
                .build());
    }

    /**
     * Updates the merged duration element (displays current duration + clickable to cycle)
     */
    private void updateDurationElement() {
        var msgManager = plugin.getMessageManager();
        String durationText = formatDuration(durationHours);

        inventory.setItem(DURATION_SLOT, new ItemBuilder(Material.CLOCK)
                .name(msgManager.getRaw("create-listing.duration-title").replace("{duration}", durationText))
                .lore(
                        "",
                        msgManager.getRaw("create-listing.duration-lore"),
                        "",
                        msgManager.getRaw("create-listing.duration-click")
                )
                .glow()
                .build());
    }

    private String formatDuration(int hours) {
        if (hours >= 24) {
            int days = hours / 24;
            return days + " day" + (days > 1 ? "s" : "");
        } else {
            return hours + " hour" + (hours > 1 ? "s" : "");
        }
    }

    private void updateConfirmButton() {
        var msgManager = plugin.getMessageManager();
        double tax = plugin.getTransactionService().calculateListingTax(price);
        double earnings = price - tax;

        inventory.setItem(CONFIRM_SLOT, new ItemBuilder(Material.LIME_WOOL)
                .name(msgManager.getButton("confirm"))
                .lore(
                        msgManager.getRaw("create-listing.confirm-item")
                                .replace("{item}", selectedItem.getType().name())
                                .replace("{amount}", String.valueOf(selectedItem.getAmount())),
                        msgManager.getRaw("create-listing.confirm-price").replace("{price}", msgManager.formatCurrency(price)),
                        msgManager.getRaw("create-listing.confirm-earnings").replace("{amount}", msgManager.formatCurrency(earnings)),
                        msgManager.getRaw("create-listing.confirm-duration").replace("{duration}", formatDuration(durationHours)),
                        "",
                        msgManager.getRaw("create-listing.confirm-click")
                )
                .glow()
                .build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        var msgManager = plugin.getMessageManager();

        switch (slot) {
            case PRICE_SLOT -> {
                // Open number input for price
                guiManager.openNumberInput(player, newPrice -> {
                    if (newPrice > 0) {
                        this.price = newPrice;
                    }
                    reopenGui();
                }, price, plugin.getConfigManager().getMinPrice(),
                   plugin.getConfigManager().getMaxPrice(),
                   msgManager.getRaw("gui-titles.number-input"));
            }
            case DURATION_SLOT -> {
                // Cycle through available durations
                List<Integer> durations = plugin.getConfigManager().getAvailableDurations();
                if (!durations.isEmpty()) {
                    int currentIndex = durations.indexOf(durationHours);
                    int nextIndex = (currentIndex + 1) % durations.size();
                    durationHours = durations.get(nextIndex);
                    updateDurationElement();
                    updateConfirmButton();
                    SoundUtil.playSound(player, plugin.getConfigManager().getClickSound());
                }
            }
            case CONFIRM_SLOT -> {
                confirmListing(player);
            }
            case BACK_SLOT -> {
                // Go back to item selection
                new ItemSelectionGui(plugin, guiManager, ItemSelectionGui.SelectionMode.LISTING).open(player);
            }
        }
    }

    private void confirmListing(Player player) {
        var msgManager = plugin.getMessageManager();

        // Prevent double-submit (spam-clicking CONFIRM) which would duplicate the item
        if (submitting) {
            return;
        }

        // Verify item still exists in player's inventory with sufficient quantity
        int available = InventoryUtil.countSimilarItems(player.getInventory(), selectedItem);

        if (available < selectedItem.getAmount()) {
            if (available < 1) {
                player.sendMessage(msgManager.getPrefixed("messages.item-no-longer-available"));
            } else {
                player.sendMessage(msgManager.getPrefixed("messages.quantity-changed"));
            }
            SoundUtil.playSound(player, plugin.getConfigManager().getErrorSound());
            new ItemSelectionGui(plugin, guiManager, ItemSelectionGui.SelectionMode.LISTING).open(player);
            return;
        }

        // Validate item again
        var validation = plugin.getTransactionService().validateItem(selectedItem);
        if (!validation.isValid()) {
            player.sendMessage(msgManager.getPrefixed("messages." + validation.getErrorKey()));
            SoundUtil.playSound(player, plugin.getConfigManager().getErrorSound());
            return;
        }

        // Reject BuildersDream creative dream items (defense-in-depth)
        if (plugin.getBuildersDreamHook() != null && plugin.getBuildersDreamHook().isDreamItem(selectedItem)) {
            player.sendMessage(msgManager.getPrefixed("messages.dream-item-blocked"));
            SoundUtil.playSound(player, plugin.getConfigManager().getErrorSound());
            return;
        }

        // Create the listing
        int amount = selectedItem.getAmount();
        ItemStack listItem = selectedItem.clone();

        // Lock further submits until this attempt resolves
        submitting = true;

        // Reserve the item NOW (remove it before the async create) so the player
        // cannot move/drop it during the DB round-trip and end up with both the
        // item AND the listing (dupe). If the create fails, the item is returned.
        // Removing on the main thread here, before the async hop, is the anti-dupe
        // gate — the previously-removed-AFTER-success flow left a live window in
        // which closing the GUI freed the inventory for exfiltration.
        if (!InventoryUtil.removeItem(player, listItem, amount)) {
            // Couldn't remove the full amount (item changed under us) — abort cleanly.
            submitting = false;
            player.sendMessage(msgManager.getPrefixed("messages.item-no-longer-available"));
            SoundUtil.playSound(player, plugin.getConfigManager().getErrorSound());
            new ItemSelectionGui(plugin, guiManager, ItemSelectionGui.SelectionMode.LISTING).open(player);
            return;
        }

        plugin.getTransactionService().createListingTransaction(
                player, listItem, amount, price, durationHours
        ).thenAccept(result -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (result.isSuccess()) {
                    player.sendMessage(msgManager.getPrefixed("messages.listing-created",
                            "id", String.valueOf(result.getId())));
                    SoundUtil.playSound(player, plugin.getConfigManager().getSuccessSound());
                    player.closeInventory();
                    guiManager.openMainMenu(player);
                } else {
                    // Failed — return the reserved item and allow the player to retry.
                    for (ItemStack refund : InventoryUtil.giveItemSplit(player, listItem)) {
                        player.getWorld().dropItemNaturally(player.getLocation(), refund);
                    }
                    submitting = false;
                    player.sendMessage(msgManager.getPrefixed("messages." + result.getErrorKey()));
                    SoundUtil.playSound(player, plugin.getConfigManager().getErrorSound());
                }
            });
        });
    }

    private void reopenGui() {
        var msgManager = plugin.getMessageManager();
        String title = TextUtil.colorizeToString(msgManager.getRaw("gui-titles.create-listing"));
        inventory = Bukkit.createInventory(this, 54, title);

        buildGui();
        player.openInventory(inventory);
    }

    @Override
    public boolean allowsItemMovement() {
        return false;
    }

    @Override
    public GuiType getType() {
        return GuiType.CREATE_LISTING;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
