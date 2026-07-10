package pt.henrique.market.servershop.service;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import pt.henrique.market.Market;
import pt.henrique.market.hook.BuildersDreamHook;
import pt.henrique.market.hook.DeathChestHook;
import pt.henrique.market.hook.LandClaimHook;
import pt.henrique.market.servershop.gui.SellChestPreviewGui;
import pt.henrique.market.servershop.model.ShopItem;
import pt.henrique.market.servershop.model.ShopPriceEntry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages "Sell Chest" sessions.
 * <p>
 * Flow:
 * <ol>
 *   <li>Player clicks "Sell Chest" button → {@link #startSession(Player)}</li>
 *   <li>A 15-second BossBar timer counts down</li>
 *   <li>Player right-clicks a container → {@link #onContainerClick(Player, Block)}</li>
 *   <li>Plugin scans the container for sellable items</li>
 *   <li>Preview GUI opens → player confirms → items sold, money deposited</li>
 * </ol>
 */
public class ChestSellService {

    private final Market plugin;
    private final Map<UUID, ChestSellSession> activeSessions = new ConcurrentHashMap<>();

    private static final int SESSION_TIMEOUT_SECONDS = 15;

    public ChestSellService(Market plugin) {
        this.plugin = plugin;
    }

    // ══════════════════════════════════════════════════════════════
    //  Session lifecycle
    // ══════════════════════════════════════════════════════════════

    /**
     * Starts a chest sell session for the given player.
     * Shows a BossBar timer and waits for the player to click a container.
     */
    public void startSession(Player player) {
        UUID uuid = player.getUniqueId();

        // Cancel any existing session first
        if (activeSessions.containsKey(uuid)) {
            cancelSession(player, "servershop.messages.sell-chest-cancelled");
        }

        // Close any open inventory so player can click blocks
        player.closeInventory();

        // Create BossBar
        BossBar bossBar = BossBar.bossBar(
                Component.text("Click a chest to sell — " + SESSION_TIMEOUT_SECONDS + "s remaining"),
                1.0f,
                BossBar.Color.YELLOW,
                BossBar.Overlay.PROGRESS
        );
        player.showBossBar(bossBar);

        Location startLocation = player.getLocation().clone();
        ChestSellSession session = new ChestSellSession(uuid, bossBar, startLocation);
        activeSessions.put(uuid, session);

        // Send start message
        player.sendMessage(plugin.getMessageManager().getPrefixedP(player, "servershop.messages.sell-chest-start"));

        // Tick task: update BossBar every second, cancel after timeout
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            ChestSellSession s = activeSessions.get(uuid);
            if (s == null) return;

            s.tick();
            if (s.isExpired()) {
                cancelSession(player, "servershop.messages.sell-chest-timeout");
                return;
            }
            float progress = (float) s.getRemainingSeconds() / (float) SESSION_TIMEOUT_SECONDS;
            bossBar.progress(Math.max(0f, Math.min(1f, progress)));
            bossBar.name(Component.text("Click a chest to sell — " + s.getRemainingSeconds() + "s remaining"));
        }, 0L, 20L);

        session.setTask(task);
    }

    /**
     * Called when the player right-clicks a block while in an active session.
     */
    public void onContainerClick(Player player, Block block) {
        ChestSellSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        Material type = block.getType();

        // Validate container type
        if (!isValidSellContainer(type)) {
            player.sendMessage(plugin.getMessageManager().getPrefixedP(player,
                    "servershop.messages.sell-chest-invalid-container"));
            return;
        }

        // LandClaim check — skip for Ender Chests (they're virtual, not tied to claims)
        if (type != Material.ENDER_CHEST) {
            LandClaimHook landClaimHook = plugin.getLandClaimHook();
            if (landClaimHook != null && landClaimHook.isAvailable()) {
                if (!landClaimHook.canBuild(player, block)) {
                    player.sendMessage(plugin.getMessageManager().getPrefixedP(player,
                            "servershop.messages.sell-chest-not-your-claim"));
                    return;
                }
            }
        }

        // DeathChest check — block selling from death chests
        if (type != Material.ENDER_CHEST) {
            DeathChestHook deathChestHook = plugin.getDeathChestHook();
            if (deathChestHook != null && deathChestHook.isAvailable()) {
                if (deathChestHook.isDeathChest(block.getLocation())) {
                    player.sendMessage(plugin.getMessageManager().getPrefixedP(player,
                            "servershop.messages.sell-chest-is-deathchest"));
                    endSession(player);
                    return;
                }
            }
        }

        // Get the container inventory
        Inventory containerInventory;
        if (type == Material.ENDER_CHEST) {
            containerInventory = player.getEnderChest();
        } else {
            BlockState state = block.getState();
            if (state instanceof Container container) {
                containerInventory = container.getInventory();
            } else {
                return;
            }
        }

        // Cancel the BossBar timer
        endSession(player);

        // Scan inventory for sellable items
        List<SellPreviewEntry> sellableItems = scanForSellableItems(containerInventory, player);

        if (sellableItems.isEmpty()) {
            player.sendMessage(plugin.getMessageManager().getPrefixedP(player,
                    "servershop.messages.sell-chest-nothing-to-sell"));
            return;
        }

        // Open preview GUI
        new SellChestPreviewGui(plugin, sellableItems, containerInventory, block.getLocation())
                .open(player);
    }

    // ══════════════════════════════════════════════════════════════
    //  Scanning
    // ══════════════════════════════════════════════════════════════

    /**
     * Scans a container for items that can be sold in the server shop.
     * Excludes: dream items (BuildersDream), items with no sell price.
     */
    private List<SellPreviewEntry> scanForSellableItems(Inventory inventory, Player player) {
        List<SellPreviewEntry> entries = new ArrayList<>();
        BuildersDreamHook dreamHook = plugin.getBuildersDreamHook();

        // Aggregate by item ID for cleaner display
        Map<String, SellPreviewEntry> aggregated = new LinkedHashMap<>();

        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType().isAir()) continue;

            // Check for BuildersDream tag — reject dream items
            if (dreamHook != null && dreamHook.isDreamItem(item)) continue;

            // Identify item and check if it has a sell price
            String itemId = plugin.getShopPriceManager().identifyItem(item);
            if (itemId == null) continue;

            ShopPriceEntry priceEntry = plugin.getShopPriceManager().getPriceEntry(itemId);
            if (priceEntry == null || !priceEntry.sellEnabled() || priceEntry.sellPrice() <= 0) continue;

            // Compute adjusted sell price per unit (includes enchant, mod, durability)
            double adjustedPrice = plugin.getShopTransactionService()
                    .computeAdjustedSellPrice(priceEntry.sellPrice(), item);
            if (adjustedPrice <= 0) continue;

            ShopItem shopItem = plugin.getShopPriceManager().getShopItem(itemId);
            String displayName = shopItem != null ? shopItem.getDisplayName() : itemId;

            // Aggregate by item ID
            SellPreviewEntry existing = aggregated.get(itemId);
            if (existing != null) {
                // Add to existing quantity — keep the same per-unit price
                aggregated.put(itemId, new SellPreviewEntry(
                        existing.displayItem(), itemId, displayName,
                        existing.pricePerUnit(),
                        existing.quantity() + item.getAmount()));
            } else {
                aggregated.put(itemId, new SellPreviewEntry(
                        item.clone(), itemId, displayName,
                        adjustedPrice, item.getAmount()));
            }
        }

        entries.addAll(aggregated.values());
        return entries;
    }

    // ══════════════════════════════════════════════════════════════
    //  Session management
    // ══════════════════════════════════════════════════════════════

    public boolean hasActiveSession(UUID uuid) {
        return activeSessions.containsKey(uuid);
    }

    /**
     * Cancels a session with a message.
     */
    public void cancelSession(Player player, String messageKey) {
        endSession(player);
        if (messageKey != null) {
            player.sendMessage(plugin.getMessageManager().getPrefixedP(player, messageKey));
        }
    }

    /**
     * Silently ends a session (removes BossBar, cancels task).
     */
    private void endSession(Player player) {
        ChestSellSession session = activeSessions.remove(player.getUniqueId());
        if (session != null) {
            if (session.getTask() != null) session.getTask().cancel();
            player.hideBossBar(session.getBossBar());
        }
    }

    /**
     * Checks if a player has moved too far from their session start location.
     *
     * @return true if the player moved more than 5 blocks
     */
    public boolean hasMovedTooFar(Player player) {
        ChestSellSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return false;
        Location start = session.getStartLocation();
        Location current = player.getLocation();
        if (!Objects.equals(start.getWorld(), current.getWorld())) return true;
        return start.distanceSquared(current) > 25; // 5 blocks squared
    }

    /**
     * Cleans up session when player disconnects.
     */
    public void handleQuit(UUID uuid) {
        ChestSellSession session = activeSessions.remove(uuid);
        if (session != null) {
            if (session.getTask() != null) session.getTask().cancel();
            // BossBar is auto-removed when player disconnects
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════════

    private boolean isValidSellContainer(Material type) {
        return type == Material.CHEST || type == Material.TRAPPED_CHEST
                || type == Material.BARREL || type == Material.ENDER_CHEST
                || type.name().endsWith("SHULKER_BOX");
    }

    // ══════════════════════════════════════════════════════════════
    //  Inner types
    // ══════════════════════════════════════════════════════════════

    /**
     * Represents a pending chest sell session.
     */
    private static class ChestSellSession {
        private final UUID playerUuid;
        private final BossBar bossBar;
        private final Location startLocation;
        private BukkitTask task;
        private int elapsedSeconds = 0;

        ChestSellSession(UUID playerUuid, BossBar bossBar, Location startLocation) {
            this.playerUuid = playerUuid;
            this.bossBar = bossBar;
            this.startLocation = startLocation;
        }

        void tick() { elapsedSeconds++; }
        boolean isExpired() { return elapsedSeconds >= SESSION_TIMEOUT_SECONDS; }
        int getRemainingSeconds() { return Math.max(0, SESSION_TIMEOUT_SECONDS - elapsedSeconds); }

        BossBar getBossBar() { return bossBar; }
        Location getStartLocation() { return startLocation; }
        BukkitTask getTask() { return task; }
        void setTask(BukkitTask task) { this.task = task; }
    }

    /**
     * An entry in the sell preview showing one item type and its value.
     */
    public record SellPreviewEntry(
            ItemStack displayItem,
            String itemId,
            String displayName,
            double pricePerUnit,
            int quantity
    ) {
        public double totalValue() {
            return Math.round(pricePerUnit * quantity * 100.0) / 100.0;
        }
    }
}


