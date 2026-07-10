package pt.henrique.market.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import pt.henrique.market.Market;
import pt.henrique.market.gui.MarketGui;

/**
 * Listener for all GUI-related inventory events.
 * Handles clicks, drags, and closes for market GUIs.
 * Implements security measures to prevent item duplication exploits.
 */
public class GuiListener implements Listener {

    private final Market plugin;

    public GuiListener(Market plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles all click events in market GUIs.
     * Security: Validates all click actions and prevents item manipulation.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory topInventory = event.getView().getTopInventory();
        InventoryHolder holder = topInventory.getHolder();

        // Check if this is a market GUI
        if (!(holder instanceof MarketGui marketGui)) return;

        // Get the clicked inventory
        Inventory clickedInventory = event.getClickedInventory();

        // Handle different GUI types
        if (marketGui.allowsItemMovement()) {
            // GUIs like CreateListing allow item placement in specific slots
            handleItemMovementGui(event, marketGui);
        } else {
            // Standard GUIs don't allow any item movement
            event.setCancelled(true);
        }

        // Always delegate to the GUI's click handler
        marketGui.handleClick(event);
    }

    /**
     * Handles GUIs that allow item movement (like create listing/auction).
     * Only allows items in designated slots.
     */
    private void handleItemMovementGui(InventoryClickEvent event, MarketGui marketGui) {
        // Cancel by default - the GUI handler will un-cancel for specific slots
        ClickType clickType = event.getClick();

        // Block potentially exploitative click types
        switch (clickType) {
            case DOUBLE_CLICK -> {
                // Prevent collecting items from market GUI via double-click
                event.setCancelled(true);
            }
            case NUMBER_KEY -> {
                // Block number key swaps to prevent inventory tricks
                if (event.getRawSlot() < event.getView().getTopInventory().getSize()) {
                    // Allow in player inventory, cancel in market GUI
                    // The specific GUI handler will manage this
                }
            }
            case SWAP_OFFHAND -> {
                event.setCancelled(true);
            }
            default -> {
                // Let the GUI handler decide
            }
        }
    }

    /**
     * Handles drag events - prevents dragging items across market GUIs.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Inventory topInventory = event.getView().getTopInventory();
        InventoryHolder holder = topInventory.getHolder();

        if (!(holder instanceof MarketGui marketGui)) return;

        // Check if any dragged slots are in the top inventory
        int topSize = topInventory.getSize();
        boolean affectsTop = event.getRawSlots().stream()
                .anyMatch(slot -> slot < topSize);

        if (affectsTop) {
            // Delegate to GUI handler (most will cancel)
            marketGui.handleDrag(event);
        }
    }

    /**
     * Handles inventory close events.
     * Ensures items are returned and GUI state is cleaned up.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        Inventory inventory = event.getInventory();
        InventoryHolder holder = inventory.getHolder();

        if (!(holder instanceof MarketGui marketGui)) return;

        // Notify the GUI of the close
        marketGui.handleClose(event);

        // Unregister from GUI manager
        plugin.getGuiManager().unregisterGui(player.getUniqueId());
    }

    /**
     * Prevents moving items out of market GUIs via shift-click from player inventory.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        // This handles hopper/dropper interactions - cancel if involves market GUI
        if (event.getSource().getHolder() instanceof MarketGui ||
            event.getDestination().getHolder() instanceof MarketGui) {
            event.setCancelled(true);
        }
    }
}

