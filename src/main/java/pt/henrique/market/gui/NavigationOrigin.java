package pt.henrique.market.gui;

/**
 * Represents where the player navigated from, used to determine
 * where the "Back" button should return to.
 */
public enum NavigationOrigin {

    /** Navigated directly from the main hub menu */
    MAIN_MENU,

    /** Navigated from the Community Market sub-menu */
    COMMUNITY_MENU,

    /** Navigated from the Auctions sub-menu */
    AUCTIONS_MENU
}

