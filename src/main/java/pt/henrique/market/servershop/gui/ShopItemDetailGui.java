package pt.henrique.market.servershop.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import pt.henrique.market.Market;
import pt.henrique.market.config.MessageManager;
import pt.henrique.market.gui.MarketGui;
import pt.henrique.market.servershop.SellVariantScanner;
import pt.henrique.market.servershop.model.SellVariant;
import pt.henrique.market.servershop.model.ShopItem;
import pt.henrique.market.servershop.model.ShopPriceEntry;
import pt.henrique.market.servershop.service.ShopTransactionService;
import pt.henrique.market.util.ItemBuilder;
import pt.henrique.market.util.SoundUtil;
import pt.henrique.market.util.TextUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Item detail GUI — quantity selector + central action icon.
 *
 * Buy/sell is triggered via the <b>action icon</b> in the center of row 5:
 * <ul>
 *   <li><b>Left-click</b> → Buy</li>
 *   <li><b>Right-click</b> → Sell</li>
 * </ul>
 *
 * Layout (54 slots / 6 rows):
 * <pre>
 *   Row 0: filler  (AUTO at slot 4)
 *   Row 1: [-32][-16][-8][-1] [PREVIEW] [+1][+8][+16][+32]
 *   Row 2: [MIN] filler×3 [QTY] filler×3 [MAX]
 *   Row 3: [-4pk][-3pk][-2pk][-1pk] [PKG] [+1pk][+2pk][+3pk][+4pk]
 *   Row 4: filler×9
 *   Row 5: filler×4 [ACTION] filler×3 [BACK]
 * </pre>
 *
 * Buying happens here. Selling does not: this screen is a price row, and a row
 * cannot say which of the player's bows to hand over — right-click routes to
 * {@link SellVariantDetailGui} for the one item they own, or to the sell grid
 * narrowed to that material when they own several.
 */
public class ShopItemDetailGui implements MarketGui {

    private final Market plugin;
    private final ShopGuiManager shopGuiManager;
    private final String itemId;
    private Inventory inventory;
    private int quantity = 1;

    /** Pending confirmation: null = none. */
    private ConfirmAction pendingConfirm = null;
    private enum ConfirmAction { BUY }

    // ── Row 0 ────────────────────────────────────────────
    private static final int SLOT_AUTO = 4;
    // ── Row 1 ────────────────────────────────────────────
    private static final int SLOT_PREVIEW = 13;
    private static final int SLOT_SUB_32 = 9, SLOT_SUB_16 = 10, SLOT_SUB_8 = 11, SLOT_SUB_1 = 12;
    private static final int SLOT_ADD_1 = 14, SLOT_ADD_8 = 15, SLOT_ADD_16 = 16, SLOT_ADD_32 = 17;
    // ── Row 2 ────────────────────────────────────────────
    private static final int SLOT_MIN = 18, SLOT_QTY_INFO = 22, SLOT_MAX = 26;
    // ── Row 3 (packs) ────────────────────────────────────
    private static final int SLOT_PACK_INFO = 31;
    private static final int[] SLOT_PACK_SUB = {30, 29, 28, 27};
    private static final int[] SLOT_PACK_ADD = {32, 33, 34, 35};
    // ── Row 5 ────────────────────────────────────────────
    private static final int SLOT_ACTION = 49;   // center of bottom row
    private static final int SLOT_BACK   = 53;

    private static final int MAX_PACK_BUTTONS = 4;

    private int maxStackSize = 64;
    private boolean showPackSelectors = false;
    private int packButtonCount = 4;

    public ShopItemDetailGui(Market plugin, ShopGuiManager shopGuiManager, String itemId) {
        this.plugin = plugin;
        this.shopGuiManager = shopGuiManager;
        this.itemId = itemId;
    }

