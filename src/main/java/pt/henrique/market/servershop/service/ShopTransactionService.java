package pt.henrique.market.servershop.service;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pt.henrique.market.Market;
import pt.henrique.market.economy.EconomyManager;
import pt.henrique.market.hook.ToolModsHook;
import pt.henrique.market.servershop.model.ShopItem;
import pt.henrique.market.servershop.model.ShopTransaction;
import pt.henrique.market.servershop.model.ShopTransaction.ShopTransactionType;

import java.util.HashMap;

/**
 * Core service that orchestrates server shop buy and sell transactions.
 * Performs all validation, economy operations, and inventory management.
 * All operations are atomic — either fully succeed or fully roll back.
 * <p>
 * <b>Sell price adjustments (Server Market only):</b>
 * <ol>
 *   <li>Base sell price from config (per-item or category default)</li>
 *   <li>Enchantment bonus: configurable % of each enchantment's store buy price, capped per item</li>
 *   <li>ToolMods bonus: per-mod sell bonus from {@link ToolModsHook}</li>
 *   <li>Durability reduction: up to −50% for worn items (Mending exempts, Unbreaking softens)</li>
 * </ol>
 * <p>
 * <b>Daily sell cap:</b> {@code server-shop.daily-sell-cap} limits the net amount a player
 * can earn from all server-shop sell flows per calendar day (0 = disabled). Tracked
 * in-memory on the main thread, seeded from {@code shop_transactions} on first use.
 */
public class ShopTransactionService {

    private final Market plugin;

    public ShopTransactionService(Market plugin) {
        this.plugin = plugin;
    }

    public enum TransactionResult {
        SUCCESS,
        NOT_ENOUGH_MONEY,
        NOT_ENOUGH_ITEMS,
        INVENTORY_FULL,
        ITEM_NOT_BUYABLE,
        ITEM_NOT_SELLABLE,
        ECONOMY_ERROR,
        INVALID_QUANTITY,
        DREAM_ITEM_BLOCKED,
        DAILY_LIMIT_REACHED
    }

    // ── Daily sell cap (main-thread only) ─────────────────────

    /** Calendar day the in-memory counters refer to. */
    private java.time.LocalDate capDay = java.time.LocalDate.now();
    /** Net cents earned from shop sells today, per player. */
    private final HashMap<java.util.UUID, Long> soldTodayCents = new HashMap<>();

    /** The configured daily sell cap in currency units (0 or negative = disabled). */
    public double getDailySellCap() {
        return plugin.getConfig().getDouble("server-shop.daily-sell-cap", 0.0);
    }

    /**
     * Remaining net amount (in cents) the player may still earn from shop sells
     * today. {@link Long#MAX_VALUE} when the cap is disabled.
     */
    public long getRemainingDailyCapCents(Player player) {
        double cap = getDailySellCap();
        if (cap <= 0) return Long.MAX_VALUE;
        rollCapDay();
        long sold = soldTodayCents.computeIfAbsent(player.getUniqueId(),
                uuid -> plugin.getDatabaseManager().getShopSellNetCentsSince(uuid, startOfDayMillis()));
        return Math.max(0, EconomyManager.toCents(cap) - sold);
    }

    /** Records a successful shop sell against the player's daily allowance. */
    public void recordDailySell(Player player, double netAmount) {
        if (getDailySellCap() <= 0) return;
        rollCapDay();
        soldTodayCents.merge(player.getUniqueId(), EconomyManager.toCents(netAmount), Long::sum);
    }

    private void rollCapDay() {
        java.time.LocalDate today = java.time.LocalDate.now();
        if (!today.equals(capDay)) {
            capDay = today;
            soldTodayCents.clear();
        }
    }

    private long startOfDayMillis() {
        return java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli();
    }

    private boolean isDebug() {
        return plugin.getConfig().getBoolean("debug", false);
    }

    private void debug(String message) {
        if (isDebug()) {
            plugin.getLogger().info("[DEBUG-ShopTx] " + message);
        }
    }

