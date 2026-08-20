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
import pt.henrique.market.economy.EconomyManager;
import pt.henrique.market.gui.MarketGui;
import pt.henrique.market.hook.ToolModsHook;
import pt.henrique.market.servershop.model.SellQuote;
import pt.henrique.market.servershop.model.SellVariant;
import pt.henrique.market.servershop.service.ShopTransactionService;
import pt.henrique.market.servershop.service.ShopTransactionService.SellPriceBreakdown;
import pt.henrique.market.util.ItemBuilder;
import pt.henrique.market.util.SoundUtil;
import pt.henrique.market.util.TextUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

/**
 * Sell screen for one specific item — the exact bow the player clicked, not every
 * bow they happen to own.
 * <p>
 * The preview slot shows the real item, so its enchantments, name and durability
 * are visible in the tooltip, and the price breakdown is computed from that same
 * item. Selling removes only stacks identical to it.
 *
 * <p>Layout (54 slots), matching {@link ShopItemDetailGui} so the controls feel
 * the same:</p>
 * <pre>
 *   Row 0: filler, [4] set-to-all
 *   Row 1: [-32][-16][-8][-1] [PREVIEW] [+1][+8][+16][+32]
 *   Row 2: [MIN] filler×3 [QTY] filler×3 [MAX]
 *   Row 3: pack selectors (stackable items only)
 *   Row 4: filler
 *   Row 5: filler×4 [SELL] filler×3 [BACK]
 * </pre>
 */
public class SellVariantDetailGui implements MarketGui {

    /** Where the Back button returns to. */
    public enum Origin { SELL_INVENTORY, SHOP_MENU }

    private final Market plugin;
    private final ShopGuiManager shopGuiManager;
    private final SellVariant variant;
    private final IntPredicate slots;
    private final Origin origin;
    private Inventory inventory;

    private int quantity = 1;
    private boolean pendingConfirm = false;
    private boolean executing = false;

    private static final int SLOT_AUTO = 4;
    private static final int SLOT_PREVIEW = 13;
    private static final int SLOT_SUB_32 = 9, SLOT_SUB_16 = 10, SLOT_SUB_8 = 11, SLOT_SUB_1 = 12;
    private static final int SLOT_ADD_1 = 14, SLOT_ADD_8 = 15, SLOT_ADD_16 = 16, SLOT_ADD_32 = 17;
    private static final int SLOT_MIN = 18, SLOT_QTY_INFO = 22, SLOT_MAX = 26;
    private static final int SLOT_PACK_INFO = 31;
    private static final int[] SLOT_PACK_SUB = {30, 29, 28, 27};
    private static final int[] SLOT_PACK_ADD = {32, 33, 34, 35};
    private static final int SLOT_ACTION = 49;
    private static final int SLOT_BACK = 53;
    private static final int MAX_PACK_BUTTONS = 4;

    private int maxStackSize = 64;
    private boolean showPackSelectors = false;
    private int packButtonCount = 4;

    public SellVariantDetailGui(Market plugin, ShopGuiManager shopGuiManager,
                                SellVariant variant, IntPredicate slots, Origin origin) {
        this.plugin = plugin;
        this.shopGuiManager = shopGuiManager;
        this.variant = variant;
        this.slots = slots;
        this.origin = origin;
    }

    public void open(Player player) {
        MessageManager msg = plugin.getMessageManager();
        String lang = msg.getPlayerLanguage(player);
        String title = msg.getRaw(lang, "servershop.titles.sell-only").replace("{item}", variant.displayName());
        inventory = Bukkit.createInventory(this, 54, TextUtil.colorize(truncateTitle(title)));

        maxStackSize = Math.max(1, variant.template().getMaxStackSize());
        boolean packEnabled = plugin.getConfig().getBoolean("gui.quantity.pack-selectors.enabled", true);
        int cfgPacks = plugin.getConfig().getInt("gui.quantity.pack-selectors.max-packs", MAX_PACK_BUTTONS);
        packButtonCount = Math.max(1, Math.min(cfgPacks, MAX_PACK_BUTTONS));
        showPackSelectors = packEnabled && maxStackSize > 1;

        quantity = Math.max(1, Math.min(quantity, held(player)));

        updateDisplay(player);
        plugin.getGuiManager().registerGui(player.getUniqueId(), this);
        shopGuiManager.registerGui(player.getUniqueId(), this);
        player.openInventory(inventory);
    }

