package pt.henrique.market;

import org.bukkit.plugin.java.JavaPlugin;
import pt.henrique.market.command.MarketCommand;
import pt.henrique.market.command.SellCommand;
import pt.henrique.market.config.ConfigManager;
import pt.henrique.market.config.MessageManager;
import pt.henrique.market.db.DatabaseManager;
import pt.henrique.market.economy.EconomyManager;
import pt.henrique.market.gui.GuiManager;
import pt.henrique.market.hook.BuildersDreamHook;
import pt.henrique.market.hook.DeathChestHook;
import pt.henrique.market.hook.LandClaimHook;
import pt.henrique.market.hook.PlayerSettingsHook;
import pt.henrique.market.hook.ToolModsHook;
import pt.henrique.market.listener.GuiListener;
import pt.henrique.market.listener.PlayerListener;
import pt.henrique.market.service.*;
import pt.henrique.market.servershop.config.ShopPriceManager;
import pt.henrique.market.servershop.gui.ShopGuiManager;
import pt.henrique.market.servershop.listener.ChestSellListener;
import pt.henrique.market.servershop.listener.ShopSearchSignHandler;
import pt.henrique.market.servershop.service.ChestSellService;
import pt.henrique.market.servershop.SellVariantScanner;
import pt.henrique.market.servershop.service.EnchantPriceUtil;
import pt.henrique.market.servershop.service.ShopPricingService;
import pt.henrique.market.servershop.service.ShopTransactionService;
import pt.henrique.market.servershop.service.ShulkerSellService;
import pt.henrique.market.task.AuctionTask;
import pt.henrique.market.task.ExpiredListingTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;


/**
 * Main plugin class for Market — a unified marketplace plugin combining
 * Server Shop (server-run buy/sell), Community Market (player listings),
 * and Auctions into one GUI-driven experience.
 */
public final class Market extends JavaPlugin {

    private static Market instance;

    private ConfigManager configManager;
    private MessageManager messageManager;
    private DatabaseManager databaseManager;
    private EconomyManager economyManager;
    private GuiManager guiManager;

    private ListingService listingService;
    private AuctionService auctionService;
    private ClaimService claimService;
    private EarningsService earningsService;
    private TransactionService transactionService;

    private AuctionTask auctionTask;
    private ExpiredListingTask expiredListingTask;

    // Server Shop components
    private ShopPriceManager shopPriceManager;
    private ShopPricingService shopPricingService;
    private ShopTransactionService shopTransactionService;
    private SellVariantScanner sellVariantScanner;
    private ShopGuiManager shopGuiManager;
    private ShopSearchSignHandler shopSearchSignHandler;
    private EnchantPriceUtil enchantPriceUtil;
    private PlayerSettingsHook playerSettingsHook;
    private BuildersDreamHook buildersDreamHook;
    private DeathChestHook deathChestHook;
    private LandClaimHook landClaimHook;
    private ChestSellService chestSellService;
    private ShulkerSellService shulkerSellService;

