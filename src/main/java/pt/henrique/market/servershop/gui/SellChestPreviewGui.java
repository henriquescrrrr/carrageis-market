package pt.henrique.market.servershop.gui;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import pt.henrique.market.Market;
import pt.henrique.market.config.MessageManager;
import pt.henrique.market.gui.MarketGui;
import pt.henrique.market.servershop.model.SellVariant;
import pt.henrique.market.servershop.service.EnchantPriceUtil;
import pt.henrique.market.util.ItemBuilder;
import pt.henrique.market.util.SoundUtil;
import pt.henrique.market.util.TextUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Preview GUI for the Sell Chest feature.
 * Shows all sellable items scanned from a container with a total value summary.
 * <p>
 * Layout (54 slots):
 * <ul>
 *   <li>Rows 0–4 (slots 0–44): Items that will be sold, with per-item price lore</li>
 *   <li>Row 5 (slots 45–53): [filler] [filler] [TOTAL_INFO] [filler] [CONFIRM] [filler] [CANCEL] [filler] [filler]</li>
 * </ul>
 */
public class SellChestPreviewGui implements MarketGui {

    private final Market plugin;
    private final List<SellVariant> entries;
    private final Inventory sourceInventory;
    private final Location sourceLocation;
    private Inventory inventory;

    private static final int SLOT_TOTAL    = 47;
    private static final int SLOT_CONFIRM  = 49;
    private static final int SLOT_CANCEL   = 51;

    public SellChestPreviewGui(Market plugin, List<SellVariant> entries,
                               Inventory sourceInventory, Location sourceLocation) {
        this.plugin = plugin;
        this.entries = entries;
        this.sourceInventory = sourceInventory;
        this.sourceLocation = sourceLocation;
    }

    public void open(Player player) {
        MessageManager msg = plugin.getMessageManager();
        String lang = msg.getPlayerLanguage(player);
        String title = msg.getRaw(lang, "servershop.messages.sell-chest-preview-title");
        if (title == null) title = "&8&lSell Chest Preview";
        inventory = Bukkit.createInventory(this, 54, TextUtil.colorize(title));

        buildGui(player);
        plugin.getGuiManager().registerGui(player.getUniqueId(), this);
        player.openInventory(inventory);
    }

    private void buildGui(Player player) {
        MessageManager msg = plugin.getMessageManager();
        String lang = msg.getPlayerLanguage(player);

        var filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) inventory.setItem(i, filler);

        // ── Rows 0–4: Item entries ──────────────────────────────
        int slot = 0;
        for (SellVariant entry : entries) {
            if (slot >= 45) break;
            List<String> lore = new ArrayList<>();
            lore.add(msg.getRaw(lang, "servershop.lore.variant-amount")
                    .replace("{amount}", String.valueOf(entry.amount())));
            lore.add(msg.getRaw(lang, "servershop.lore.variant-unit-price")
                    .replace("{price}", msg.formatCurrency(entry.unitPrice())));
            lore.add(msg.getRaw(lang, "servershop.lore.variant-stack-total")
                    .replace("{total}", msg.formatCurrency(entry.totalValue())));
            String enchants = EnchantPriceUtil.describeEnchants(entry.template());
            if (!enchants.isEmpty()) {
                lore.add(msg.getRaw(lang, "servershop.lore.variant-enchants").replace("{enchants}", enchants));
            }

            inventory.setItem(slot, new ItemBuilder(entry.createStack(Math.min(entry.amount(), 64)))
                    .name("&f" + entry.displayName())
                    .lore(lore)
                    .build());
            slot++;
        }

        // ── Row 5: Summary and actions ──────────────────────────
        double grossTotal = 0;
        int totalCount = 0;
        for (SellVariant entry : entries) {
            grossTotal += entry.totalValue();
            totalCount += entry.amount();
        }

        double taxRate = plugin.getShopPricingService().getSellTaxRate();
        double taxAmount = plugin.getShopPricingService().calculateTax(grossTotal);
        double netTotal = grossTotal - taxAmount;

        // Total info item
        String totalName = msg.getRaw(lang, "servershop.messages.sell-chest-total-name");
        if (totalName == null) totalName = "&6&lSale Summary";

        List<String> totalLore = new ArrayList<>();
        totalLore.add(replace(msg.getRaw(lang, "servershop.messages.sell-chest-total-lore-items"),
                "{count}", String.valueOf(totalCount)));
        totalLore.add(replace(msg.getRaw(lang, "servershop.messages.sell-chest-total-lore-gross"),
                "{gross}", msg.formatCurrency(grossTotal)));
        if (taxAmount > 0) {
            totalLore.add(replace(
                    replace(msg.getRaw(lang, "servershop.messages.sell-chest-total-lore-tax"),
                            "{tax_rate}", String.format("%.1f", taxRate)),
                    "{tax}", msg.formatCurrency(taxAmount)));
        }
        totalLore.add(replace(msg.getRaw(lang, "servershop.messages.sell-chest-total-lore-net"),
                "{net}", msg.formatCurrency(netTotal)));