    /* ════════════════════════════════════════════════════════
     *  Display
     * ════════════════════════════════════════════════════════ */

    private void updateDisplay(Player player) {
        MessageManager msg = plugin.getMessageManager();
        String lang = msg.getPlayerLanguage(player);

        var filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) inventory.setItem(i, filler);

        int available = held(player);
        double unit = variant.unitPrice();
        double gross = Math.round(unit * quantity * 100.0) / 100.0;
        double tax = plugin.getShopPricingService().calculateTax(gross);
        double net = gross - tax;

        // ── Preview: the actual item, with the price it earns ────
        List<String> previewLore = new ArrayList<>();
        previewLore.add(msg.getRaw(lang, "servershop.lore.variant-available")
                .replace("{available}", String.valueOf(available)));
        previewLore.add(msg.getRaw(lang, "servershop.lore.variant-unit-price")
                .replace("{price}", msg.formatCurrency(unit)));
        previewLore.addAll(breakdownLore(msg, lang));
        inventory.setItem(SLOT_PREVIEW, new ItemBuilder(variant.createStack(Math.min(Math.max(quantity, 1), 64)))
                .name("&f&l" + variant.displayName()).lore(previewLore).build());

        // ── Row 1: quantity steps ────────────────────────────────
        inventory.setItem(SLOT_SUB_32, qtyBtn(msg.getRaw(lang, "servershop.buttons.quantity-sub-32"), Material.RED_STAINED_GLASS_PANE));
        inventory.setItem(SLOT_SUB_16, qtyBtn(msg.getRaw(lang, "servershop.buttons.quantity-sub-16"), Material.RED_STAINED_GLASS_PANE));
        inventory.setItem(SLOT_SUB_8, qtyBtn(msg.getRaw(lang, "servershop.buttons.quantity-sub-8"), Material.RED_STAINED_GLASS_PANE));
        inventory.setItem(SLOT_SUB_1, qtyBtn(msg.getRaw(lang, "servershop.buttons.quantity-sub-1"), Material.RED_STAINED_GLASS_PANE));
        inventory.setItem(SLOT_ADD_1, qtyBtn(msg.getRaw(lang, "servershop.buttons.quantity-add-1"), Material.LIME_STAINED_GLASS_PANE));
        inventory.setItem(SLOT_ADD_8, qtyBtn(msg.getRaw(lang, "servershop.buttons.quantity-add-8"), Material.LIME_STAINED_GLASS_PANE));
        inventory.setItem(SLOT_ADD_16, qtyBtn(msg.getRaw(lang, "servershop.buttons.quantity-add-16"), Material.LIME_STAINED_GLASS_PANE));
        inventory.setItem(SLOT_ADD_32, qtyBtn(msg.getRaw(lang, "servershop.buttons.quantity-add-32"), Material.LIME_STAINED_GLASS_PANE));

        // ── Row 2 ────────────────────────────────────────────────
        inventory.setItem(SLOT_MIN, new ItemBuilder(Material.RED_CONCRETE)
                .name(msg.getRaw(lang, "servershop.buttons.quantity-min")).build());
        inventory.setItem(SLOT_MAX, new ItemBuilder(Material.LIME_CONCRETE)
                .name(msg.getRaw(lang, "servershop.buttons.quantity-max"))
                .lore(msg.getRaw(lang, "servershop.lore.variant-max-hint")).build());
        inventory.setItem(SLOT_QTY_INFO, new ItemBuilder(Material.PAPER)
                .name(msg.getRaw(lang, "servershop.buttons.quantity-info").replace("{quantity}", String.valueOf(quantity)))
                .lore("", msg.getRaw(lang, "servershop.lore.variant-total").replace("{total}", msg.formatCurrency(net)))
                .build());

