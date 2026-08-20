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
import pt.henrique.market.servershop.MultiSellSession;
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

/**
 * Confirmation / preview GUI for the multi-select "sell to server" flow.
 * <p>
 * Mirrors {@link SellAllConfirmGui} but is scoped to the {@link MultiSellSession}
 * selection, selling {@code applyPercent(totalHeld, percent)} of each selected
 * item. Because the selection is held as item templates rather than price rows,
 * picking a plain pickaxe never sweeps up an enchanted one of the same type.
 *
 * <p>Layout (54 slots): preview grid 0–44, then Confirm(47) / Info(49) /
 * Cancel(51) / Back(53). Cancel and Back both return to the percentage picker.</p>
 */
public class MultiSellConfirmGui implements MarketGui {

    private final Market plugin;
    private final ShopGuiManager shopGuiManager;
    private Inventory inventory;

    private static final int SLOT_CONFIRM = 47;
    private static final int SLOT_INFO    = 49;
    private static final int SLOT_CANCEL  = 51;
    private static final int SLOT_BACK    = 53;

    private List<SellEntry> sellEntries = new ArrayList<>();
    private double grandTotal = 0;
    private double grandTax   = 0;
    private double grandNet   = 0;
    private int    totalItems = 0;
    private int    percent    = 100;

    private boolean executing = false;

    /** One selected item with its percentage-applied quantity. */
    private record SellEntry(SellVariant variant, int qtyToSell, int totalHeld,
                             boolean capped, double subtotal) {}

    public MultiSellConfirmGui(Market plugin, ShopGuiManager shopGuiManager) {
        this.plugin = plugin;
        this.shopGuiManager = shopGuiManager;
    }

    public void open(Player player) {
        MessageManager msg = plugin.getMessageManager();
        String lang = msg.getPlayerLanguage(player);
        String title = msg.getRaw(lang, "servershop.titles.multi-sell-confirm");
        inventory = Bukkit.createInventory(this, 54, TextUtil.colorize(title));

        scanSelection(player);
        buildDisplay(player);

        plugin.getGuiManager().registerGui(player.getUniqueId(), this);
        shopGuiManager.registerGui(player.getUniqueId(), this);
        player.openInventory(inventory);
    }

    /* ════════════════════════════════════════════════════════
     *  Scanning
     * ════════════════════════════════════════════════════════ */

    private void scanSelection(Player player) {
        sellEntries = new ArrayList<>();
        grandTotal = 0; grandTax = 0; grandNet = 0; totalItems = 0;

        MultiSellSession session = shopGuiManager.getMultiSellSession(player.getUniqueId());
        percent = session.getPercent();
        int maxTx = plugin.getConfig().getInt("server-shop.max-transaction-quantity", 2304);

        boolean shulkerSell = plugin.getConfig().getBoolean("sell-inventory.shulker-sell.enabled", true);
        List<SellVariant> held = plugin.getSellVariantScanner()
                .scanStorage(player, SellVariantScanner.Options.showAll(shulkerSell)).variants();

        // Walk the selection, not the inventory, so the preview keeps the order the
        // player clicked in. Anything they since ran out of simply drops out.
        for (ItemStack template : session.getSelected()) {
            SellVariant variant = SellVariantScanner.find(held, template);
            if (variant == null) continue;

            int wanted = MultiSellSession.applyPercent(variant.amount(), percent);
            int qtyToSell = Math.min(wanted, maxTx);
            if (qtyToSell <= 0) continue;

            double sub = Math.round(variant.unitPrice() * qtyToSell * 100.0) / 100.0;
            sellEntries.add(new SellEntry(variant, qtyToSell, variant.amount(), wanted > maxTx, sub));
            totalItems += qtyToSell;
            grandTotal += sub;
        }

        grandTotal = Math.round(grandTotal * 100.0) / 100.0;
        grandTax = plugin.getShopPricingService().calculateTax(grandTotal);
        grandNet = grandTotal - grandTax;
    }

