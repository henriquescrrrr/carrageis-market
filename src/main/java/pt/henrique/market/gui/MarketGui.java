package pt.henrique.market.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import pt.henrique.market.Market;

/**
 * Base interface for all market GUI screens.
 * Implements InventoryHolder to allow identification of market inventories.
 */
public interface MarketGui extends InventoryHolder {

    /**
     * Gets the GUI type identifier
     */
    GuiType getType();

    /**
     * Handles a click event in this GUI
     *
     * @param event The click event
     */
    void handleClick(InventoryClickEvent event);

    /**
     * Handles a drag event in this GUI
     *
     * @param event The drag event
     */
    default void handleDrag(InventoryDragEvent event) {
        // By default, cancel all drags to prevent item manipulation
        event.setCancelled(true);
    }

    /**
     * Handles the inventory being closed
     *
     * @param event The close event
     */
    default void handleClose(InventoryCloseEvent event) {
        // Default: do nothing special
    }

    /**
     * Checks if items can be moved in this GUI
     * Most GUIs should return false, but create listing/auction GUIs need true
     */
    default boolean allowsItemMovement() {
        return false;
    }

    /**
     * Enum of all GUI types for identification
     */
    enum GuiType {
        MAIN_MENU,
        COMMUNITY_MENU,
        AUCTIONS_MENU,
        BROWSE_MARKET,
        BROWSE_AUCTIONS,
        CREATE_LISTING,
        CREATE_AUCTION,
        ITEM_SELECTION,
        QUANTITY_SELECT,
        MY_LISTINGS,
        MY_AUCTIONS,
        CLAIM,
        EARNINGS,
        HELP,
        ADMIN,
        ADMIN_LISTINGS,
        ADMIN_AUCTIONS,
        NUMBER_INPUT,
        PERCENT_INPUT,
        CONFIRMATION,
        LISTING_DETAILS,
        AUCTION_DETAILS,
        DURATION_SELECT,
        FILTER_MENU,
        SORT_MENU,
        // Server Shop GUI types
        SHOP_MAIN_MENU,
        SHOP_CATEGORY,
        SHOP_ITEM_DETAIL,
        SHOP_SEARCH_RESULTS,
        SHOP_SELL_INVENTORY,
        SHOP_SELL_ONLY,
        SHOP_SELL_CHOOSER,
        SHOP_SELL_ALL_CONFIRM,
        SHOP_MULTI_SELL_QTY,
        SHOP_MULTI_SELL_CONFIRM,
        SHOP_SHULKER_SELL_CHOOSER,
        SELL_CHEST_PREVIEW
    }
}