        List<String> autoLore = new ArrayList<>(msg.getList(lang, "servershop.lore.auto-quantity"));
        autoLore.replaceAll(l -> l.replace("{available}", String.valueOf(available)));
        inventory.setItem(SLOT_AUTO, new ItemBuilder(available > 0 ? Material.HOPPER : Material.GRAY_DYE)
                .name(msg.getRaw(lang, "servershop.buttons.auto-quantity")).lore(autoLore).build());

        // ── Row 3: pack selectors ────────────────────────────────
        if (showPackSelectors) {
            String stackSize = String.valueOf(maxStackSize);
            inventory.setItem(SLOT_PACK_INFO, new ItemBuilder(Material.CHEST)
                    .name(msg.getRaw(lang, "servershop.buttons.pack-info").replace("{stack_size}", stackSize))
                    .lore(msg.getRaw(lang, "servershop.lore.pack-info-line1").replace("{stack_size}", stackSize),
                          msg.getRaw(lang, "servershop.lore.pack-info-line2").replace("{stack_size}", stackSize))
                    .build());
            for (int i = 0; i < MAX_PACK_BUTTONS && i < packButtonCount; i++) {
                int packs = i + 1;
                String sN = String.valueOf(packs), sD = String.valueOf(packs * maxStackSize);
                inventory.setItem(SLOT_PACK_SUB[i], new ItemBuilder(Material.ORANGE_STAINED_GLASS_PANE)
                        .name(msg.getRaw(lang, "servershop.buttons.pack-sub").replace("{packs}", sN).replace("{amount}", sD)).build());
                inventory.setItem(SLOT_PACK_ADD[i], new ItemBuilder(Material.CYAN_STAINED_GLASS_PANE)
                        .name(msg.getRaw(lang, "servershop.buttons.pack-add").replace("{packs}", sN).replace("{amount}", sD)).build());
            }
        }

        // ── Row 5: sell + back ───────────────────────────────────
        List<String> actionLore = new ArrayList<>();
        actionLore.add("");
        actionLore.add(msg.getRaw(lang, "servershop.lore.variant-sell-line")
                .replace("{quantity}", String.valueOf(quantity))
                .replace("{item}", variant.displayName()));
        actionLore.add(msg.getRaw(lang, "servershop.lore.variant-gross").replace("{gross}", msg.formatCurrency(gross)));
        if (tax > 0) actionLore.add(msg.getRaw(lang, "servershop.lore.variant-tax").replace("{tax}", msg.formatCurrency(tax)));
        actionLore.add(msg.getRaw(lang, "servershop.lore.variant-net").replace("{net}", msg.formatCurrency(net)));
        actionLore.add("");
        if (available < quantity) {
            actionLore.add(msg.getRaw(lang, "servershop.lore.variant-not-enough")
                    .replace("{quantity}", String.valueOf(quantity))
                    .replace("{available}", String.valueOf(available)));
        } else {
            actionLore.add(msg.getRaw(lang, pendingConfirm
                    ? "servershop.lore.variant-confirm-hint"
                    : "servershop.lore.variant-sell-hint"));
        }
        ItemBuilder action = new ItemBuilder(pendingConfirm ? Material.ORANGE_CONCRETE : Material.GOLD_INGOT)
                .name(msg.getRaw(lang, pendingConfirm
                        ? "servershop.buttons.confirm-sell"
                        : "servershop.buttons.variant-sell"))
                .lore(actionLore);
        if (pendingConfirm) action.glow();
        inventory.setItem(SLOT_ACTION, action.build());

