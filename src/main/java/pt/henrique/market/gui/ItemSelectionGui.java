package pt.henrique.market.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import pt.henrique.market.Market;
import pt.henrique.market.util.ItemBuilder;
import pt.henrique.market.util.InventoryUtil;
import pt.henrique.market.util.SoundUtil;
import pt.henrique.market.util.TextUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * GUI for selecting an item from the player's inventory to list or auction.
 * Displays a mirror view of the player's inventory as clickable icons.
 * This replaces the "drag item into slot" workflow for better UX.
 */
public class ItemSelectionGui implements MarketGui {

    private final Market plugin;
    private final GuiManager guiManager;
    private final SelectionMode mode;
    private Inventory inventory;
    private Player player;

    // Maps GUI slot -> player inventory slot for tracking selections
    private final Map<Integer, Integer> slotMapping = new HashMap<>();

    // Layout: 54-slot chest
    // Rows 0-3 (slots 0-35): Player inventory mirror
    // Row 4 (slots 36-44): Hotbar mirror
    // Row 5 (slots 45-53): Navigation/info bar

    private static final int INFO_SLOT = 49;
    private static final int BACK_SLOT = 45;

    public enum SelectionMode {
        LISTING,
        AUCTION
    }

    public ItemSelectionGui(Market plugin, GuiManager guiManager, SelectionMode mode) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.mode = mode;
    }

    /**
     * Opens the item selection GUI for a player
     */
    public void open(Player player) {
        this.player = player;

        var msgManager = plugin.getMessageManager();
        String titleKey = mode == SelectionMode.LISTING
                ? "gui-titles.select-item-listing"
                : "gui-titles.select-item-auction";
        String title = TextUtil.colorizeToString(msgManager.getRaw(titleKey));

        inventory = Bukkit.createInventory(this, 54, title);

        buildGui();
        player.openInventory(inventory);
        guiManager.registerGui(player.getUniqueId(), this);
    }

    private void buildGui() {
        var msgManager = plugin.getMessageManager();
        slotMapping.clear();

        // Fill bottom row with glass
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, filler);
        }

        PlayerInventory playerInv = player.getInventory();

        // Mirror player's main inventory (slots 9-35 in player inv -> slots 0-26 in GUI)
        for (int i = 9; i < 36; i++) {
            ItemStack item = playerInv.getItem(i);
            int guiSlot = i - 9; // Maps 9-35 to 0-26

            if (item != null && !item.getType().isAir()) {
                // Check if item can be listed
                if (canBeSelected(item)) {
                    inventory.setItem(guiSlot, createSelectableItem(item));
                    slotMapping.put(guiSlot, i);
                } else {
                    // Show as blocked/unavailable
                    inventory.setItem(guiSlot, createBlockedItem(item));
                }
            }
        }

        // Mirror hotbar (slots 0-8 in player inv -> slots 27-35 in GUI)
        for (int i = 0; i < 9; i++) {
            ItemStack item = playerInv.getItem(i);
            int guiSlot = 27 + i; // Maps 0-8 to 27-35

            if (item != null && !item.getType().isAir()) {
                if (canBeSelected(item)) {
                    inventory.setItem(guiSlot, createSelectableItem(item));
                    slotMapping.put(guiSlot, i);
                } else {
                    inventory.setItem(guiSlot, createBlockedItem(item));
                }
            }
        }

        // Info display
        String infoLoreKey = mode == SelectionMode.LISTING ? "item-selection.info-lore-listing" : "item-selection.info-lore-auction";
        String[] infoLoreParts = msgManager.getRaw(infoLoreKey).split("\\|");
        java.util.List<String> infoLore = new java.util.ArrayList<>();
        for (String part : infoLoreParts) {
            infoLore.add(part);
        }
        infoLore.add("");
        infoLore.add(msgManager.getRaw("item-selection.blacklisted-note"));

        inventory.setItem(INFO_SLOT, new ItemBuilder(Material.PAPER)
                .name(msgManager.getRaw("item-selection.info-title"))
                .lore(infoLore)
                .build());

        // Back button
        inventory.setItem(BACK_SLOT, new ItemBuilder(Material.BARRIER)
                .name(msgManager.getButton("back"))
                .lore(msgManager.getRaw("item-selection.back-lore"))
                .build());
    }

    /**
     * Checks if an item can be selected for listing/auction
     */
    private boolean canBeSelected(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }

        // Check BuildersDream — reject creative dream items
        if (plugin.getBuildersDreamHook() != null && plugin.getBuildersDreamHook().isDreamItem(item)) {
            return false;
        }

        // Check material blacklist
        if (plugin.getConfigManager().isMaterialBlacklisted(item.getType())) {
            return false;
        }

        // Check keyword blacklist in name/lore
        var validation = plugin.getTransactionService().validateItem(item);
        return validation.isValid();
    }

    /**
     * Creates a display item with selection lore
     */
    private ItemStack createSelectableItem(ItemStack original) {
        var msgManager = plugin.getMessageManager();
        return new ItemBuilder(original.clone())
                .addLore(java.util.List.of(
                        "",
                        msgManager.getRaw("item-selection.click-to-select")
                ))
                .build();
    }

    /**
     * Creates a blocked/unavailable item display
     */
    private ItemStack createBlockedItem(ItemStack original) {
        var msgManager = plugin.getMessageManager();
        return new ItemBuilder(Material.BARRIER)
                .name(msgManager.getRaw("item-selection.blocked-title").replace("{material}", original.getType().name()))
                .lore(
                        msgManager.getRaw("item-selection.blocked-lore-1"),
                        msgManager.getRaw("item-selection.blocked-lore-2")
                )
                .build();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        // Back button
        if (slot == BACK_SLOT) {
            guiManager.openMainMenu(player);
            return;
        }

        // Check if clicked slot contains a selectable item
        if (slotMapping.containsKey(slot)) {
            int playerInvSlot = slotMapping.get(slot);
            ItemStack selectedItem = player.getInventory().getItem(playerInvSlot);

            // Verify item still exists
            if (selectedItem == null || selectedItem.getType().isAir()) {
                player.sendMessage(plugin.getMessageManager().getPrefixedP(player, "messages.invalid-item"));
                SoundUtil.playSound(player, plugin.getConfigManager().getErrorSound());
                open(player); // Refresh
                return;
            }

            // Verify item is still valid
            if (!canBeSelected(selectedItem)) {
                player.sendMessage(plugin.getMessageManager().getPrefixedP(player, "messages.blacklisted-item"));
                SoundUtil.playSound(player, plugin.getConfigManager().getErrorSound());
                return;
            }

            SoundUtil.playSound(player, plugin.getConfigManager().getClickSound());

            // Check if item is stackable (max stack size > 1)
            int maxStackSize = selectedItem.getMaxStackSize();
            int availableQuantity = InventoryUtil.countSimilarItems(player.getInventory(), selectedItem);

            if (maxStackSize > 1 && availableQuantity > 1) {
                // Stackable item with more than 1 available - show quantity selector
                new QuantitySelectGui(plugin, guiManager, quantity -> {
                    if (quantity > 0) {
                        // Proceed with the selected quantity
                        ItemStack itemWithQuantity = selectedItem.clone();
                        itemWithQuantity.setAmount(quantity);

                        if (mode == SelectionMode.LISTING) {
                            guiManager.getCreateListingGui().openWithItem(player, playerInvSlot, itemWithQuantity);
                        } else {
                            guiManager.getCreateAuctionGui().openWithItem(player, playerInvSlot, itemWithQuantity);
                        }
                    } else {
                        // User cancelled - go back to item selection
                        open(player);
                    }
                }, selectedItem, availableQuantity).open(player);
            } else {
                // Unstackable item or only 1 available - skip quantity selection
                ItemStack singleItem = selectedItem.clone();
                singleItem.setAmount(1);

                if (mode == SelectionMode.LISTING) {
                    guiManager.getCreateListingGui().openWithItem(player, playerInvSlot, singleItem);
                } else {
                    guiManager.getCreateAuctionGui().openWithItem(player, playerInvSlot, singleItem);
                }
            }
        }
    }

    @Override
    public GuiType getType() {
        return GuiType.ITEM_SELECTION;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

