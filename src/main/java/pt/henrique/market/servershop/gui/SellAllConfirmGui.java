package pt.henrique.market.servershop.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import pt.henrique.market.Market;
import pt.henrique.market.config.MessageManager;
import pt.henrique.market.gui.MarketGui;
import pt.henrique.market.servershop.ShulkerUtil;
import pt.henrique.market.servershop.model.ShopItem;
import pt.henrique.market.servershop.service.ShopTransactionService;
import pt.henrique.market.servershop.service.ShopTransactionService.SellPriceBreakdown;
import pt.henrique.market.util.ItemBuilder;
import pt.henrique.market.util.SoundUtil;
import pt.henrique.market.util.TextUtil;

import java.util.*;

/**
 * Confirmation GUI for the "Sell Entire Inventory" action.
 *
 * <p>Scans the player's inventory, groups sellable items, shows a preview grid
 * with per-item breakdown, and requires a confirm click to execute.</p>
 *
 * <p>Layout (54 slots / 6 rows):</p>
 * <pre>
 *   Rows 0–4 (slots 0–44): item preview grid (up to 45 sellable item types)
 *   Row 5 (slots 45–53): [filler] [filler] [CONFIRM(47)] [filler] [INFO(49)] [filler] [CANCEL(51)] [filler] [BACK]
 * </pre>
 *
 * <p>Safety:</p>
 * <ul>
 *   <li>Inventory is re-scanned at confirm time; if items changed, totals are recalculated.</li>
 *   <li>Confirm click is debounced via a flag to prevent double-execution.</li>
 *   <li>Items are removed BEFORE depositing money (atomic pattern with refund on economy failure).</li>
 * </ul>
 */
public class SellAllConfirmGui implements MarketGui {

    private final Market plugin;
    private final ShopGuiManager shopGuiManager;
    private Inventory inventory;

    private static final int SLOT_CONFIRM = 47;
    private static final int SLOT_INFO    = 49;
    private static final int SLOT_CANCEL  = 51;
    private static final int SLOT_BACK    = 53;

    /** Snapshot of sellable items computed when the GUI opens. */
    private List<SellEntry> sellEntries = new ArrayList<>();
    private double grandTotal = 0;
    private double grandTax   = 0;
    private double grandNet   = 0;
    private int    totalItems = 0;

    /** Debounce flag — once confirm is executing, block further clicks. */
    private boolean executing = false;

    /** Represents one grouped sellable item type with its price breakdown. */
    private record SellEntry(String itemId, ShopItem shopItem, int quantity,
                              double pricePerUnit, double subtotal,
                              SellPriceBreakdown breakdown) {}

    public SellAllConfirmGui(Market plugin, ShopGuiManager shopGuiManager) {
        this.plugin = plugin;
        this.shopGuiManager = shopGuiManager;
    }

    public void open(Player player) {
        MessageManager msg = plugin.getMessageManager();
        String lang = msg.getPlayerLanguage(player);
        String title = msg.getRaw(lang, "servershop.titles.sell-all-confirm");
        inventory = Bukkit.createInventory(this, 54, TextUtil.colorize(title));

        scanInventory(player);
        buildDisplay(player);

        plugin.getGuiManager().registerGui(player.getUniqueId(), this);
        shopGuiManager.registerGui(player.getUniqueId(), this);
        player.openInventory(inventory);
    }

    // ═════════════════════════════════════════════════════
    //  Inventory scanning
    // ═════════════════════════════════════════════════════

    /**
     * Scans the player's inventory and groups sellable items.
     * Respects config toggles for hotbar, armor, and shield inclusion.
     */
    private void scanInventory(Player player) {
        sellEntries = new ArrayList<>();
        grandTotal = 0; grandTax = 0; grandNet = 0; totalItems = 0;

        boolean includeHotbar = plugin.getConfig().getBoolean("sell-inventory.sell-all.include-hotbar", true);
        boolean includeArmor  = plugin.getConfig().getBoolean("sell-inventory.sell-all.include-armor-and-shield", false);

        // Group items by item ID → total quantity
        Map<String, Integer> grouped = new LinkedHashMap<>();
        // Also track a representative stack per item ID (for durability/enchant calcs)
        Map<String, ItemStack> representativeStacks = new LinkedHashMap<>();

        ItemStack[] contents = player.getInventory().getContents();
        // Inventory layout: 0–8 = hotbar, 9–35 = main inventory, 36–39 = armor, 40 = offhand
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType().isAir()) continue;

            // Skip shulker boxes with contents — they have a dedicated sell chooser;
            // selling them in bulk here would destroy their contents.
            if (plugin.getConfig().getBoolean("sell-inventory.shulker-sell.enabled", true)
                    && ShulkerUtil.isShulkerBox(stack) && ShulkerUtil.hasContents(stack)) continue;