        inventory.setItem(SLOT_BACK, new ItemBuilder(Material.BARRIER)
                .name(msg.getRaw(lang, "servershop.buttons.back")).build());
    }

    /** Lore lines explaining how the unit price was reached. */
    private List<String> breakdownLore(MessageManager msg, String lang) {
        List<String> lore = new ArrayList<>();
        ItemStack template = variant.template();
        SellQuote quote = variant.quote();
        SellPriceBreakdown breakdown = plugin.getShopTransactionService()
                .computeSellBreakdown(quote.baseSell(), template, quote.composite());

        boolean hasExtras = breakdown.enchantBonus() > 0 || breakdown.modBonus() > 0
                || breakdown.wearPenalty() > 0 || quote.composite();
        if (!hasExtras) return lore;

        lore.add("");
        lore.add(msg.getRaw(lang, "servershop.lore.breakdown-header"));

        if (quote.composite()) {
            // Enchanted book: show what each enchantment contributes to the sum.
            for (var entry : plugin.getEnchantPriceUtil().getBookValueBreakdown(template).entrySet()) {
                lore.add(msg.getRaw(lang, "servershop.lore.breakdown-enchant-line")
                        .replace("{enchant}", entry.getKey())
                        .replace("{value}", msg.formatCurrency(entry.getValue())));
            }
        } else {
            lore.add(msg.getRaw(lang, "servershop.lore.breakdown-base")
                    .replace("{base}", msg.formatCurrency(breakdown.baseSell())));
            if (breakdown.enchantBonus() > 0) {
                lore.add(msg.getRaw(lang, "servershop.lore.breakdown-enchant")
                        .replace("{bonus}", msg.formatCurrency(breakdown.enchantBonus())));
                for (var entry : plugin.getEnchantPriceUtil().getEnchantBreakdown(template).entrySet()) {
                    lore.add(msg.getRaw(lang, "servershop.lore.breakdown-enchant-line")
                            .replace("{enchant}", entry.getKey())
                            .replace("{value}", msg.formatCurrency(EconomyManager.toDouble(entry.getValue()))));
                }
            }
        }

        if (breakdown.modBonus() > 0) {
            for (var mod : ToolModsHook.getModBreakdown(template).entrySet()) {
                lore.add(msg.getRaw(lang, "servershop.lore.breakdown-mod")
                        .replace("{mod}", mod.getKey())
                        .replace("{value}", msg.formatCurrency(EconomyManager.toDouble(mod.getValue()))));
            }
        }
        if (breakdown.wearPenalty() > 0) {
            lore.add(msg.getRaw(lang, "servershop.lore.breakdown-wear")
                    .replace("{penalty}", msg.formatCurrency(breakdown.wearPenalty()))
                    .replace("{description}", breakdown.wearDescription()));
        } else if (breakdown.wearDescription().contains("Mending")) {
            lore.add(msg.getRaw(lang, "servershop.lore.breakdown-wear-mending"));
        }
        lore.add(msg.getRaw(lang, "servershop.lore.breakdown-final")
                .replace("{final}", msg.formatCurrency(breakdown.finalSell())));
        return lore;
    }

    private ItemStack qtyBtn(String name, Material material) {
        return new ItemBuilder(material).name(name).build();
    }

    /** Bukkit truncates long inventory titles; keep them well inside the limit. */
    private static String truncateTitle(String title) {
        return title.length() <= 60 ? title : title.substring(0, 57) + "...";
    }

    /* ════════════════════════════════════════════════════════
     *  Clicks
     * ════════════════════════════════════════════════════════ */

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventory) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        int maxQty = plugin.getConfig().getInt("server-shop.max-transaction-quantity", 2304);

        switch (slot) {
            case SLOT_SUB_32 -> adjust(-32, maxQty, player);
            case SLOT_SUB_16 -> adjust(-16, maxQty, player);
            case SLOT_SUB_8 -> adjust(-8, maxQty, player);
            case SLOT_SUB_1 -> adjust(-1, maxQty, player);
            case SLOT_ADD_1 -> adjust(1, maxQty, player);
            case SLOT_ADD_8 -> adjust(8, maxQty, player);
            case SLOT_ADD_16 -> adjust(16, maxQty, player);
            case SLOT_ADD_32 -> adjust(32, maxQty, player);
            case SLOT_MIN -> {
                pendingConfirm = false;
                quantity = 1;
                SoundUtil.playClickSound(player);
                updateDisplay(player);
            }
            case SLOT_MAX, SLOT_AUTO -> {
                pendingConfirm = false;
                int available = held(player);
                if (available <= 0) { SoundUtil.playErrorSound(player); return; }
                quantity = Math.max(1, Math.min(available, maxQty));
                SoundUtil.playClickSound(player);
                updateDisplay(player);
            }
            case SLOT_ACTION -> handleSellClick(player);
            case SLOT_BACK -> {
                SoundUtil.playClickSound(player);
                goBack(player);
            }
            default -> {
                if (!showPackSelectors) return;
                for (int i = 0; i < packButtonCount; i++) {
                    if (slot == SLOT_PACK_SUB[i]) { adjust(-((i + 1) * maxStackSize), maxQty, player); return; }
                    if (slot == SLOT_PACK_ADD[i]) { adjust((i + 1) * maxStackSize, maxQty, player); return; }
                }
            }
        }
    }

    private void handleSellClick(Player player) {
        if (executing) return;
        MessageManager msg = plugin.getMessageManager();

        int available = held(player);
        if (available < quantity) {
            SoundUtil.playErrorSound(player);
            player.sendMessage(msg.getPrefixedP(player, "servershop.messages.sell-no-items",
                    "quantity", String.valueOf(quantity)));
            updateDisplay(player);
            return;
        }

        if (!pendingConfirm && plugin.getConfig().getBoolean("server-shop.sell-confirmation", true)) {
            pendingConfirm = true;
            SoundUtil.playClickSound(player);
            updateDisplay(player);
            return;
        }

        executing = true;
        pendingConfirm = false;
        int qty = quantity;
        double gross = Math.round(variant.unitPrice() * qty * 100.0) / 100.0;
        double tax = plugin.getShopPricingService().calculateTax(gross);
        double net = gross - tax;

        ShopTransactionService.TransactionResult result =
                plugin.getShopTransactionService().sellVariant(player, variant, qty, slots);
        executing = false;

        switch (result) {
            case SUCCESS -> {
                SoundUtil.playPurchaseSound(player);
                if (tax > 0) {
                    player.sendMessage(msg.getPrefixedP(player, "servershop.messages.sell-success-tax", Map.of(
                            "quantity", String.valueOf(qty), "item", variant.displayName(),
                            "net_total", msg.formatCurrency(net), "tax_amount", msg.formatCurrency(tax))));
                } else {
                    player.sendMessage(msg.getPrefixedP(player, "servershop.messages.sell-success", Map.of(
                            "quantity", String.valueOf(qty), "item", variant.displayName(),
                            "total", msg.formatCurrency(net))));
                }
                goBack(player);
            }
            case NOT_ENOUGH_ITEMS -> {
                SoundUtil.playErrorSound(player);
                player.sendMessage(msg.getPrefixedP(player, "servershop.messages.sell-no-items",
                        "quantity", String.valueOf(qty)));
                goBack(player);
            }
            case DREAM_ITEM_BLOCKED -> {
                SoundUtil.playErrorSound(player);
                player.sendMessage(msg.getPrefixedP(player, "messages.dream-item-blocked"));
                goBack(player);
            }
            case DAILY_LIMIT_REACHED -> {
                SoundUtil.playErrorSound(player);
                player.sendMessage(msg.getPrefixedP(player, "servershop.messages.daily-sell-cap"));
                updateDisplay(player);
            }
            default -> {
                SoundUtil.playErrorSound(player);
                player.sendMessage(msg.getPrefixedP(player, "servershop.messages.sell-failed"));
                updateDisplay(player);
            }
        }
    }

    private void goBack(Player player) {
        if (origin == Origin.SHOP_MENU) {
            shopGuiManager.openMainMenu(player);
        } else {
            shopGuiManager.openSellInventory(player);
        }
    }

    private void adjust(int delta, int maxQty, Player player) {
        pendingConfirm = false;
        if (delta > 1 && quantity == 1) {
            quantity = Math.min(delta, maxQty);
        } else {
            quantity = Math.max(1, Math.min(quantity + delta, maxQty));
        }
        SoundUtil.playClickSound(player);
        updateDisplay(player);
    }

    /** How many units of exactly this item the player currently holds in range. */
    private int held(Player player) {
        return plugin.getShopTransactionService().countVariant(player, variant.template(), slots);
    }

    @Override public GuiType getType() { return GuiType.SHOP_SELL_ONLY; }
    @Override public @NotNull Inventory getInventory() { return inventory; }
}