    /* ════════════════════════════════════════════════════════
     *  Display
     * ════════════════════════════════════════════════════════ */

    private void buildDisplay(Player player) {
        MessageManager msg = plugin.getMessageManager();
        String lang = msg.getPlayerLanguage(player);
        var filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) inventory.setItem(i, filler);

        for (int i = 0; i < Math.min(sellEntries.size(), 45); i++) {
            SellEntry entry = sellEntries.get(i);
            SellVariant variant = entry.variant();
            List<String> lore = new ArrayList<>();
            lore.add(msg.getRaw(lang, "servershop.lore.variant-amount")
                    .replace("{amount}", String.valueOf(entry.qtyToSell())));
            lore.add(msg.getRaw(lang, "servershop.lore.multi-of-total")
                    .replace("{total}", String.valueOf(entry.totalHeld()))
                    .replace("{percent}", String.valueOf(percent)));
            lore.add(msg.getRaw(lang, "servershop.lore.variant-unit-price")
                    .replace("{price}", msg.formatCurrency(variant.unitPrice())));
            lore.add(msg.getRaw(lang, "servershop.lore.variant-stack-total")
                    .replace("{total}", msg.formatCurrency(entry.subtotal())));
            if (entry.capped()) {
                lore.add(msg.getRaw(lang, "servershop.lore.multi-capped")
                        .replace("{max}", String.valueOf(
                                plugin.getConfig().getInt("server-shop.max-transaction-quantity", 2304))));
            }
            String enchants = EnchantPriceUtil.describeEnchants(variant.template());
            if (!enchants.isEmpty()) {
                lore.add(msg.getRaw(lang, "servershop.lore.variant-enchants").replace("{enchants}", enchants));
            }

            inventory.setItem(i, new ItemBuilder(variant.createStack(Math.min(entry.qtyToSell(), 64)))
                    .name("&f" + variant.displayName()).lore(lore).build());
        }

        if (sellEntries.isEmpty()) {
            inventory.setItem(22, new ItemBuilder(Material.BARRIER)
                    .name(msg.getRaw(lang, "servershop.buttons.sell-all-empty"))
                    .lore(msg.getList(lang, "servershop.lore.sell-all-empty")).build());
        }

        // ── Info summary ─────────────────────────────────────
        List<String> infoLore = new ArrayList<>();
        infoLore.add("");
        infoLore.add(msg.getRaw(lang, "servershop.lore.multi-info-percent").replace("{percent}", String.valueOf(percent)));
        infoLore.add(msg.getRaw(lang, "servershop.lore.multi-info-types").replace("{types}", String.valueOf(sellEntries.size())));
        infoLore.add(msg.getRaw(lang, "servershop.lore.multi-info-units").replace("{units}", String.valueOf(totalItems)));
        infoLore.add("");
        infoLore.add(msg.getRaw(lang, "servershop.lore.multi-info-gross").replace("{gross}", msg.formatCurrency(grandTotal)));
        if (grandTax > 0)
            infoLore.add(msg.getRaw(lang, "servershop.lore.multi-info-tax").replace("{tax}", msg.formatCurrency(grandTax)));
        infoLore.add(msg.getRaw(lang, "servershop.lore.multi-info-net").replace("{net}", msg.formatCurrency(grandNet)));
        inventory.setItem(SLOT_INFO, new ItemBuilder(Material.PAPER)
                .name(msg.getRaw(lang, "servershop.buttons.sell-all-info")).lore(infoLore).build());

        // ── Confirm ──────────────────────────────────────────
        if (!sellEntries.isEmpty()) {
            List<String> confirmLore = new ArrayList<>();
            confirmLore.add("");
            confirmLore.add(msg.getRaw(lang, "servershop.lore.sell-all-info-items")
                    .replace("{count}", String.valueOf(totalItems)));
            confirmLore.add(msg.getRaw(lang, "servershop.lore.variant-net").replace("{net}", msg.formatCurrency(grandNet)));
            confirmLore.add("");
            confirmLore.add(msg.getRaw(lang, "servershop.lore.multi-confirm-hint"));
            inventory.setItem(SLOT_CONFIRM, new ItemBuilder(Material.LIME_WOOL)
                    .name(msg.getRaw(lang, "servershop.buttons.multi-confirm"))
                    .lore(confirmLore).glow().build());
        }

