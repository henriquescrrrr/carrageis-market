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
import pt.henrique.market.util.ItemBuilder;
import pt.henrique.market.util.SoundUtil;
import pt.henrique.market.util.TextUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Percentage selector for the multi-select "sell to server" flow.
 * <p>
 * Mirrors the slot layout of {@link ShopItemDetailGui} so players keep their
 * muscle memory, but every control sets a <b>percentage</b> applied to the total
 * amount of each selected item type:
 * <pre>
 *   Slot 4 (AUTO)            → 100% (sell everything selected)
 *   Slot 13 (PREVIEW)        → summary: types, %, gross/tax/net
 *   Row 1 (9–12, 14–17)      → primary percentage presets (config)
 *   Slot 18 (MIN)            → 1%      Slot 26 (MAX) → 100%
 *   Slot 22 (QTY_INFO)       → Custom % picker
 *   Row 3 (27–30, 32–35)     → secondary percentage presets (config)
 *   Slot 49 (ACTION)         → Preview & Sell → confirmation GUI
 *   Slot 53 (BACK)           → back to Sell Items (selection preserved)
 * </pre>
 */
public class ShopMultiSellQuantityGui implements MarketGui {

    private final Market plugin;
    private final ShopGuiManager shopGuiManager;
    private Inventory inventory;

    // ── Slot layout (mirrors ShopItemDetailGui) ──────────────
    private static final int SLOT_AUTO = 4;
    private static final int SLOT_PREVIEW = 13;
    private static final int[] ROW1_SLOTS = {9, 10, 11, 12, 14, 15, 16, 17};
    private static final int SLOT_MIN = 18, SLOT_CUSTOM = 22, SLOT_MAX = 26;
    private static final int[] ROW3_SLOTS = {27, 28, 29, 30, 32, 33, 34, 35};
    private static final int SLOT_ACTION = 49;
    private static final int SLOT_BACK = 53;

    private static final List<Integer> DEFAULT_PRIMARY = List.of(10, 20, 25, 40, 50, 60, 75, 90);
    private static final List<Integer> DEFAULT_SECONDARY = List.of(5, 15, 30, 33, 66, 70, 80, 95);

    public ShopMultiSellQuantityGui(Market plugin, ShopGuiManager shopGuiManager) {
        this.plugin = plugin;
        this.shopGuiManager = shopGuiManager;
    }

    public void open(Player player) {
        MessageManager msg = plugin.getMessageManager();
        String lang = msg.getPlayerLanguage(player);
        String title = msg.getRaw(lang, "servershop.titles.multi-sell-percent");
        inventory = Bukkit.createInventory(this, 54, TextUtil.colorize(title));

        render(player);

        plugin.getGuiManager().registerGui(player.getUniqueId(), this);
        shopGuiManager.registerGui(player.getUniqueId(), this);
        player.openInventory(inventory);
    }

    /* ════════════════════════════════════════════════════════
     *  Rendering
     * ════════════════════════════════════════════════════════ */

    private void render(Player player) {
        MessageManager msg = plugin.getMessageManager();
        String lang = msg.getPlayerLanguage(player);
        MultiSellSession session = shopGuiManager.getMultiSellSession(player.getUniqueId());
        int percent = session.getPercent();

        var filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) inventory.setItem(i, filler);

        // ── Auto = 100% ──────────────────────────────────────
        ItemBuilder auto = new ItemBuilder(Material.HOPPER)
                .name(msg.getRaw(lang, "servershop.buttons.multi-auto"))
                .lore(msg.getList(lang, "servershop.lore.multi-auto"));
        if (percent >= 100) auto.glow();
        inventory.setItem(SLOT_AUTO, auto.build());

        // ── Preview / summary panel ──────────────────────────
        inventory.setItem(SLOT_PREVIEW, buildPreview(player, session, percent, msg, lang));

        // ── Preset rows ──────────────────────────────────────
        placePresets(ROW1_SLOTS, readPresets("primary-presets", DEFAULT_PRIMARY),
                Material.LIME_STAINED_GLASS_PANE, percent, msg, lang);
        placePresets(ROW3_SLOTS, readPresets("secondary-presets", DEFAULT_SECONDARY),
                Material.CYAN_STAINED_GLASS_PANE, percent, msg, lang);

        // ── Min / Max ────────────────────────────────────────
        ItemBuilder min = new ItemBuilder(Material.RED_CONCRETE)
                .name("&c&l1%")
                .lore(msg.getRaw(lang, "servershop.lore.multi-percent-set").replace("{percent}", "1"));
        if (percent == 1) min.glow();
        inventory.setItem(SLOT_MIN, min.build());