    @Override
    public void onEnable() {
        instance = this;

        // Migrate data from old plugin name if needed
        migrateOldDataFolder();

        // Load configurations
        configManager = new ConfigManager(this);
        messageManager = new MessageManager(this);

        // Initialize PlayerSettings hook (per-player language)
        playerSettingsHook = new PlayerSettingsHook();
        playerSettingsHook.init(getLogger());
        messageManager.setPlayerSettingsHook(playerSettingsHook);

        // Initialize economy
        economyManager = new EconomyManager(this);
        if (!economyManager.setupEconomy()) {
            getLogger().severe("Disabling Market due to missing economy provider.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize database
        databaseManager = new DatabaseManager(this);
        if (!databaseManager.initialize()) {
            getLogger().severe("Failed to initialize database! Disabling Market...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Database initialized successfully.");

        // Initialize services
        claimService = new ClaimService(this);
        earningsService = new EarningsService(this);
        listingService = new ListingService(this);
        auctionService = new AuctionService(this);
        transactionService = new TransactionService(this);

        // Initialize GUI manager
        guiManager = new GuiManager(this);

        // Register unified /market command (aliases: /m, /shop, /cmarket, /servershop)
        MarketCommand marketCommand = new MarketCommand(this);
        var command = getCommand("market");
        if (command != null) {
            command.setExecutor(marketCommand);
            command.setTabCompleter(marketCommand);
        }

        // Register /sell command (alias: /s)
        SellCommand sellCommand = new SellCommand(this);
        var sellCmd = getCommand("sell");
        if (sellCmd != null) {
            sellCmd.setExecutor(sellCommand);
            sellCmd.setTabCompleter(sellCommand);
        }

        // Register listeners
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        // ==================== Server Shop Setup ====================
        shopPriceManager = new ShopPriceManager(this);
        shopPriceManager.reload();
        enchantPriceUtil = new EnchantPriceUtil(this);
        enchantPriceUtil.reload();
        reloadToolModsHook();
        shopPricingService = new ShopPricingService(this);
        shopTransactionService = new ShopTransactionService(this);
        sellVariantScanner = new SellVariantScanner(this);
        shopGuiManager = new ShopGuiManager(this);
        shopSearchSignHandler = new ShopSearchSignHandler(this);
        getServer().getPluginManager().registerEvents(shopSearchSignHandler, this);

        // Initialize optional hooks (reflection-based, safe if plugins not present)
        buildersDreamHook = new BuildersDreamHook();
        buildersDreamHook.init(getLogger());

        deathChestHook = new DeathChestHook();
        deathChestHook.init(getLogger());

        landClaimHook = new LandClaimHook();
        landClaimHook.init(getLogger());

        // Chest sell service + listener
        chestSellService = new ChestSellService(this);
        shulkerSellService = new ShulkerSellService(this);
        getServer().getPluginManager().registerEvents(new ChestSellListener(this), this);

        // Start tasks
        startTasks();

        getLogger().info("Market has been enabled!");
    }

    @Override
    public void onDisable() {
        if (auctionTask != null) auctionTask.cancel();
        if (expiredListingTask != null) expiredListingTask.cancel();
        if (guiManager != null) guiManager.closeAllGuis();
        if (shopGuiManager != null) shopGuiManager.closeAllGuis();
        if (databaseManager != null) databaseManager.shutdown();
        getLogger().info("Market has been disabled!");
    }

    /**
     * Migrates data from old "CommunityMarket" plugin folder if it exists.
     * This handles upgrades from the old plugin name to the new "Market" name.
     */
    private void migrateOldDataFolder() {
        File pluginsDir = getDataFolder().getParentFile();
        File oldFolder = new File(pluginsDir, "CommunityMarket");
        File newFolder = getDataFolder(); // plugins/Market/

        if (oldFolder.exists() && oldFolder.isDirectory() && !newFolder.exists()) {
            getLogger().info("Detected old CommunityMarket data folder. Migrating to Market...");
            try {
                copyDirectory(oldFolder.toPath(), newFolder.toPath());
                getLogger().info("Migration complete! Old data preserved in plugins/CommunityMarket/");
                getLogger().info("You can safely delete plugins/CommunityMarket/ after verifying everything works.");
            } catch (IOException e) {
                getLogger().warning("Failed to migrate old data folder: " + e.getMessage());
                getLogger().warning("Please manually copy plugins/CommunityMarket/ to plugins/Market/");
            }
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void startTasks() {
        int auctionInterval = configManager.getAuctionCheckInterval() * 20;
        auctionTask = new AuctionTask(this);
        auctionTask.runTaskTimerAsynchronously(this, auctionInterval, auctionInterval);

        int expiredInterval = configManager.getExpiredCheckInterval() * 60 * 20;
        expiredListingTask = new ExpiredListingTask(this);
        expiredListingTask.runTaskTimerAsynchronously(this, expiredInterval, expiredInterval);
    }

    public void reload() {
        configManager.reload();
        messageManager.reload();
        messageManager.setPlayerSettingsHook(playerSettingsHook);
        if (shopPriceManager != null) shopPriceManager.reload();
        if (enchantPriceUtil != null) enchantPriceUtil.reload();
        reloadToolModsHook();
        getLogger().info("Configuration reloaded.");
    }

    /**
     * Reloads the ToolMods hook with current config overrides.
     * Reads the enabled flag and multiplier overrides from config,
     * then passes them to {@link ToolModsHook#reload(boolean, Map)}.
     */
    private void reloadToolModsHook() {
        boolean toolmodsEnabled = configManager.isToolmodsEnabled();
        Map<String, Double> overrides = configManager.getToolmodsMultiplierOverrides();
        ToolModsHook.reload(toolmodsEnabled, overrides);
        if (toolmodsEnabled) {
            getLogger().info("[ToolMods] Integration enabled"
                    + (overrides.isEmpty() ? "." : " with " + overrides.size() + " multiplier override(s)."));
        } else {
            getLogger().info("[ToolMods] Integration disabled via config.");
        }
    }


    public static Market getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public MessageManager getMessageManager() { return messageManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public GuiManager getGuiManager() { return guiManager; }
    public ListingService getListingService() { return listingService; }
    public AuctionService getAuctionService() { return auctionService; }
    public ClaimService getClaimService() { return claimService; }
    public EarningsService getEarningsService() { return earningsService; }
    public TransactionService getTransactionService() { return transactionService; }
    public ShopPriceManager getShopPriceManager() { return shopPriceManager; }
    public ShopPricingService getShopPricingService() { return shopPricingService; }
    public ShopTransactionService getShopTransactionService() { return shopTransactionService; }
    public SellVariantScanner getSellVariantScanner() { return sellVariantScanner; }
    public ShopGuiManager getShopGuiManager() { return shopGuiManager; }
    public ShopSearchSignHandler getShopSearchSignHandler() { return shopSearchSignHandler; }
    public EnchantPriceUtil getEnchantPriceUtil() { return enchantPriceUtil; }
    public PlayerSettingsHook getPlayerSettingsHook() { return playerSettingsHook; }
    public BuildersDreamHook getBuildersDreamHook() { return buildersDreamHook; }
    public DeathChestHook getDeathChestHook() { return deathChestHook; }
    public LandClaimHook getLandClaimHook() { return landClaimHook; }
    public ChestSellService getChestSellService() { return chestSellService; }
    public ShulkerSellService getShulkerSellService() { return shulkerSellService; }
}
