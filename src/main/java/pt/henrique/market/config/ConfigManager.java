package pt.henrique.market.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import pt.henrique.market.Market;

import java.util.*;

/**
 * Manages the main plugin configuration
 */
public class ConfigManager {

    private final Market plugin;
    private FileConfiguration config;

    // Database settings
    private String databaseType;
    private String sqliteFile;
    private String mysqlHost;
    private int mysqlPort;
    private String mysqlDatabase;
    private String mysqlUsername;
    private String mysqlPassword;
    private int poolMaxSize;
    private int poolMinIdle;
    private long poolConnectionTimeout;
    private long poolIdleTimeout;
    private long poolMaxLifetime;

    // Economy settings
    private String currencyFormat;
    private String currencySymbol;
    private double marketTax;
    private double auctionTax;

    // Market settings
    private int maxListingsPerPlayer;
    private int listingCooldown;
    private int defaultDurationHours;
    private List<Integer> availableDurations;
    private double minPrice;
    private double maxPrice;

    // Auction settings
    private int maxAuctionsPerPlayer;
    private int minDurationHours;
    private int maxDurationHours;
    private int defaultAuctionDurationHours;
    private List<Integer> availableAuctionDurations;
    private double minStartPrice;
    private double maxStartPrice;
    private double minBidIncrementPercent;
    private double minBidIncrementAbsolute;
    private boolean antiSnipeEnabled;
    private int antiSnipeTriggerSeconds;
    private int antiSnipeExtensionSeconds;
    private int antiSnipeMaxExtensions;

    // Blacklist
    private Set<Material> blacklistedMaterials;
    private List<String> blacklistedKeywords;

    // GUI settings
    private String mainMenuTitle;
    private String browseMarketTitle;
    private String browseAuctionsTitle;
    private String createListingTitle;
    private String createAuctionTitle;
    private String myListingsTitle;
    private String myAuctionsTitle;
    private String claimTitle;
    private String earningsTitle;
    private String confirmTitle;
    private String numberInputTitle;
    private String adminTitle;
    private int itemsPerPage;
    private boolean helpButtonEnabled;
    private String clickSound;
    private String successSound;
    private String errorSound;
    private String purchaseSound;

    // Notifications
    private boolean notifyOnSale;
    private boolean notifyOnOutbid;
    private boolean notifyOnWin;
    private boolean notifyOnExpire;

    // Performance
    private int cacheDuration;
    private int auctionCheckInterval;
    private int expiredCheckInterval;

    // ToolMods integration
    private boolean toolmodsEnabled;
    private Map<String, Double> toolmodsMultiplierOverrides;

    public ConfigManager(Market plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
        loadSettings();
    }