    public void open(Player player) {
        MessageManager msg = plugin.getMessageManager();
        String lang = msg.getPlayerLanguage(player);
        ShopItem shopItem = plugin.getShopPriceManager().getShopItem(itemId);
        String itemName = shopItem != null ? shopItem.getDisplayName() : itemId;
        String title = msg.getRaw(lang, "servershop.titles.item-detail").replace("{item}", itemName);
        inventory = Bukkit.createInventory(this, 54, TextUtil.colorize(title));

        if (shopItem != null) maxStackSize = shopItem.getBaseMaterial().getMaxStackSize();
        boolean packEnabled = plugin.getConfig().getBoolean("gui.quantity.pack-selectors.enabled", true);
        int cfgPacks = plugin.getConfig().getInt("gui.quantity.pack-selectors.max-packs", MAX_PACK_BUTTONS);
        packButtonCount = Math.max(1, Math.min(cfgPacks, MAX_PACK_BUTTONS));
        showPackSelectors = packEnabled && maxStackSize > 1;

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
        ShopPriceEntry entry = plugin.getShopPriceManager().getPriceEntry(itemId);
        if (entry == null) return;
        ShopItem shopItem = plugin.getShopPriceManager().getShopItem(itemId);
        if (shopItem == null) return;

        var filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) inventory.setItem(i, filler);

        String itemName = shopItem.getDisplayName();
        double balance = plugin.getEconomyManager().getBalance(player.getUniqueId());
        String balStr = msg.formatCurrency(balance);

        // This screen is a price row, so it quotes the row's own price. What a
        // particular item is worth depends on its enchantments and wear, and that
        // is shown on the sell screen for that item.
        double adjSellPrice = entry.sellPrice();

        // ── Preview item (row 1 center) ──────────────────────
        List<String> prevLore = new ArrayList<>(msg.getList(lang, "servershop.lore.item-detail"));
        prevLore.replaceAll(l -> l
                .replace("{buy_price}", entry.buyEnabled() ? msg.formatCurrency(entry.buyPrice()) : "N/A")
                .replace("{sell_price}", entry.sellEnabled() ? msg.formatCurrency(adjSellPrice) : "N/A")
                .replace("{spread}", String.valueOf(entry.getSpreadPercent()))
                .replace("{balance}", balStr));
        if (entry.sellEnabled() && entry.sellPrice() > 0) {
            prevLore.add("");
            prevLore.add(msg.getRaw(lang, "servershop.lore.item-detail-sell-note"));
        }
        inventory.setItem(SLOT_PREVIEW, new ItemBuilder(shopItem.createItemStack(Math.min(quantity, 64)))
                .name("&f&l" + itemName).lore(prevLore).build());

        // ── Row 1: qty buttons ───────────────────────────────
        inventory.setItem(SLOT_SUB_32, qtyBtn(msg.getRaw(lang, "servershop.buttons.quantity-sub-32"), Material.RED_STAINED_GLASS_PANE));
        inventory.setItem(SLOT_SUB_16, qtyBtn(msg.getRaw(lang, "servershop.buttons.quantity-sub-16"), Material.RED_STAINED_GLASS_PANE));
        inventory.setItem(SLOT_SUB_8,  qtyBtn(msg.getRaw(lang, "servershop.buttons.quantity-sub-8"),  Material.RED_STAINED_GLASS_PANE));
        inventory.setItem(SLOT_SUB_1,  qtyBtn(msg.getRaw(lang, "servershop.buttons.quantity-sub-1"),  Material.RED_STAINED_GLASS_PANE));
        inventory.setItem(SLOT_ADD_1,  qtyBtn(msg.getRaw(lang, "servershop.buttons.quantity-add-1"),  Material.LIME_STAINED_GLASS_PANE));
        inventory.setItem(SLOT_ADD_8,  qtyBtn(msg.getRaw(lang, "servershop.buttons.quantity-add-8"),  Material.LIME_STAINED_GLASS_PANE));
        inventory.setItem(SLOT_ADD_16, qtyBtn(msg.getRaw(lang, "servershop.buttons.quantity-add-16"), Material.LIME_STAINED_GLASS_PANE));
        inventory.setItem(SLOT_ADD_32, qtyBtn(msg.getRaw(lang, "servershop.buttons.quantity-add-32"), Material.LIME_STAINED_GLASS_PANE));