    /**
     * Processes a buy transaction: player buys items from the server.
     * Must be called on the main thread (inventory operations).
     */
    public TransactionResult buyItem(Player player, String itemId, int quantity) {
        debug("buyItem() START: player=" + player.getName() + ", itemId=" + itemId + ", qty=" + quantity);

        int maxQty = plugin.getConfig().getInt("server-shop.max-transaction-quantity", 2304);
        if (quantity <= 0 || quantity > maxQty) {
            debug("buyItem() FAIL: invalid quantity " + quantity + " (max=" + maxQty + ")");
            return TransactionResult.INVALID_QUANTITY;
        }

        ShopPricingService pricing = plugin.getShopPricingService();
        if (!pricing.isBuyable(itemId)) {
            debug("buyItem() FAIL: item " + itemId + " is not buyable");
            return TransactionResult.ITEM_NOT_BUYABLE;
        }

        double totalCost = pricing.getTotalBuyPrice(itemId, quantity);
        debug("buyItem() totalCost=" + totalCost + " for " + quantity + "x " + itemId);
        if (totalCost < 0) {
            debug("buyItem() FAIL: totalCost < 0");
            return TransactionResult.ITEM_NOT_BUYABLE;
        }

        if (!plugin.getEconomyManager().has(player.getUniqueId(), totalCost)) {
            debug("buyItem() FAIL: player doesn't have enough money. Required=" + totalCost);
            return TransactionResult.NOT_ENOUGH_MONEY;
        }

        ShopItem shopItem = plugin.getShopPriceManager().getShopItem(itemId);
        if (shopItem == null) {
            debug("buyItem() FAIL: shopItem is null for " + itemId);
            return TransactionResult.ITEM_NOT_BUYABLE;
        }

        boolean dropOnFull = plugin.getConfig().getString("server-shop.full-inventory-action", "drop").equalsIgnoreCase("drop");
        if (!dropOnFull) {
            int emptySlots = countEmptySlots(player);
            int maxStack = shopItem.getBaseMaterial().getMaxStackSize();
            int slotsNeeded = (int) Math.ceil((double) quantity / maxStack);
            if (emptySlots < slotsNeeded) {
                debug("buyItem() FAIL: inventory full (empty=" + emptySlots + ", needed=" + slotsNeeded + ")");
                return TransactionResult.INVENTORY_FULL;
            }
        }

        debug("buyItem() withdrawing " + totalCost + " from player " + player.getName());
        if (!plugin.getEconomyManager().withdraw(player.getUniqueId(), totalCost)) {
            debug("buyItem() FAIL: economy withdraw returned false");
            return TransactionResult.ECONOMY_ERROR;
        }

        giveItems(player, shopItem, quantity);
        debug("buyItem() SUCCESS: gave " + quantity + "x " + itemId + " to " + player.getName());

        // Log transaction
        logTransaction(player, itemId, quantity, totalCost / quantity, totalCost, 0, ShopTransactionType.BUY);

        return TransactionResult.SUCCESS;
    }

    /**
     * Processes a sell transaction: player sells items to the server.
     * Must be called on the main thread (inventory operations).
     * <p>
     * The sell price includes enchantment bonuses and durability adjustments.
     * For items sold from inventory, the <em>first matching stack</em> is used
     * for durability/enchant calculations (all units valued at the same rate).
     */
    public TransactionResult sellItem(Player player, String itemId, int quantity) {
        debug("sellItem() START: player=" + player.getName() + ", itemId=" + itemId + ", qty=" + quantity);

        int maxQty = plugin.getConfig().getInt("server-shop.max-transaction-quantity", 2304);
        if (quantity <= 0 || quantity > maxQty) {
            debug("sellItem() FAIL: invalid quantity " + quantity + " (max=" + maxQty + ")");
            return TransactionResult.INVALID_QUANTITY;
        }

        ShopPricingService pricing = plugin.getShopPricingService();
        if (!pricing.isSellable(itemId)) {
            debug("sellItem() FAIL: item " + itemId + " is not sellable");
            return TransactionResult.ITEM_NOT_SELLABLE;
        }

        double baseSellPrice = plugin.getShopPriceManager().getSellPrice(itemId);
        debug("sellItem() baseSellPrice=" + baseSellPrice + " for " + itemId);
        if (baseSellPrice < 0) {
            debug("sellItem() FAIL: baseSellPrice < 0");
            return TransactionResult.ITEM_NOT_SELLABLE;
        }

        ShopItem shopItem = plugin.getShopPriceManager().getShopItem(itemId);
        if (shopItem == null) {
            debug("sellItem() FAIL: shopItem is null for " + itemId);
            return TransactionResult.ITEM_NOT_SELLABLE;
        }

        int available = countItems(player, shopItem);
        debug("sellItem() available=" + available + " items in inventory");
        if (available < quantity) {
            debug("sellItem() FAIL: not enough items (have=" + available + ", need=" + quantity + ")");
            return TransactionResult.NOT_ENOUGH_ITEMS;
        }

        // Compute per-unit sell price with enchant/durability adjustments
        // Use the first matching stack for durability/enchant info
        ItemStack representativeStack = findFirstMatchingStack(player, shopItem);
        debug("sellItem() representativeStack=" + (representativeStack != null ? representativeStack.getType() + " x" + representativeStack.getAmount() : "null"));

        // BuildersDream check — reject creative dream items
        if (representativeStack != null
                && plugin.getBuildersDreamHook() != null
                && plugin.getBuildersDreamHook().isDreamItem(representativeStack)) {
            debug("sellItem() FAIL: item is a BuildersDream dream item");
            return TransactionResult.DREAM_ITEM_BLOCKED;
        }

        double adjustedPricePerUnit = computeAdjustedSellPrice(baseSellPrice, representativeStack);
        double totalSellPrice = Math.round(adjustedPricePerUnit * quantity * 100.0) / 100.0;
        debug("sellItem() adjustedPricePerUnit=" + adjustedPricePerUnit + ", totalSellPrice=" + totalSellPrice);

        double tax = pricing.calculateTax(totalSellPrice);
        double netAmount = totalSellPrice - tax;
        debug("sellItem() tax=" + tax + ", netAmount=" + netAmount);

        // Daily sell cap — checked before any inventory mutation
        if (EconomyManager.toCents(netAmount) > getRemainingDailyCapCents(player)) {
            debug("sellItem() FAIL: daily sell cap reached for " + player.getName());
            return TransactionResult.DAILY_LIMIT_REACHED;
        }

        // Remove items first (prevent dupes)
        if (!removeItems(player, shopItem, quantity)) {
            debug("sellItem() FAIL: removeItems returned false");
            return TransactionResult.NOT_ENOUGH_ITEMS;
        }

        debug("sellItem() depositing " + netAmount + " to player " + player.getName());
        if (!plugin.getEconomyManager().deposit(player.getUniqueId(), netAmount)) {
            debug("sellItem() FAIL: economy deposit returned false — REFUNDING items");
            giveItems(player, shopItem, quantity); // Refund
            return TransactionResult.ECONOMY_ERROR;
        }

        debug("sellItem() SUCCESS: sold " + quantity + "x " + itemId + " for net " + netAmount);
        recordDailySell(player, netAmount);
        logTransaction(player, itemId, quantity, adjustedPricePerUnit, totalSellPrice, tax, ShopTransactionType.SELL);

        return TransactionResult.SUCCESS;
    }