        ItemBuilder max = new ItemBuilder(Material.LIME_CONCRETE)
                .name("&a&l100%")
                .lore(msg.getRaw(lang, "servershop.lore.multi-percent-set").replace("{percent}", "100"));
        if (percent >= 100) max.glow();
        inventory.setItem(SLOT_MAX, max.build());

        // ── Custom % ─────────────────────────────────────────
        inventory.setItem(SLOT_CUSTOM, new ItemBuilder(Material.NAME_TAG)
                .name(msg.getRaw(lang, "servershop.buttons.multi-custom-percent"))
                .lore(msg.getList(lang, "servershop.lore.multi-custom-percent"))
                .build());

        // ── Action: Preview & Sell ───────────────────────────
        int eligible = countEligible(player, session, percent);
        List<String> actionLore = new ArrayList<>();
        actionLore.add("");
        actionLore.add(msg.getRaw(lang, "servershop.lore.multi-info-percent").replace("{percent}", String.valueOf(percent)));
        actionLore.add(msg.getRaw(lang, "servershop.lore.multi-info-types").replace("{types}", String.valueOf(eligible)));
        actionLore.add("");
        actionLore.add(msg.getRaw(lang, eligible > 0
                ? "servershop.lore.multi-action-hint"
                : "servershop.lore.multi-action-empty"));
        inventory.setItem(SLOT_ACTION, new ItemBuilder(eligible > 0 ? Material.LIME_WOOL : Material.GRAY_DYE)
                .name(msg.getRaw(lang, "servershop.buttons.multi-action"))
                .lore(actionLore)
                .glow(eligible > 0)
                .build());