        // ── Cancel / Back ────────────────────────────────────
        inventory.setItem(SLOT_CANCEL, new ItemBuilder(Material.RED_WOOL)
                .name(msg.getRaw(lang, "servershop.buttons.sell-all-cancel"))
                .lore(msg.getRaw(lang, "servershop.lore.multi-cancel")).build());
        inventory.setItem(SLOT_BACK, new ItemBuilder(Material.BARRIER)
                .name(msg.getRaw(lang, "servershop.buttons.back")).build());
    }

    /* ════════════════════════════════════════════════════════
     *  Click handling
     * ════════════════════════════════════════════════════════ */

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventory) return;
        if (event.isShiftClick()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();

        switch (slot) {
            case SLOT_CONFIRM -> executeMultiSell(player);
            case SLOT_CANCEL, SLOT_BACK -> {
                SoundUtil.playClickSound(player);
                shopGuiManager.openMultiSellQuantity(player);
            }
            default -> { /* preview cells are display only */ }
        }
    }

    private void executeMultiSell(Player player) {
        if (executing) return; // debounce
        executing = true;

        MessageManager msg = plugin.getMessageManager();

        // Re-scan at confirm time for safety (inventory may have changed)
        scanSelection(player);
        if (sellEntries.isEmpty()) {
            SoundUtil.playErrorSound(player);
            player.sendMessage(msg.getPrefixedP(player, "servershop.messages.multi-none-eligible"));
            executing = false;
            shopGuiManager.openMultiSellQuantity(player);
            return;
        }

        int successCount = 0;
        double totalEarned = 0;
        double totalTaxPaid = 0;
        boolean capReached = false;

        for (SellEntry entry : sellEntries) {
            ShopTransactionService.TransactionResult result = plugin.getShopTransactionService()
                    .sellVariant(player, entry.variant(), entry.qtyToSell(), ShopTransactionService.STORAGE_SLOTS);
            if (result == ShopTransactionService.TransactionResult.SUCCESS) {
                successCount += entry.qtyToSell();
                double tax = plugin.getShopPricingService().calculateTax(entry.subtotal());
                totalEarned += (entry.subtotal() - tax);
                totalTaxPaid += tax;
            } else if (result == ShopTransactionService.TransactionResult.DAILY_LIMIT_REACHED) {
                capReached = true;
                break; // no further sells can succeed today
            }
            // Partial success allowed — one item failing does not abort the rest.
        }

        if (capReached) {
            player.sendMessage(msg.getPrefixedP(player, "servershop.messages.daily-sell-cap"));
        }

        if (successCount > 0) {
            SoundUtil.playPurchaseSound(player);
            if (totalTaxPaid > 0) {
                player.sendMessage(msg.getPrefixedP(player, "servershop.messages.multi-success-tax", Map.of(
                        "count", String.valueOf(successCount),
                        "net_total", msg.formatCurrency(totalEarned),
                        "tax_amount", msg.formatCurrency(totalTaxPaid))));
            } else {
                player.sendMessage(msg.getPrefixedP(player, "servershop.messages.multi-success", Map.of(
                        "count", String.valueOf(successCount),
                        "total", msg.formatCurrency(totalEarned))));
            }
        } else {
            SoundUtil.playErrorSound(player);
            player.sendMessage(msg.getPrefixedP(player, "servershop.messages.multi-failed"));
        }

        executing = false;
        shopGuiManager.clearMultiSellSession(player.getUniqueId());
        shopGuiManager.openSellInventory(player);
    }

    @Override public GuiType getType() { return GuiType.SHOP_MULTI_SELL_CONFIRM; }
    @Override public @NotNull Inventory getInventory() { return inventory; }
}