    /**
     * Computes the adjusted sell price per unit, applying enchantment bonus
     * and durability reduction.
     *
     * @param baseSellPrice the configured base sell price (double)
     * @param stack         a representative ItemStack (for enchant/durability info), may be null
     * @return adjusted sell price per unit (double)
     */
    public double computeAdjustedSellPrice(double baseSellPrice, ItemStack stack) {
        if (stack == null) return baseSellPrice;

        long baseCents = EconomyManager.toCents(baseSellPrice);

        // Enchantment bonus: +25% of each enchantment's store buy price
        long enchantBonus = plugin.getEnchantPriceUtil().getEnchantBonusCents(stack);

        // ToolMods bonus: per-mod sell bonus
        long modBonus = ToolModsHook.getModBonusCents(stack);

        long totalBeforeWear = baseCents + enchantBonus + modBonus;

        // Durability reduction (applies to total including enchant + mod bonus)
        // Mending exempts from durability penalty entirely
        long finalCents = DurabilityPriceUtil.applyDurability(totalBeforeWear, stack);

        return EconomyManager.toDouble(finalCents);
    }

    /**
     * Computes a detailed sell price breakdown for GUI display.
     *
     * @param baseSellPrice base configured sell price (double)
     * @param stack         the item being sold
     * @return breakdown record with all components
     */
    public SellPriceBreakdown computeSellBreakdown(double baseSellPrice, ItemStack stack) {
        long baseCents = EconomyManager.toCents(baseSellPrice);
        long enchantBonus = (stack != null) ? plugin.getEnchantPriceUtil().getEnchantBonusCents(stack) : 0;
        long modBonus = (stack != null) ? ToolModsHook.getModBonusCents(stack) : 0;
        long totalBeforeWear = baseCents + enchantBonus + modBonus;
        double durabilityMultiplier = (stack != null) ? DurabilityPriceUtil.getDurabilityMultiplier(stack) : 1.0;
        String wearDescription = (stack != null) ? DurabilityPriceUtil.getWearDescription(stack) : "N/A";

        long finalCents = DurabilityPriceUtil.applyDurability(totalBeforeWear, stack);
        long wearPenalty = totalBeforeWear - finalCents;

        return new SellPriceBreakdown(
                EconomyManager.toDouble(baseCents),
                EconomyManager.toDouble(enchantBonus),
                EconomyManager.toDouble(modBonus),
                wearDescription,
                EconomyManager.toDouble(wearPenalty),
                durabilityMultiplier,
                EconomyManager.toDouble(finalCents)
        );
    }