        // ── Back ─────────────────────────────────────────────
        inventory.setItem(SLOT_BACK, new ItemBuilder(Material.BARRIER)
                .name(msg.getRaw(lang, "servershop.buttons.back")).build());
    }

    private ItemStack buildPreview(Player player, MultiSellSession session, int percent,
                                   MessageManager msg, String lang) {
        double gross = 0;
        int eligible = 0;
        int totalUnits = 0;
        int maxTx = plugin.getConfig().getInt("server-shop.max-transaction-quantity", 2304);
        List<SellVariant> held = heldVariants(player);
        for (ItemStack template : session.getSelected()) {
            SellVariant variant = SellVariantScanner.find(held, template);
            if (variant == null) continue;
            int qty = Math.min(MultiSellSession.applyPercent(variant.amount(), percent), maxTx);
            if (qty <= 0) continue;
            gross += Math.round(variant.unitPrice() * qty * 100.0) / 100.0;
            totalUnits += qty;
            eligible++;
        }
        double tax = plugin.getShopPricingService().calculateTax(gross);
        double net = gross - tax;

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(msg.getRaw(lang, "servershop.lore.multi-info-percent").replace("{percent}", String.valueOf(percent)));
        lore.add(msg.getRaw(lang, "servershop.lore.multi-info-types").replace("{types}", String.valueOf(eligible)));
        lore.add(msg.getRaw(lang, "servershop.lore.multi-info-units").replace("{units}", String.valueOf(totalUnits)));
        lore.add("");
        lore.add(msg.getRaw(lang, "servershop.lore.multi-info-gross").replace("{gross}", msg.formatCurrency(gross)));
        if (tax > 0)
            lore.add(msg.getRaw(lang, "servershop.lore.multi-info-tax").replace("{tax}", msg.formatCurrency(tax)));
        lore.add(msg.getRaw(lang, "servershop.lore.multi-info-net").replace("{net}", msg.formatCurrency(net)));

        return new ItemBuilder(Material.PAPER)
                .name(msg.getRaw(lang, "servershop.buttons.multi-preview"))
                .lore(lore)
                .amount(Math.max(1, Math.min(64, percent)))
                .glow()
                .build();
    }

    private void placePresets(int[] slots, List<Integer> presets, Material mat, int percent,
                              MessageManager msg, String lang) {
        for (int i = 0; i < slots.length && i < presets.size(); i++) {
            int value = presets.get(i);
            ItemBuilder b = new ItemBuilder(mat)
                    .name("&b&l" + value + "%")
                    .lore(msg.getRaw(lang, "servershop.lore.multi-percent-set").replace("{percent}", String.valueOf(value)))
                    .amount(Math.max(1, Math.min(64, value)));
            if (value == percent) b.glow();
            inventory.setItem(slots[i], b.build());
        }
    }

    /** Reads a preset list from config, keeping only values in 1–100. */
    private List<Integer> readPresets(String key, List<Integer> fallback) {
        List<Integer> raw = plugin.getConfig().getIntegerList("sell-inventory.multi-select." + key);
        if (raw == null || raw.isEmpty()) return fallback;
        List<Integer> valid = new ArrayList<>();
        for (int v : raw) if (v >= 1 && v <= 100) valid.add(v);
        return valid.isEmpty() ? fallback : valid;
    }

    /* ════════════════════════════════════════════════════════
     *  Eligibility / pricing helpers (shared logic with confirm GUI)
     * ════════════════════════════════════════════════════════ */

    /** The distinct sellable items the player currently holds in their backpack. */
    private List<SellVariant> heldVariants(Player player) {
        boolean shulkerSell = plugin.getConfig().getBoolean("sell-inventory.shulker-sell.enabled", true);
        return plugin.getSellVariantScanner()
                .scanStorage(player, SellVariantScanner.Options.showAll(shulkerSell)).variants();
    }

    /** How many of the selected items would actually sell at this percentage. */
    private int countEligible(Player player, MultiSellSession session, int percent) {
        int maxTx = plugin.getConfig().getInt("server-shop.max-transaction-quantity", 2304);
        List<SellVariant> held = heldVariants(player);
        int count = 0;
        for (ItemStack template : session.getSelected()) {
            SellVariant variant = SellVariantScanner.find(held, template);
            if (variant == null) continue;
            if (Math.min(MultiSellSession.applyPercent(variant.amount(), percent), maxTx) > 0) count++;
        }
        return count;
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
        MultiSellSession session = shopGuiManager.getMultiSellSession(player.getUniqueId());

        if (slot == SLOT_BACK) {
            SoundUtil.playClickSound(player);
            shopGuiManager.openSellInventory(player); // selection preserved
            return;
        }
        if (slot == SLOT_CUSTOM) {
            SoundUtil.playClickSound(player);
            openCustomPercent(player, session);
            return;
        }
        if (slot == SLOT_ACTION) {
            if (countEligible(player, session, session.getPercent()) <= 0) {
                SoundUtil.playErrorSound(player);
                player.sendMessage(plugin.getMessageManager().getPrefixedP(player, "servershop.messages.multi-none-eligible"));
                return;
            }
            SoundUtil.playClickSound(player);
            shopGuiManager.openMultiSellConfirm(player);
            return;
        }
        if (slot == SLOT_AUTO || slot == SLOT_MAX) {
            setPercent(player, session, 100);
            return;
        }
        if (slot == SLOT_MIN) {
            setPercent(player, session, 1);
            return;
        }

        Integer preset = presetAt(slot);
        if (preset != null) {
            setPercent(player, session, preset);
        }
    }

    private void setPercent(Player player, MultiSellSession session, int value) {
        session.setPercent(value);
        SoundUtil.playClickSound(player);
        render(player);
    }

    /** Returns the preset value mapped to a row-1/row-3 slot, or null. */
    private Integer presetAt(int slot) {
        List<Integer> primary = readPresets("primary-presets", DEFAULT_PRIMARY);
        for (int i = 0; i < ROW1_SLOTS.length && i < primary.size(); i++)
            if (ROW1_SLOTS[i] == slot) return primary.get(i);
        List<Integer> secondary = readPresets("secondary-presets", DEFAULT_SECONDARY);
        for (int i = 0; i < ROW3_SLOTS.length && i < secondary.size(); i++)
            if (ROW3_SLOTS[i] == slot) return secondary.get(i);
        return null;
    }

    private void openCustomPercent(Player player, MultiSellSession session) {
        MessageManager msg = plugin.getMessageManager();
        String lang = msg.getPlayerLanguage(player);
        String title = msg.getRaw(lang, "servershop.titles.percent-custom");
        plugin.getGuiManager().openPercentInput(player, value -> {
            if (value > 0) session.setPercent(value);
            // PercentInputGui closes the inventory before this fires → reopen.
            shopGuiManager.openMultiSellQuantity(player);
        }, session.getPercent(), title);
    }

    @Override public GuiType getType() { return GuiType.SHOP_MULTI_SELL_QTY; }
    @Override public @NotNull Inventory getInventory() { return inventory; }
}