        inventory.setItem(SLOT_TOTAL, new ItemBuilder(Material.GOLD_INGOT)
                .name(totalName).lore(totalLore).glow().build());

        // Confirm button
        String confirmName = msg.getRaw(lang, "servershop.messages.sell-chest-confirm");
        if (confirmName == null) confirmName = "&a&lConfirm Sale";
        inventory.setItem(SLOT_CONFIRM, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                .name(confirmName)
                .lore("&7Click to sell all items", "&7and receive your payment.")
                .build());

        // Cancel button
        String cancelName = msg.getRaw(lang, "servershop.messages.sell-chest-cancel");
        if (cancelName == null) cancelName = "&c&lCancel";
        inventory.setItem(SLOT_CANCEL, new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                .name(cancelName)
                .lore("&7Click to cancel.", "&7Nothing will be sold.")
                .build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventory) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();

        if (slot == SLOT_CONFIRM) {
            executeChestSell(player);
        } else if (slot == SLOT_CANCEL) {
            SoundUtil.playClickSound(player);
            player.closeInventory();
            player.sendMessage(plugin.getMessageManager().getPrefixedP(player,
                    "servershop.messages.sell-chest-cancelled"));
        }
    }

    /**
     * Executes the chest sell: removes items from the container and deposits money.
     */
    private void executeChestSell(Player player) {
        MessageManager msg = plugin.getMessageManager();

        // Daily sell cap — projected worst case, checked before any removal
        double projectedGross = 0;
        for (SellVariant entry : entries) projectedGross += entry.totalValue();
        double projectedNet = projectedGross - plugin.getShopPricingService().calculateTax(projectedGross);
        if (projectedNet > 0 && pt.henrique.market.economy.EconomyManager.toCents(projectedNet)
                > plugin.getShopTransactionService().getRemainingDailyCapCents(player)) {
            SoundUtil.playErrorSound(player);
            player.sendMessage(msg.getPrefixedP(player, "servershop.messages.daily-sell-cap"));
            player.closeInventory();
            return;
        }

        // Re-validate: make sure the container still has the items
        // (someone might have removed items between preview and confirm)
        int totalRemoved = 0;
        double totalGross = 0;

        for (SellVariant entry : entries) {
            ItemStack template = entry.template();
            // Take only what is still there, and only stacks identical to this entry.
            int available = plugin.getShopTransactionService().countVariantIn(sourceInventory, template);
            int removed = Math.min(available, entry.amount());
            if (removed <= 0) continue;
            plugin.getShopTransactionService().removeVariantFrom(sourceInventory, template, removed);
            totalRemoved += removed;
            double entryGross = Math.round(entry.unitPrice() * removed * 100.0) / 100.0;
            totalGross += entryGross;
            plugin.getShopTransactionService().logShopSale(player, entry.itemId(), removed,
                    entry.unitPrice(), entryGross,
                    plugin.getShopPricingService().calculateTax(entryGross));
        }

        if (totalRemoved == 0) {
            SoundUtil.playErrorSound(player);
            player.sendMessage(msg.getPrefixedP(player, "servershop.messages.sell-chest-nothing-to-sell"));
            player.closeInventory();
            return;
        }

        double tax = plugin.getShopPricingService().calculateTax(totalGross);
        double netAmount = totalGross - tax;
        if (netAmount > 0) plugin.getShopTransactionService().recordDailySell(player, netAmount);

        // Deposit money asynchronously
        final int finalCount = totalRemoved;
        final double finalNet = netAmount;
        plugin.getEconomyManager().depositCents(
                player.getUniqueId(),
                pt.henrique.market.economy.EconomyManager.toCents(netAmount),
                "Market: Sell Chest (" + finalCount + " items)"
        ).thenAccept(success -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    SoundUtil.playPurchaseSound(player);
                    player.sendMessage(msg.getPrefixedP(player,
                            "servershop.messages.sell-chest-sold",
                            Map.of("count", String.valueOf(finalCount),
                                    "total", msg.formatCurrency(finalNet))));
                } else {
                    // Deposit failed — this is a problem. Items are already removed.
                    // Store as pending earnings for recovery on next login.
                    plugin.getLogger().warning("Sell Chest deposit failed for " + player.getName()
                            + "! Amount: " + finalNet + ". Items already removed from container.");
                    player.sendMessage(msg.getPrefixedP(player, "servershop.messages.sell-chest-sold",
                            Map.of("count", String.valueOf(finalCount),
                                    "total", msg.formatCurrency(finalNet))));
                }
                player.closeInventory();
            });
        });
    }

    private String replace(String text, String key, String value) {
        if (text == null) return "";
        return text.replace(key, value);
    }

    @Override
    public GuiType getType() { return GuiType.SELL_CHEST_PREVIEW; }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }
}