        // ── Row 2 ────────────────────────────────────────────
        inventory.setItem(SLOT_MIN, new ItemBuilder(Material.RED_CONCRETE)
                .name(msg.getRaw(lang, "servershop.buttons.quantity-min")).build());
        inventory.setItem(SLOT_MAX, new ItemBuilder(Material.LIME_CONCRETE)
                .name(msg.getRaw(lang, "servershop.buttons.quantity-max"))
                .lore("&7Sets to maximum affordable/available").build());

        if (plugin.getConfig().getBoolean("server-shop.auto-quantity-button", true)) {
            int avail = plugin.getShopTransactionService().countItems(player, itemId);
            List<String> autoLore;
            if (avail > 0) {
                autoLore = new ArrayList<>(msg.getList(lang, "servershop.lore.auto-quantity"));
                int finalAvail = avail;
                autoLore.replaceAll(l -> l.replace("{available}", String.valueOf(finalAvail)));
            } else {
                autoLore = new ArrayList<>(msg.getList(lang, "servershop.lore.auto-quantity-zero"));
            }
            inventory.setItem(SLOT_AUTO, new ItemBuilder(avail > 0 ? Material.HOPPER : Material.GRAY_DYE)
                    .name(msg.getRaw(lang, "servershop.buttons.auto-quantity")).lore(autoLore).build());
        }

        double totalBuy = plugin.getShopPricingService().getTotalBuyPrice(itemId, quantity);
        inventory.setItem(SLOT_QTY_INFO, new ItemBuilder(Material.PAPER)
                .name("&e&lQuantity: &f" + quantity)
                .lore("", "&7Total: &a" + (totalBuy >= 0 ? msg.formatCurrency(totalBuy) : "N/A"),
                        "&7Balance: &f" + balStr).build());

        // ── Row 3: pack selectors ────────────────────────────
        if (showPackSelectors) {
            inventory.setItem(SLOT_PACK_INFO, new ItemBuilder(Material.CHEST)
                    .name(msg.getRaw(lang, "servershop.buttons.pack-info").replace("{stack_size}", String.valueOf(maxStackSize)))
                    .lore(msg.getRaw(lang, "servershop.lore.pack-info-line1").replace("{stack_size}", String.valueOf(maxStackSize)),
                          msg.getRaw(lang, "servershop.lore.pack-info-line2").replace("{stack_size}", String.valueOf(maxStackSize)))
                    .build());
            for (int i = 0; i < MAX_PACK_BUTTONS; i++) {
                int pk = i + 1;
                if (pk > packButtonCount) break;
                int delta = pk * maxStackSize;
                String sN = String.valueOf(pk), sD = String.valueOf(delta), sS = String.valueOf(maxStackSize);
                inventory.setItem(SLOT_PACK_SUB[i], new ItemBuilder(Material.ORANGE_STAINED_GLASS_PANE)
                        .name(msg.getRaw(lang, "servershop.buttons.pack-sub").replace("{packs}", sN).replace("{amount}", sD))
                        .lore(msg.getRaw(lang, "servershop.lore.pack-sub").replace("{packs}", sN).replace("{amount}", sD).replace("{stack_size}", sS)).build());
                inventory.setItem(SLOT_PACK_ADD[i], new ItemBuilder(Material.CYAN_STAINED_GLASS_PANE)
                        .name(msg.getRaw(lang, "servershop.buttons.pack-add").replace("{packs}", sN).replace("{amount}", sD))
                        .lore(msg.getRaw(lang, "servershop.lore.pack-add").replace("{packs}", sN).replace("{amount}", sD).replace("{stack_size}", sS)).build());
            }
        }