    private void loadSettings() {
        // Database settings
        databaseType = config.getString("database.type", "sqlite");
        sqliteFile = config.getString("database.sqlite.file", "database.db");
        mysqlHost = config.getString("database.mysql.host", "localhost");
        mysqlPort = config.getInt("database.mysql.port", 3306);
        mysqlDatabase = config.getString("database.mysql.database", "communitymarket");
        mysqlUsername = config.getString("database.mysql.username", "root");
        mysqlPassword = config.getString("database.mysql.password", "");
        poolMaxSize = config.getInt("database.mysql.pool.maximum-pool-size", 10);
        poolMinIdle = config.getInt("database.mysql.pool.minimum-idle", 2);
        poolConnectionTimeout = config.getLong("database.mysql.pool.connection-timeout", 30000);
        poolIdleTimeout = config.getLong("database.mysql.pool.idle-timeout", 600000);
        poolMaxLifetime = config.getLong("database.mysql.pool.max-lifetime", 1800000);

        // Economy settings
        currencyFormat = config.getString("economy.currency-format", "$#,##0.00");
        currencySymbol = config.getString("economy.currency-symbol", "$");
        marketTax = config.getDouble("economy.taxes.market-tax", 5.0);
        auctionTax = config.getDouble("economy.taxes.auction-tax", 7.5);

        // Market settings
        maxListingsPerPlayer = config.getInt("market.max-listings-per-player", 20);
        listingCooldown = config.getInt("market.listing-cooldown", 0);
        defaultDurationHours = config.getInt("market.default-duration-hours", 168);
        availableDurations = config.getIntegerList("market.available-durations");
        minPrice = config.getDouble("market.min-price", 1.0);
        maxPrice = config.getDouble("market.max-price", 1000000000.0);

        // Auction settings
        maxAuctionsPerPlayer = config.getInt("auction.max-auctions-per-player", 10);
        minDurationHours = config.getInt("auction.min-duration-hours", 1);
        maxDurationHours = config.getInt("auction.max-duration-hours", 168);
        defaultAuctionDurationHours = config.getInt("auction.default-duration-hours", 24);
        availableAuctionDurations = config.getIntegerList("auction.available-durations");
        minStartPrice = config.getDouble("auction.min-start-price", 1.0);
        maxStartPrice = config.getDouble("auction.max-start-price", 1000000000.0);
        minBidIncrementPercent = config.getDouble("auction.min-bid-increment-percent", 5.0);
        minBidIncrementAbsolute = config.getDouble("auction.min-bid-increment-absolute", 1.0);
        antiSnipeEnabled = config.getBoolean("auction.anti-snipe.enabled", true);
        antiSnipeTriggerSeconds = config.getInt("auction.anti-snipe.trigger-seconds", 30);
        antiSnipeExtensionSeconds = config.getInt("auction.anti-snipe.extension-seconds", 30);
        antiSnipeMaxExtensions = config.getInt("auction.anti-snipe.max-extensions", 10);

        // Blacklist
        blacklistedMaterials = new HashSet<>();
        for (String materialName : config.getStringList("blacklist.materials")) {
            try {
                Material material = Material.valueOf(materialName.toUpperCase());
                blacklistedMaterials.add(material);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material in blacklist: " + materialName);
            }
        }
        blacklistedKeywords = config.getStringList("blacklist.keywords");

        // GUI settings
        mainMenuTitle = config.getString("gui.main-menu-title", "&8&lCommunity Market");
        browseMarketTitle = config.getString("gui.browse-market-title", "&8&lBrowse Market");
        browseAuctionsTitle = config.getString("gui.browse-auctions-title", "&8&lBrowse Auctions");
        createListingTitle = config.getString("gui.create-listing-title", "&8&lCreate Listing");
        createAuctionTitle = config.getString("gui.create-auction-title", "&8&lCreate Auction");
        myListingsTitle = config.getString("gui.my-listings-title", "&8&lMy Listings");
        myAuctionsTitle = config.getString("gui.my-auctions-title", "&8&lMy Auctions");
        claimTitle = config.getString("gui.claim-title", "&8&lClaim Items");
        earningsTitle = config.getString("gui.earnings-title", "&8&lEarnings");
        confirmTitle = config.getString("gui.confirm-title", "&8&lConfirm Action");
        numberInputTitle = config.getString("gui.number-input-title", "&8&lEnter Amount");
        adminTitle = config.getString("gui.admin-title", "&8&lAdmin Panel");
        itemsPerPage = config.getInt("gui.items-per-page", 45);
        helpButtonEnabled = config.getBoolean("gui.show-help-button", true);
        clickSound = config.getString("gui.sounds.click", "UI_BUTTON_CLICK");
        successSound = config.getString("gui.sounds.success", "ENTITY_PLAYER_LEVELUP");
        errorSound = config.getString("gui.sounds.error", "ENTITY_VILLAGER_NO");
        purchaseSound = config.getString("gui.sounds.purchase", "ENTITY_EXPERIENCE_ORB_PICKUP");

        // Notifications
        notifyOnSale = config.getBoolean("notifications.notify-on-sale", true);
        notifyOnOutbid = config.getBoolean("notifications.notify-on-outbid", true);
        notifyOnWin = config.getBoolean("notifications.notify-on-win", true);
        notifyOnExpire = config.getBoolean("notifications.notify-on-expire", true);

        // Performance
        cacheDuration = config.getInt("performance.cache-duration", 30);
        auctionCheckInterval = config.getInt("performance.auction-check-interval", 5);
        expiredCheckInterval = config.getInt("performance.expired-check-interval", 5);

        // ToolMods integration
        toolmodsEnabled = config.getBoolean("toolmods.enabled", true);
        toolmodsMultiplierOverrides = new HashMap<>();
        ConfigurationSection overridesSection = config.getConfigurationSection("toolmods.sell-multiplier-overrides");
        if (overridesSection != null) {
            for (String modKey : overridesSection.getKeys(false)) {
                double multiplier = overridesSection.getDouble(modKey, -1);
                if (multiplier >= 0 && multiplier <= 1.0) {
                    toolmodsMultiplierOverrides.put(modKey, multiplier);
                } else {
                    plugin.getLogger().warning("[ToolMods] Invalid multiplier for '" + modKey
                            + "': " + multiplier + " (must be 0.0–1.0). Skipped.");
                }
            }
            if (!toolmodsMultiplierOverrides.isEmpty()) {
                plugin.getLogger().info("[ToolMods] Loaded " + toolmodsMultiplierOverrides.size()
                        + " sell-multiplier override(s).");
            }
        }
    }