            // Skip hotbar if config disables it
            if (!includeHotbar && i <= 8) continue;
            // Skip armor slots (36–39) and offhand (40) if config disables it
            if (!includeArmor && i >= 36 && i <= 40) continue;

            String itemId = plugin.getShopPriceManager().identifyItem(stack);
            if (itemId == null) continue;
            if (!plugin.getShopPricingService().isSellable(itemId)) continue;
            // Skip BuildersDream creative dream items
            if (plugin.getBuildersDreamHook() != null && plugin.getBuildersDreamHook().isDreamItem(stack)) continue;

            grouped.merge(itemId, stack.getAmount(), Integer::sum);
            representativeStacks.putIfAbsent(itemId, stack);
        }

        // Build sell entries with price calculations
        for (Map.Entry<String, Integer> entry : grouped.entrySet()) {
            String itemId = entry.getKey();
            int qty = entry.getValue();

            ShopItem shopItem = plugin.getShopPriceManager().getShopItem(itemId);
            if (shopItem == null) continue;

            double baseSell = plugin.getShopPriceManager().getSellPrice(itemId);
            if (baseSell <= 0) continue;

            ItemStack repStack = representativeStacks.get(itemId);
            SellPriceBreakdown breakdown = plugin.getShopTransactionService()
                    .computeSellBreakdown(baseSell, repStack);
            double adjPrice = breakdown.finalSell();
            double sub = Math.round(adjPrice * qty * 100.0) / 100.0;

            sellEntries.add(new SellEntry(itemId, shopItem, qty, adjPrice, sub, breakdown));
            totalItems += qty;
            grandTotal += sub;
        }

        grandTax = plugin.getShopPricingService().calculateTax(grandTotal);
        grandNet = grandTotal - grandTax;
    }

    // ═════════════════════════════════════════════════════
    //  Display
    // ═════════════════════════════════════════════════════

    private void buildDisplay(Player player) {
        MessageManager msg = plugin.getMessageManager();
        String lang = msg.getPlayerLanguage(player);
        var filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) inventory.setItem(i, filler);

        // ── Item preview grid (rows 0–4, up to 45 entries) ───
        for (int i = 0; i < Math.min(sellEntries.size(), 45); i++) {
            SellEntry e = sellEntries.get(i);
            String name = e.shopItem.getDisplayName();
            List<String> lore = new ArrayList<>();
            lore.add("&7Quantity: &f" + e.quantity);
            lore.add("&7Price Each: &a" + msg.formatCurrency(e.pricePerUnit));
            lore.add("&7Subtotal: &a" + msg.formatCurrency(e.subtotal));

            if (e.breakdown.enchantBonus() > 0 || e.breakdown.modBonus() > 0 || e.breakdown.wearPenalty() > 0) {
                lore.add("");
                lore.add("&7Base: &f" + msg.formatCurrency(e.breakdown.baseSell()));
                if (e.breakdown.enchantBonus() > 0)
                    lore.add("&7Enchant: &a+" + msg.formatCurrency(e.breakdown.enchantBonus()));
                if (e.breakdown.modBonus() > 0)
                    lore.add("&7Mod Bonus: &a+" + msg.formatCurrency(e.breakdown.modBonus()));
                if (e.breakdown.wearPenalty() > 0)
                    lore.add("&7Wear: &c-" + msg.formatCurrency(e.breakdown.wearPenalty()));
            }

            ItemStack icon = e.shopItem.createItemStack(Math.min(e.quantity, 64));
            inventory.setItem(i, new ItemBuilder(icon).name("&f" + name).lore(lore).build());
        }

        if (sellEntries.isEmpty()) {
            inventory.setItem(22, new ItemBuilder(Material.BARRIER)
                    .name(msg.getRaw(lang, "servershop.buttons.sell-all-empty"))
                    .lore(msg.getList(lang, "servershop.lore.sell-all-empty")).build());
        }

        // ── Row 5: Confirm / Info / Cancel / Back ────────────
        // Info summary
        List<String> infoLore = new ArrayList<>();
        infoLore.add("");
        infoLore.add(msg.getRaw(lang, "servershop.lore.sell-all-info-items").replace("{count}", String.valueOf(totalItems)));
        infoLore.add(msg.getRaw(lang, "servershop.lore.sell-all-info-types").replace("{types}", String.valueOf(sellEntries.size())));
        infoLore.add("");
        infoLore.add(msg.getRaw(lang, "servershop.lore.sell-all-info-total").replace("{total}", msg.formatCurrency(grandTotal)));
        if (grandTax > 0)
            infoLore.add(msg.getRaw(lang, "servershop.lore.sell-all-info-tax").replace("{tax}", msg.formatCurrency(grandTax)));
        infoLore.add(msg.getRaw(lang, "servershop.lore.sell-all-info-net").replace("{net}", msg.formatCurrency(grandNet)));

        inventory.setItem(SLOT_INFO, new ItemBuilder(Material.PAPER)
                .name(msg.getRaw(lang, "servershop.buttons.sell-all-info")).lore(infoLore).build());

        // Confirm
        if (!sellEntries.isEmpty()) {
            List<String> confirmLore = new ArrayList<>();
            confirmLore.add("");
            confirmLore.add("&7Items: &f" + totalItems);
            confirmLore.add("&7You Receive: &a" + msg.formatCurrency(grandNet));
            confirmLore.add("");
            confirmLore.add(msg.getRaw(lang, "servershop.lore.sell-all-confirm-hint"));
            inventory.setItem(SLOT_CONFIRM, new ItemBuilder(Material.LIME_WOOL)
                    .name(msg.getRaw(lang, "servershop.buttons.sell-all-confirm"))
                    .lore(confirmLore).glow().build());
        }

        // Cancel
        inventory.setItem(SLOT_CANCEL, new ItemBuilder(Material.RED_WOOL)
                .name(msg.getRaw(lang, "servershop.buttons.sell-all-cancel"))
                .lore("&7Cancel and go back.").build());

        // Back
        inventory.setItem(SLOT_BACK, new ItemBuilder(Material.BARRIER)
                .name(msg.getRaw(lang, "servershop.buttons.back")).build());
    }

    // ═════════════════════════════════════════════════════
    //  Click handling
    // ═════════════════════════════════════════════════════

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventory) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.isShiftClick()) return;
        int slot = event.getRawSlot();

        switch (slot) {
            case SLOT_CONFIRM -> executeSellAll(player);
            case SLOT_CANCEL, SLOT_BACK -> {
                SoundUtil.playClickSound(player);
                shopGuiManager.openSellInventory(player);
            }
        }
    }

    /**
     * Executes the sell-all transaction with full safety checks.
     * <ol>
     *   <li>Re-scan inventory at confirm time</li>
     *   <li>Remove items first (anti-dupe)</li>
     *   <li>Deposit money; refund items on economy failure</li>
     *   <li>Log all transactions</li>
     * </ol>
     */
    private void executeSellAll(Player player) {
        if (executing) return; // debounce
        executing = true;

        MessageManager msg = plugin.getMessageManager();

        // Re-scan inventory at confirm time for safety
        scanInventory(player);
        if (sellEntries.isEmpty()) {
            SoundUtil.playErrorSound(player);
            player.sendMessage(msg.getPrefixedP(player, "servershop.messages.sell-all-nothing"));
            executing = false;
            shopGuiManager.openSellInventory(player);
            return;
        }

        // Execute each item type as an individual sell transaction
        int successCount = 0;
        double totalEarned = 0;
        double totalTaxPaid = 0;

        boolean capReached = false;
        for (SellEntry e : sellEntries) {
            ShopTransactionService.TransactionResult result =
                    plugin.getShopTransactionService().sellItem(player, e.itemId, e.quantity);
            if (result == ShopTransactionService.TransactionResult.SUCCESS) {
                successCount += e.quantity;
                double sub = Math.round(e.pricePerUnit * e.quantity * 100.0) / 100.0;
                double tax = plugin.getShopPricingService().calculateTax(sub);
                totalEarned += (sub - tax);
                totalTaxPaid += tax;
            } else if (result == ShopTransactionService.TransactionResult.DAILY_LIMIT_REACHED) {
                capReached = true;
                break; // no further sells can succeed today
            }
            // If a single item type fails, others still proceed (partial success)
        }

        if (capReached) {
            player.sendMessage(msg.getPrefixedP(player, "servershop.messages.daily-sell-cap"));
        }

        if (successCount > 0) {
            SoundUtil.playPurchaseSound(player);
            if (totalTaxPaid > 0) {
                player.sendMessage(msg.getPrefixedP(player, "servershop.messages.sell-all-success-tax", Map.of(
                        "count", String.valueOf(successCount),
                        "net_total", msg.formatCurrency(totalEarned),
                        "tax_amount", msg.formatCurrency(totalTaxPaid))));
            } else {
                player.sendMessage(msg.getPrefixedP(player, "servershop.messages.sell-all-success", Map.of(
                        "count", String.valueOf(successCount),
                        "total", msg.formatCurrency(totalEarned))));
            }
        } else {
            SoundUtil.playErrorSound(player);
            player.sendMessage(msg.getPrefixedP(player, "servershop.messages.sell-all-failed"));
        }

        executing = false;
        // Refresh sell inventory view
        shopGuiManager.openSellInventory(player);
    }

    @Override public GuiType getType() { return GuiType.SHOP_SELL_ALL_CONFIRM; }
    @Override public @NotNull Inventory getInventory() { return inventory; }
}