        // ── Row 5: action icon + back ────────────────────────
        buildActionIcon(player, entry, shopItem, itemName, balStr, adjSellPrice, msg, lang);
        inventory.setItem(SLOT_BACK, new ItemBuilder(Material.BARRIER).name(msg.getRaw(lang, "servershop.buttons.back")).build());
    }

    /**
     * Builds the central action icon (slot 49).
     * Normal state shows buy/sell prices + click hints.
     * Confirm state shows transaction details + "click again" prompt.
     */
    private void buildActionIcon(Player player, ShopPriceEntry entry, ShopItem shopItem,
                                  String itemName, String balStr, double adjSellPrice,
                                  MessageManager msg, String lang) {
        double totalBuy = plugin.getShopPricingService().getTotalBuyPrice(itemId, quantity);
        double balance = plugin.getEconomyManager().getBalance(player.getUniqueId());
        int avail = plugin.getShopTransactionService().countItems(player, itemId);

        // ── Confirm: BUY ─────────────────────────────────────
        if (pendingConfirm == ConfirmAction.BUY) {
            double cost = totalBuy >= 0 ? totalBuy : 0;
            double rem = balance - cost;
            List<String> lore = new ArrayList<>(msg.getList(lang, "servershop.lore.confirm-buy-action"));
            lore.replaceAll(l -> l
                    .replace("{item}", itemName).replace("{quantity}", String.valueOf(quantity))
                    .replace("{total}", msg.formatCurrency(cost))
                    .replace("{price_each}", msg.formatCurrency(entry.buyPrice()))
                    .replace("{balance}", balStr)
                    .replace("{remaining}", msg.formatCurrency(Math.max(0, rem))));
            lore.add("");
            lore.add(msg.getRaw(lang, "servershop.lore.action-confirm-buy"));
            inventory.setItem(SLOT_ACTION, new ItemBuilder(Material.LIME_CONCRETE)
                    .name(msg.getRaw(lang, "servershop.buttons.confirm-buy")).lore(lore).glow().build());
            return;
        }

        // ── Normal: action icon with buy/sell info ───────────
        ItemStack icon = shopItem.createItemStack(Math.min(quantity, 64));
        List<String> lore = new ArrayList<>();
        lore.add("");

        boolean canBuy  = entry.buyEnabled() && entry.buyPrice() > 0;
        boolean canSell = entry.sellEnabled() && entry.sellPrice() > 0;

        if (canBuy) {
            double cost = totalBuy >= 0 ? totalBuy : 0;
            lore.add(msg.getRaw(lang, "servershop.lore.action-buy-price")
                    .replace("{price}", msg.formatCurrency(entry.buyPrice()))
                    .replace("{total}", msg.formatCurrency(cost)));
            if (balance < cost) lore.add("  &c(Cannot afford)");
        } else {
            lore.add(msg.getRaw(lang, "servershop.lore.action-buy-disabled"));
        }

        lore.add("");

        if (canSell) {
            double st = Math.round(adjSellPrice * quantity * 100.0) / 100.0;
            double tax = plugin.getShopPricingService().calculateTax(st);
            double net = st - tax;
            lore.add(msg.getRaw(lang, "servershop.lore.action-sell-price")
                    .replace("{price}", msg.formatCurrency(adjSellPrice))
                    .replace("{total}", msg.formatCurrency(net)));
            if (avail < quantity) lore.add("  &c(Need " + quantity + ", have " + avail + ")");
        } else {
            lore.add(msg.getRaw(lang, "servershop.lore.action-sell-disabled"));
        }

        lore.add("");
        lore.add("&7&m─────────────────");
        lore.add("&7Quantity: &f" + quantity);
        lore.add("&7Balance: &f" + balStr);
        lore.add("");
        lore.add(msg.getRaw(lang, "servershop.lore.action-hint-buy"));
        lore.add(msg.getRaw(lang, "servershop.lore.action-hint-sell"));

        inventory.setItem(SLOT_ACTION, new ItemBuilder(icon).name("&e&l" + itemName).lore(lore).build());
    }

    private ItemStack qtyBtn(String name, Material mat) {
        return new ItemBuilder(mat).name(name).build();
    }

    /* ════════════════════════════════════════════════════════
     *  Click handling
     * ════════════════════════════════════════════════════════ */

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventory) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        int maxQty = plugin.getConfig().getInt("server-shop.max-transaction-quantity", 2304);

        switch (slot) {
            case SLOT_SUB_32 -> { resetConfirm(); adj(-32, maxQty, player); }
            case SLOT_SUB_16 -> { resetConfirm(); adj(-16, maxQty, player); }
            case SLOT_SUB_8  -> { resetConfirm(); adj(-8, maxQty, player); }
            case SLOT_SUB_1  -> { resetConfirm(); adj(-1, maxQty, player); }
            case SLOT_ADD_1  -> { resetConfirm(); adj(1, maxQty, player); }
            case SLOT_ADD_8  -> { resetConfirm(); adj(8, maxQty, player); }
            case SLOT_ADD_16 -> { resetConfirm(); adj(16, maxQty, player); }
            case SLOT_ADD_32 -> { resetConfirm(); adj(32, maxQty, player); }
            case SLOT_MIN -> { resetConfirm(); quantity = 1; SoundUtil.playClickSound(player); updateDisplay(player); }
            case SLOT_MAX -> {
                resetConfirm();
                int mB = plugin.getShopTransactionService().getMaxBuyQuantity(player, itemId);
                int mS = plugin.getShopTransactionService().getMaxSellQuantity(player, itemId);
                quantity = Math.max(1, Math.min(Math.max(mB, mS), maxQty));
                SoundUtil.playClickSound(player); updateDisplay(player);
            }
            case SLOT_AUTO -> handleAutoQty(player, maxQty);
            case SLOT_ACTION -> handleActionClick(event.getClick(), player);
            case SLOT_BACK -> {
                SoundUtil.playClickSound(player);
                var cat = plugin.getShopPriceManager().getCategoryFor(itemId);
                if (cat != null) shopGuiManager.openCategory(player, cat, 0);
                else shopGuiManager.openMainMenu(player);
            }
            default -> { if (showPackSelectors) handlePackClick(slot, maxQty, player); }
        }
    }

    /** Routes the click on the action icon. */
    private void handleActionClick(ClickType click, Player player) {
        boolean left  = click.isLeftClick();
        boolean right = click.isRightClick();

        if (pendingConfirm != null) {
            if (pendingConfirm == ConfirmAction.BUY && left) { executeBuy(player); return; }
            // Wrong click type → cancel confirm
            resetConfirm(); SoundUtil.playClickSound(player); updateDisplay(player);
            return;
        }

        // Left = buy, right = sell
        if (left) {
            handleBuyIntent(player);
        } else if (right) {
            handleSellIntent(player);
        }
    }

    // ── Buy ──────────────────────────────────────────────

    private void handleBuyIntent(Player player) {
        ShopPriceEntry entry = plugin.getShopPriceManager().getPriceEntry(itemId);
        if (entry == null || !entry.buyEnabled()) {
            SoundUtil.playErrorSound(player);
            player.sendMessage(plugin.getMessageManager().getPrefixedP(player, "servershop.messages.buy-disabled"));
            return;
        }
        if (plugin.getConfig().getBoolean("server-shop.sell-confirmation", true)) {
            pendingConfirm = ConfirmAction.BUY;
            SoundUtil.playClickSound(player); updateDisplay(player);
        } else { executeBuy(player); }
    }

    private void executeBuy(Player player) {
        pendingConfirm = null;
        MessageManager msg = plugin.getMessageManager();
        ShopPriceEntry entry = plugin.getShopPriceManager().getPriceEntry(itemId);
        if (entry == null || !entry.buyEnabled()) { SoundUtil.playErrorSound(player); return; }
        ShopItem shopItem = plugin.getShopPriceManager().getShopItem(itemId);
        String itemName = shopItem != null ? shopItem.getDisplayName() : itemId;
        double totalCost = plugin.getShopPricingService().getTotalBuyPrice(itemId, quantity);
        int qty = quantity;
        ShopTransactionService.TransactionResult r = plugin.getShopTransactionService().buyItem(player, itemId, qty);
        switch (r) {
            case SUCCESS -> {
                SoundUtil.playPurchaseSound(player);
                player.sendMessage(msg.getPrefixedP(player, "servershop.messages.buy-success", Map.of(
                        "quantity", String.valueOf(qty), "item", itemName, "total", msg.formatCurrency(totalCost))));
                updateDisplay(player);
            }
            case NOT_ENOUGH_MONEY -> { SoundUtil.playErrorSound(player); player.sendMessage(msg.getPrefixedP(player, "servershop.messages.buy-no-money", "total", msg.formatCurrency(totalCost))); }
            case INVENTORY_FULL   -> { SoundUtil.playErrorSound(player); player.sendMessage(msg.getPrefixedP(player, "servershop.messages.buy-inventory-full")); }
            default               -> { SoundUtil.playErrorSound(player); player.sendMessage(msg.getPrefixedP(player, "servershop.messages.buy-failed")); }
        }
    }

    // ── Sell ─────────────────────────────────────────────

    /**
     * Routes selling to the concrete item the player owns.
     * <p>
     * This screen is reached by browsing a price row, which says nothing about
     * <em>which</em> of the player's bows should go. So: one matching item sells
     * straight away, several open the sell grid narrowed to that material, and the
     * player picks. Selling from the row itself is what used to hand over the good
     * gear when a junk copy was meant.
     */
    private void handleSellIntent(Player player) {
        ShopPriceEntry entry = plugin.getShopPriceManager().getPriceEntry(itemId);
        if (entry == null || !entry.sellEnabled()) {
            SoundUtil.playErrorSound(player);
            player.sendMessage(plugin.getMessageManager().getPrefixedP(player, "servershop.messages.sell-disabled"));
            return;
        }

        ShopItem shopItem = plugin.getShopPriceManager().getShopItem(itemId);
        if (shopItem == null) {
            SoundUtil.playErrorSound(player);
            player.sendMessage(plugin.getMessageManager().getPrefixedP(player, "servershop.messages.sell-disabled"));
            return;
        }

        boolean shulkerSell = plugin.getConfig().getBoolean("sell-inventory.shulker-sell.enabled", true);
        List<SellVariant> owned = new ArrayList<>();
        for (SellVariant variant : plugin.getSellVariantScanner()
                .scanStorage(player, SellVariantScanner.Options.showAll(shulkerSell)).variants()) {
            if (variant.template().getType() == shopItem.getBaseMaterial()) owned.add(variant);
        }

        if (owned.isEmpty()) {
            SoundUtil.playErrorSound(player);
            player.sendMessage(plugin.getMessageManager().getPrefixedP(player,
                    "servershop.messages.sell-no-items", "quantity", String.valueOf(quantity)));
            return;
        }

        SoundUtil.playClickSound(player);
        if (owned.size() == 1) {
            shopGuiManager.openSellVariantDetail(player, owned.get(0),
                    ShopTransactionService.STORAGE_SLOTS, SellVariantDetailGui.Origin.SELL_INVENTORY);
        } else {
            player.sendMessage(plugin.getMessageManager().getPrefixedP(player,
                    "servershop.messages.sell-pick-variant",
                    "count", String.valueOf(owned.size())));
            shopGuiManager.openSellInventory(player, 0, shopItem.getBaseMaterial());
        }
    }

    // ── Utility ──────────────────────────────────────────

    private void handlePackClick(int slot, int maxQty, Player player) {
        for (int i = 0; i < packButtonCount; i++) {
            if (slot == SLOT_PACK_SUB[i]) { resetConfirm(); adj(-((i+1)*maxStackSize), maxQty, player); return; }
            if (slot == SLOT_PACK_ADD[i]) { resetConfirm(); adj( (i+1)*maxStackSize,  maxQty, player); return; }
        }
    }

    private void handleAutoQty(Player player, int maxQty) {
        if (!plugin.getConfig().getBoolean("server-shop.auto-quantity-button", true)) return;
        resetConfirm();
        int avail = plugin.getShopTransactionService().countItems(player, itemId);
        if (avail <= 0) { SoundUtil.playErrorSound(player); return; }
        quantity = Math.max(1, Math.min(avail, maxQty));
        SoundUtil.playClickSound(player); updateDisplay(player);
    }

    private void resetConfirm() { pendingConfirm = null; }

    private void adj(int delta, int maxQty, Player player) {
        // If at minimum (1) and incrementing by more than 1, set directly to the
        // increment value so +8 gives 8 instead of 9, +16 gives 16, etc.
        if (delta > 1 && quantity == 1) {
            quantity = Math.min(delta, maxQty);
        } else {
            quantity = Math.max(1, Math.min(quantity + delta, maxQty));
        }
        SoundUtil.playClickSound(player); updateDisplay(player);
    }

    @Override public GuiType getType() { return GuiType.SHOP_ITEM_DETAIL; }
    @Override public @NotNull Inventory getInventory() { return inventory; }
}
