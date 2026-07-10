package pt.henrique.market.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import pt.henrique.market.Market;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages all GUI instances and player GUI states.
 * Central hub for opening, closing, and tracking GUI interactions.
 */
public class GuiManager {

    private final Market plugin;

    // Track open GUIs per player
    private final Map<UUID, MarketGui> openGuis = new HashMap<>();

    // GUI builders/templates
    private final MainMenuGui mainMenuGui;
    private final CommunityMenuGui communityMenuGui;
    private final AuctionsMenuGui auctionsMenuGui;
    private final BrowseMarketGui browseMarketGui;
    private final BrowseAuctionsGui browseAuctionsGui;
    private final CreateListingGui createListingGui;
    private final CreateAuctionGui createAuctionGui;
    private final MyListingsGui myListingsGui;
    private final MyAuctionsGui myAuctionsGui;
    private final ClaimGui claimGui;
    private final EarningsGui earningsGui;
    private final HelpGui helpGui;
    private final AdminGui adminGui;

    public GuiManager(Market plugin) {
        this.plugin = plugin;

        // Initialize GUI handlers
        this.mainMenuGui = new MainMenuGui(plugin, this);
        this.communityMenuGui = new CommunityMenuGui(plugin, this);
        this.auctionsMenuGui = new AuctionsMenuGui(plugin, this);
        this.browseMarketGui = new BrowseMarketGui(plugin, this);
        this.browseAuctionsGui = new BrowseAuctionsGui(plugin, this);
        this.createListingGui = new CreateListingGui(plugin, this);
        this.createAuctionGui = new CreateAuctionGui(plugin, this);
        this.myListingsGui = new MyListingsGui(plugin, this);
        this.myAuctionsGui = new MyAuctionsGui(plugin, this);
        this.claimGui = new ClaimGui(plugin, this);
        this.earningsGui = new EarningsGui(plugin, this);
        this.helpGui = new HelpGui(plugin, this);
        this.adminGui = new AdminGui(plugin, this);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Hub / Sub-menu navigation
    // ═══════════════════════════════════════════════════════════════

    /** Opens the main hub menu */
    public void openMainMenu(Player player) {
        mainMenuGui.open(player);
    }

    /** Opens the Community Market sub-menu */
    public void openCommunityMenu(Player player) {
        communityMenuGui.open(player);
    }

    /** Opens the Auctions sub-menu with origin-aware back navigation */
    public void openAuctionsMenu(Player player, NavigationOrigin origin) {
        auctionsMenuGui.open(player, origin);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Community Market GUIs (origin-aware)
    // ═══════════════════════════════════════════════════════════════

    /** Opens browse market with origin tracking */
    public void openBrowseMarket(Player player, int page, NavigationOrigin origin) {
        browseMarketGui.open(player, page, origin);
    }

    /** Opens browse market (legacy — defaults to MAIN_MENU origin) */
    public void openBrowseMarket(Player player, int page) {
        browseMarketGui.open(player, page, NavigationOrigin.COMMUNITY_MENU);
    }

    /** Opens the create listing flow (starts with item selection) */
    public void openCreateListing(Player player) {
        new ItemSelectionGui(plugin, this, ItemSelectionGui.SelectionMode.LISTING).open(player);
    }

    /** Opens the player's listings GUI with origin tracking */
    public void openMyListings(Player player, NavigationOrigin origin) {
        myListingsGui.open(player, origin);
    }

    /** Opens the player's listings GUI (legacy — defaults to COMMUNITY_MENU origin) */
    public void openMyListings(Player player) {
        myListingsGui.open(player, NavigationOrigin.COMMUNITY_MENU);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Auction GUIs (origin-aware)
    // ═══════════════════════════════════════════════════════════════

    /** Opens browse auctions with origin tracking */
    public void openBrowseAuctions(Player player, int page, NavigationOrigin origin) {
        browseAuctionsGui.open(player, page, origin);
    }

    /** Opens browse auctions (legacy — defaults to AUCTIONS_MENU origin) */
    public void openBrowseAuctions(Player player, int page) {
        browseAuctionsGui.open(player, page, NavigationOrigin.AUCTIONS_MENU);
    }

    /** Opens the create auction flow (starts with item selection) */
    public void openCreateAuction(Player player) {
        new ItemSelectionGui(plugin, this, ItemSelectionGui.SelectionMode.AUCTION).open(player);
    }

    /** Opens the player's auctions GUI with origin tracking */
    public void openMyAuctions(Player player, NavigationOrigin origin) {
        myAuctionsGui.open(player, origin);
    }

    /** Opens the player's auctions GUI (legacy — defaults to AUCTIONS_MENU origin) */
    public void openMyAuctions(Player player) {
        myAuctionsGui.open(player, NavigationOrigin.AUCTIONS_MENU);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Shared GUIs (origin-aware)
    // ═══════════════════════════════════════════════════════════════

    /** Opens the claim GUI with origin tracking */
    public void openClaim(Player player, NavigationOrigin origin) {
        claimGui.open(player, origin);
    }

    /** Opens the claim GUI (legacy — defaults to COMMUNITY_MENU origin) */
    public void openClaim(Player player) {
        claimGui.open(player, NavigationOrigin.COMMUNITY_MENU);
    }

    /** Opens the earnings GUI with origin tracking */
    public void openEarnings(Player player, NavigationOrigin origin) {
        earningsGui.open(player, origin);
    }

    /** Opens the earnings GUI (legacy — defaults to COMMUNITY_MENU origin) */
    public void openEarnings(Player player) {
        earningsGui.open(player, NavigationOrigin.COMMUNITY_MENU);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Utility GUIs
    // ═══════════════════════════════════════════════════════════════

    /** Opens the help GUI */
    public void openHelp(Player player) {
        helpGui.open(player);
    }

    /** Opens the admin panel */
    public void openAdmin(Player player) {
        adminGui.open(player);
    }

    /** Opens a number input GUI */
    public void openNumberInput(Player player, NumberInputGui.NumberInputCallback callback,
                                 double currentValue, double minValue, double maxValue, String title) {
        new NumberInputGui(plugin, this, callback, currentValue, minValue, maxValue, title).open(player);
    }

    /** Opens a percentage input GUI (1–100). */
    public void openPercentInput(Player player, PercentInputGui.PercentInputCallback callback,
                                 int currentValue, String title) {
        new PercentInputGui(plugin, this, callback, currentValue, title).open(player);
    }

    /** Opens a confirmation GUI */
    public void openConfirmation(Player player, ConfirmationGui.ConfirmCallback callback,
                                  String title, String... infoLines) {
        new ConfirmationGui(plugin, this, callback, title, infoLines).open(player);
    }

    // ═══════════════════════════════════════════════════════════════
    //  GUI tracking
    // ═══════════════════════════════════════════════════════════════

    /** Registers an open GUI for a player */
    public void registerGui(UUID playerUuid, MarketGui gui) {
        openGuis.put(playerUuid, gui);
    }

    /** Unregisters a GUI when closed */
    public void unregisterGui(UUID playerUuid) {
        openGuis.remove(playerUuid);
    }

    /** Gets the currently open GUI for a player */
    public MarketGui getOpenGui(UUID playerUuid) {
        return openGuis.get(playerUuid);
    }

    /** Checks if a player has a market GUI open */
    public boolean hasGuiOpen(UUID playerUuid) {
        return openGuis.containsKey(playerUuid);
    }

    /** Closes all open market GUIs (used on plugin disable) */
    public void closeAllGuis() {
        for (UUID uuid : openGuis.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.closeInventory();
            }
        }
        openGuis.clear();
    }

    /** Gets the plugin instance */
    public Market getPlugin() {
        return plugin;
    }

    // Getters for GUI handlers
    public MainMenuGui getMainMenuGui() { return mainMenuGui; }
    public CommunityMenuGui getCommunityMenuGui() { return communityMenuGui; }
    public AuctionsMenuGui getAuctionsMenuGui() { return auctionsMenuGui; }
    public BrowseMarketGui getBrowseMarketGui() { return browseMarketGui; }
    public BrowseAuctionsGui getBrowseAuctionsGui() { return browseAuctionsGui; }
    public CreateListingGui getCreateListingGui() { return createListingGui; }
    public CreateAuctionGui getCreateAuctionGui() { return createAuctionGui; }
    public MyListingsGui getMyListingsGui() { return myListingsGui; }
    public MyAuctionsGui getMyAuctionsGui() { return myAuctionsGui; }
    public ClaimGui getClaimGui() { return claimGui; }
    public EarningsGui getEarningsGui() { return earningsGui; }
    public HelpGui getHelpGui() { return helpGui; }
    public AdminGui getAdminGui() { return adminGui; }
}