    // Getters
    public String getDatabaseType() { return databaseType; }
    public String getSqliteFile() { return sqliteFile; }
    public String getMysqlHost() { return mysqlHost; }
    public int getMysqlPort() { return mysqlPort; }
    public String getMysqlDatabase() { return mysqlDatabase; }
    public String getMysqlUsername() { return mysqlUsername; }
    public String getMysqlPassword() { return mysqlPassword; }
    public int getPoolMaxSize() { return poolMaxSize; }
    public int getPoolMinIdle() { return poolMinIdle; }
    public long getPoolConnectionTimeout() { return poolConnectionTimeout; }
    public long getPoolIdleTimeout() { return poolIdleTimeout; }
    public long getPoolMaxLifetime() { return poolMaxLifetime; }

    public String getCurrencyFormat() { return currencyFormat; }
    public String getCurrencySymbol() { return currencySymbol; }
    public double getMarketTax() { return marketTax; }
    public double getAuctionTax() { return auctionTax; }

    public int getMaxListingsPerPlayer() { return maxListingsPerPlayer; }
    public int getListingCooldown() { return listingCooldown; }
    public int getDefaultDurationHours() { return defaultDurationHours; }
    public List<Integer> getAvailableDurations() { return availableDurations; }
    public double getMinPrice() { return minPrice; }
    public double getMaxPrice() { return maxPrice; }

    public int getMaxAuctionsPerPlayer() { return maxAuctionsPerPlayer; }
    public int getMinDurationHours() { return minDurationHours; }
    public int getMaxDurationHours() { return maxDurationHours; }
    public int getDefaultAuctionDurationHours() { return defaultAuctionDurationHours; }
    public List<Integer> getAvailableAuctionDurations() { return availableAuctionDurations; }
    public double getMinStartPrice() { return minStartPrice; }
    public double getMaxStartPrice() { return maxStartPrice; }
    public double getMinBidIncrementPercent() { return minBidIncrementPercent; }
    public double getMinBidIncrementAbsolute() { return minBidIncrementAbsolute; }
    public boolean isAntiSnipeEnabled() { return antiSnipeEnabled; }
    public int getAntiSnipeTriggerSeconds() { return antiSnipeTriggerSeconds; }
    public int getAntiSnipeExtensionSeconds() { return antiSnipeExtensionSeconds; }
    public int getAntiSnipeMaxExtensions() { return antiSnipeMaxExtensions; }

    public Set<Material> getBlacklistedMaterials() { return blacklistedMaterials; }
    public List<String> getBlacklistedKeywords() { return blacklistedKeywords; }

    public String getMainMenuTitle() { return mainMenuTitle; }
    public String getBrowseMarketTitle() { return browseMarketTitle; }
    public String getBrowseAuctionsTitle() { return browseAuctionsTitle; }
    public String getCreateListingTitle() { return createListingTitle; }
    public String getCreateAuctionTitle() { return createAuctionTitle; }
    public String getMyListingsTitle() { return myListingsTitle; }
    public String getMyAuctionsTitle() { return myAuctionsTitle; }
    public String getClaimTitle() { return claimTitle; }
    public String getEarningsTitle() { return earningsTitle; }
    public String getConfirmTitle() { return confirmTitle; }
    public String getNumberInputTitle() { return numberInputTitle; }
    public String getAdminTitle() { return adminTitle; }
    public int getItemsPerPage() { return itemsPerPage; }
    public boolean isHelpButtonEnabled() { return helpButtonEnabled; }
    public String getClickSound() { return clickSound; }
    public String getSuccessSound() { return successSound; }
    public String getErrorSound() { return errorSound; }
    public String getPurchaseSound() { return purchaseSound; }

    public boolean isNotifyOnSale() { return notifyOnSale; }
    public boolean isNotifyOnOutbid() { return notifyOnOutbid; }
    public boolean isNotifyOnWin() { return notifyOnWin; }
    public boolean isNotifyOnExpire() { return notifyOnExpire; }

    public int getCacheDuration() { return cacheDuration; }
    public int getAuctionCheckInterval() { return auctionCheckInterval; }
    public int getExpiredCheckInterval() { return expiredCheckInterval; }

    public boolean isToolmodsEnabled() { return toolmodsEnabled; }
    public Map<String, Double> getToolmodsMultiplierOverrides() { return toolmodsMultiplierOverrides; }

    /**
     * Checks if a material is blacklisted
     */
    public boolean isMaterialBlacklisted(Material material) {
        return blacklistedMaterials.contains(material);
    }

    /**
     * Checks if text contains blacklisted keywords
     */
    public boolean containsBlacklistedKeyword(String text) {
        if (text == null) return false;
        String lowerText = text.toLowerCase();
        for (String keyword : blacklistedKeywords) {
            if (lowerText.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}