    /**
     * Breakdown record for sell price display in GUIs.
     */
    public record SellPriceBreakdown(
            double baseSell,
            double enchantBonus,
            double modBonus,
            String wearDescription,
            double wearPenalty,
            double durabilityMultiplier,
            double finalSell
    ) {}

    /**
     * Finds the first matching ItemStack in the player's inventory for a ShopItem.
     * Used to get a representative stack for durability/enchant pricing.
     */
    public ItemStack findFirstMatchingStack(Player player, ShopItem shopItem) {
        boolean materialOnly = isMaterialOnlyMode();
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null) continue;
            boolean match = materialOnly ? shopItem.matchesMaterialOnly(stack) : shopItem.matches(stack);
            if (match) return stack;
        }
        return null;
    }

    /**
     * Finds a specific ItemStack in the player's inventory for a given slot index.
     */
    public ItemStack getStackAtSlot(Player player, int slot) {
        if (slot < 0 || slot >= player.getInventory().getSize()) return null;
        return player.getInventory().getItem(slot);
    }

    /**
     * Logs a completed sell transaction to the database. Public entry point for
     * sell flows that don't go through {@link #sellItem} (e.g. shulker box sales),
     * keeping DB logging consistent.
     */
    public void logShopSale(Player player, String itemId, int quantity,
                            double pricePerUnit, double totalPrice, double tax) {
        logTransaction(player, itemId, quantity, pricePerUnit, totalPrice, tax, ShopTransactionType.SELL);
    }

    private void logTransaction(Player player, String itemId, int quantity,
                                 double pricePerUnit, double totalPrice, double tax,
                                 ShopTransactionType type) {
        ShopTransaction record = ShopTransaction.create(
                player.getUniqueId(), player.getName(), itemId,
                quantity, pricePerUnit, totalPrice, tax, type);
        plugin.getDatabaseManager().logShopTransaction(record);
    }

    private void giveItems(Player player, ShopItem shopItem, int quantity) {
        int maxStack = shopItem.getBaseMaterial().getMaxStackSize();
        int remaining = quantity;
        while (remaining > 0) {
            int amount = Math.min(remaining, maxStack);
            ItemStack item = shopItem.createItemStack(amount);
            HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            for (ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            remaining -= amount;
        }
    }

    private boolean removeItems(Player player, ShopItem shopItem, int quantity) {
        int remaining = quantity;
        boolean materialOnly = isMaterialOnlyMode();
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null) continue;
            boolean match = materialOnly ? shopItem.matchesMaterialOnly(stack) : shopItem.matches(stack);
            if (!match) continue;

            if (stack.getAmount() <= remaining) {
                remaining -= stack.getAmount();
                player.getInventory().setItem(i, null);
            } else {
                stack.setAmount(stack.getAmount() - remaining);
                remaining = 0;
            }
        }
        return remaining == 0;
    }

    /** Counts items matching a specific ShopItem in the player's inventory. */
    public int countItems(Player player, ShopItem shopItem) {
        int count = 0;
        boolean materialOnly = isMaterialOnlyMode();
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null) continue;
            boolean match = materialOnly ? shopItem.matchesMaterialOnly(stack) : shopItem.matches(stack);
            if (!match) continue;
            count += stack.getAmount();
        }
        return count;
    }

    /** Counts items matching a specific item ID in the player's inventory. */
    public int countItems(Player player, String itemId) {
        ShopItem shopItem = plugin.getShopPriceManager().getShopItem(itemId);
        if (shopItem == null) return 0;
        return countItems(player, shopItem);
    }

    private int countEmptySlots(Player player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType().isAir()) count++;
        }
        return count;
    }

    public int getMaxBuyQuantity(Player player, String itemId) {
        double buyPrice = plugin.getShopPriceManager().getBuyPrice(itemId);
        if (buyPrice <= 0) return 0;
        double balance = plugin.getEconomyManager().getBalance(player.getUniqueId());
        int maxByMoney = (int) (balance / buyPrice);
        int maxQty = plugin.getConfig().getInt("server-shop.max-transaction-quantity", 2304);
        return Math.min(maxByMoney, maxQty);
    }

    public int getMaxSellQuantity(Player player, String itemId) {
        int available = countItems(player, itemId);
        int maxQty = plugin.getConfig().getInt("server-shop.max-transaction-quantity", 2304);
        return Math.min(available, maxQty);
    }

    /**
     * Returns true if the match mode is MATERIAL_ONLY (default).
     * In MATERIAL_ONLY mode, any item of the same material counts for sell,
     * regardless of components/meta.
     */
    private boolean isMaterialOnlyMode() {
        return "MATERIAL_ONLY".equalsIgnoreCase(plugin.getShopPriceManager().getMatchMode());
    }
}
