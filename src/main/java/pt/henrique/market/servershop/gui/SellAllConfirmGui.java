package pt.henrique.market.servershop.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import pt.henrique.market.Market;
import pt.henrique.market.config.MessageManager;
import pt.henrique.market.gui.MarketGui;
import pt.henrique.market.servershop.SellVariantScanner;
import pt.henrique.market.servershop.model.SellVariant;
import pt.henrique.market.servershop.service.EnchantPriceUtil;
import pt.henrique.market.servershop.service.ShopTransactionService;
import pt.henrique.market.util.ItemBuilder;
import pt.henrique.market.util.SoundUtil;
import pt.henrique.market.util.TextUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

/**
 * Confirmation GUI for the "Sell Entire Inventory" action.
 *
 * <p>Scans the player's inventory, groups it into distinct items, shows a preview
 * grid with per-item breakdown, and requires a confirm click to execute.</p>
 *
 * <p>Enchanted, renamed and tool-modded items are held back by default — a bulk
 * sale is the one place where a mis-click costs gear that took hours to build.
 * They are counted and reported, not silently dropped, and remain sellable one by
 * one from the sell grid. Toggle with
 * {@code sell-inventory.sell-all.skip-special-gear}.</p>
 *
 * <p>Layout (54 slots / 6 rows):</p>
 * <pre>
 *   Rows 0–4 (slots 0–44): item preview grid (up to 45 distinct items)
 *   Row 5 (slots 45–53): [CONFIRM(47)] [INFO(49)] [CANCEL(51)] [BACK(53)]
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
    private List<SellVariant> variants = new ArrayList<>();
    private int    protectedItems = 0;
    private double grandTotal = 0;
    private double grandTax   = 0;
    private double grandNet   = 0;
    private int    totalItems = 0;

    /** Debounce flag — once confirm is executing, block further clicks. */
    private boolean executing = false;

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

    private void scanInventory(Player player) {
        SellVariantScanner.Result result = plugin.getSellVariantScanner().scanForSellAll(player);
        variants = result.variants();
        protectedItems = result.protectedItems();
        totalItems = result.totalUnits();
        grandTotal = result.grossValue();
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

        for (int i = 0; i < Math.min(variants.size(), 45); i++) {
            SellVariant variant = variants.get(i);
            List<String> lore = new ArrayList<>();
            lore.add(msg.getRaw(lang, "servershop.lore.variant-amount")
                    .replace("{amount}", String.valueOf(variant.amount())));
            lore.add(msg.getRaw(lang, "servershop.lore.variant-unit-price")
                    .replace("{price}", msg.formatCurrency(variant.unitPrice())));
            lore.add(msg.getRaw(lang, "servershop.lore.variant-stack-total")
                    .replace("{total}", msg.formatCurrency(variant.totalValue())));
            String enchants = EnchantPriceUtil.describeEnchants(variant.template());
            if (!enchants.isEmpty()) {
                lore.add(msg.getRaw(lang, "servershop.lore.variant-enchants").replace("{enchants}", enchants));
            }
            inventory.setItem(i, new ItemBuilder(variant.createStack(Math.min(variant.amount(), 64)))
                    .name("&f" + variant.displayName()).lore(lore).build());
        }

        if (variants.isEmpty()) {
            inventory.setItem(22, new ItemBuilder(Material.BARRIER)
                    .name(msg.getRaw(lang, "servershop.buttons.sell-all-empty"))
                    .lore(msg.getList(lang, "servershop.lore.sell-all-empty")).build());
        }

        // ── Row 5: Confirm / Info / Cancel / Back ────────────
        List<String> infoLore = new ArrayList<>();
        infoLore.add("");
        infoLore.add(msg.getRaw(lang, "servershop.lore.sell-all-info-items").replace("{count}", String.valueOf(totalItems)));
        infoLore.add(msg.getRaw(lang, "servershop.lore.sell-all-info-types").replace("{types}", String.valueOf(variants.size())));
        if (protectedItems > 0) {
            infoLore.add(msg.getRaw(lang, "servershop.lore.sell-all-info-protected")
                    .replace("{count}", String.valueOf(protectedItems)));
        }
        infoLore.add("");
        infoLore.add(msg.getRaw(lang, "servershop.lore.sell-all-info-total").replace("{total}", msg.formatCurrency(grandTotal)));
        if (grandTax > 0)
            infoLore.add(msg.getRaw(lang, "servershop.lore.sell-all-info-tax").replace("{tax}", msg.formatCurrency(grandTax)));
        infoLore.add(msg.getRaw(lang, "servershop.lore.sell-all-info-net").replace("{net}", msg.formatCurrency(grandNet)));

        inventory.setItem(SLOT_INFO, new ItemBuilder(Material.PAPER)
                .name(msg.getRaw(lang, "servershop.buttons.sell-all-info")).lore(infoLore).build());

        if (!variants.isEmpty()) {
            List<String> confirmLore = new ArrayList<>();
            confirmLore.add("");
            confirmLore.add(msg.getRaw(lang, "servershop.lore.sell-all-info-items")
                    .replace("{count}", String.valueOf(totalItems)));
            confirmLore.add(msg.getRaw(lang, "servershop.lore.variant-net").replace("{net}", msg.formatCurrency(grandNet)));
            confirmLore.add("");
            confirmLore.add(msg.getRaw(lang, "servershop.lore.sell-all-confirm-hint"));
            inventory.setItem(SLOT_CONFIRM, new ItemBuilder(Material.LIME_WOOL)
                    .name(msg.getRaw(lang, "servershop.buttons.sell-all-confirm"))
                    .lore(confirmLore).glow().build());
        }

        inventory.setItem(SLOT_CANCEL, new ItemBuilder(Material.RED_WOOL)
                .name(msg.getRaw(lang, "servershop.buttons.sell-all-cancel"))
                .lore(msg.getRaw(lang, "servershop.lore.sell-all-cancel")).build());

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
            default -> { /* preview cells are display only */ }
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
        if (variants.isEmpty()) {
            SoundUtil.playErrorSound(player);
            player.sendMessage(msg.getPrefixedP(player, "servershop.messages.sell-all-nothing"));
            executing = false;
            shopGuiManager.openSellInventory(player);
            return;
        }

        IntPredicate slots = plugin.getSellVariantScanner().sellAllSlots();
        int maxTx = plugin.getConfig().getInt("server-shop.max-transaction-quantity", 2304);

        int successCount = 0;
        double totalEarned = 0;
        double totalTaxPaid = 0;
        boolean capReached = false;

        for (SellVariant variant : variants) {
            int qty = Math.min(variant.amount(), maxTx);
            if (qty <= 0) continue;
            ShopTransactionService.TransactionResult result =
                    plugin.getShopTransactionService().sellVariant(player, variant, qty, slots);
            if (result == ShopTransactionService.TransactionResult.SUCCESS) {
                successCount += qty;
                double sub = Math.round(variant.unitPrice() * qty * 100.0) / 100.0;
                double tax = plugin.getShopPricingService().calculateTax(sub);
                totalEarned += (sub - tax);
                totalTaxPaid += tax;
            } else if (result == ShopTransactionService.TransactionResult.DAILY_LIMIT_REACHED) {
                capReached = true;
                break; // no further sells can succeed today
            }
            // If a single item fails, others still proceed (partial success)
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
            if (protectedItems > 0) {
                player.sendMessage(msg.getPrefixedP(player, "servershop.messages.sell-all-protected",
                        "count", String.valueOf(protectedItems)));
            }
        } else {
            SoundUtil.playErrorSound(player);
            player.sendMessage(msg.getPrefixedP(player, "servershop.messages.sell-all-failed"));
        }

        executing = false;
        shopGuiManager.openSellInventory(player);
    }

    @Override public GuiType getType() { return GuiType.SHOP_SELL_ALL_CONFIRM; }
    @Override public @NotNull Inventory getInventory() { return inventory; }
}
