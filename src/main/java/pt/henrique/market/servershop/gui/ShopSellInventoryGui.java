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
import pt.henrique.market.hook.ToolModsHook;
import pt.henrique.market.servershop.MaterialUtil;
import pt.henrique.market.servershop.MultiSellSession;
import pt.henrique.market.servershop.ShulkerUtil;
import pt.henrique.market.servershop.model.ShopItem;
import pt.henrique.market.servershop.service.ShopTransactionService.SellPriceBreakdown;
import pt.henrique.market.servershop.service.ShulkerSellService;
import pt.henrique.market.util.ItemBuilder;
import pt.henrique.market.util.SoundUtil;
import pt.henrique.market.util.TextUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GUI that displays sellable items from the player's inventory.
 * <p>
 * <b>Layout (54 slots / 6 rows):</b>
 * <ul>
 *   <li>Row 0 (slots 0–8): <b>Equipped Gear</b> — Helmet (0), Chestplate (1),
 *       Leggings (2), Boots (3), Offhand/Shield (4), then yellow filler (5–8).
 *       Visually separated with yellow glass panes.</li>
 *   <li>Rows 1–4 (slots 9–44): Regular inventory sellable items.</li>
 *   <li>Row 5 (slots 45–53): Navigation bar — gray filler + Back button (53).</li>
 * </ul>
 * <p>
 * By default, equipped gear cannot be sold (shows a warning on click).
 * Config toggle: {@code sell-inventory.allow-equipped-selling: false}
 */
public class ShopSellInventoryGui implements MarketGui {

    private final Market plugin;
    private final ShopGuiManager shopGuiManager;
    private Inventory inventory;

    /** Grid entries for rows 1–4 (index = slot − 9): aggregated items or individual shulkers-with-contents. */
    private final List<GridEntry> gridEntries = new ArrayList<>();

    private sealed interface GridEntry permits AggregatedEntry, ShulkerEntry {}
    /** A normal sellable item type, aggregated by item ID. */
    private record AggregatedEntry(String itemId, int amount) implements GridEntry {}
    /** A single shulker box that contains items, tied to its player-inventory slot. */
    private record ShulkerEntry(int playerSlot, ItemStack snapshot) implements GridEntry {}

    /** Maps GUI slot → equipped gear type for the armor row (row 0). */
    private static final int SLOT_HELMET = 0;
    private static final int SLOT_CHESTPLATE = 1;
    private static final int SLOT_LEGGINGS = 2;
    private static final int SLOT_BOOTS = 3;
    private static final int SLOT_OFFHAND = 4;
    // Slots 5–8: yellow filler labeling the row
    private static final int ARMOR_ROW_END = 8;
    private static final int SLOT_SELL_ALL = 45;
    private static final int SLOT_MULTI_TOGGLE = 46;
    private static final int SLOT_MULTI_CLEAR = 48;
    private static final int SLOT_MULTI_CONTINUE = 50;
    private static final int SLOT_BACK = 53;

    /** Stores the item ID for each armor slot, null if no sellable gear. */
    private final String[] equippedItemIds = new String[5]; // 0=helmet..4=offhand
    /** Stores the actual ItemStack for durability/enchant calculations. */
    private final ItemStack[] equippedStacks = new ItemStack[5];
    /** Whether we're pending confirm on an armor slot (-1 = none). */
    private int pendingArmorConfirmSlot = -1;

    public ShopSellInventoryGui(Market plugin, ShopGuiManager shopGuiManager) {
        this.plugin = plugin;
        this.shopGuiManager = shopGuiManager;
    }

    public void open(Player player) {
        MessageManager msg = plugin.getMessageManager();
        String lang = msg.getPlayerLanguage(player);
        String title = msg.getRaw(lang, "servershop.titles.sell-inventory");
        inventory = Bukkit.createInventory(this, 54, TextUtil.colorize(title));

        MultiSellSession session = shopGuiManager.getMultiSellSession(player.getUniqueId());
        boolean multiEnabled = plugin.getConfig().getBoolean("sell-inventory.multi-select.enabled", true);
        boolean allowEquipped = plugin.getConfig().getBoolean("sell-inventory.allow-equipped-selling", false);

        // ── Row 0: Equipped Gear ──────────────────────────────────
        var yellowFiller = new ItemBuilder(Material.YELLOW_STAINED_GLASS_PANE)
                .name("&e&lEquipped Gear").lore("&7Items you are wearing.").build();

        // Fill armor row label slots (5–8) with yellow filler
        for (int i = 5; i <= ARMOR_ROW_END; i++) {
            inventory.setItem(i, yellowFiller);
        }

        // Populate equipped gear slots
        ItemStack[] armor = player.getInventory().getArmorContents(); // boots, leggings, chestplate, helmet
        ItemStack offhand = player.getInventory().getItemInOffHand();

        populateArmorSlot(player, SLOT_HELMET, armor.length > 3 ? armor[3] : null, msg, allowEquipped);
        populateArmorSlot(player, SLOT_CHESTPLATE, armor.length > 2 ? armor[2] : null, msg, allowEquipped);
        populateArmorSlot(player, SLOT_LEGGINGS, armor.length > 1 ? armor[1] : null, msg, allowEquipped);
        populateArmorSlot(player, SLOT_BOOTS, armor.length > 0 ? armor[0] : null, msg, allowEquipped);
        populateArmorSlot(player, SLOT_OFFHAND, (offhand != null && !offhand.getType().isAir()) ? offhand : null, msg, allowEquipped);

        // ── Rows 1–4: Regular inventory sellable items ────────────
        // Shulkers WITH contents are shown individually (by slot); everything else aggregates by id.
        boolean shulkerSellEnabled = plugin.getConfig().getBoolean("sell-inventory.shulker-sell.enabled", true);
        Map<String, Integer> sellableItems = new LinkedHashMap<>();
        List<ShulkerEntry> shulkerEntries = new ArrayList<>();
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            ItemStack stack = storage[i];
            if (stack == null || stack.getType().isAir()) continue;
            // Skip BuildersDream creative dream items
            if (plugin.getBuildersDreamHook() != null && plugin.getBuildersDreamHook().isDreamItem(stack)) continue;
            // Shulker box with contents → individual entry (not aggregated, not %-selectable)
            if (shulkerSellEnabled && ShulkerUtil.isShulkerBox(stack) && ShulkerUtil.hasContents(stack)) {
                shulkerEntries.add(new ShulkerEntry(i, stack.clone()));
                continue;
            }
            String itemId = plugin.getShopPriceManager().identifyItem(stack);
            if (itemId == null) continue;
            if (!plugin.getShopPricingService().isSellable(itemId)) continue;
            sellableItems.merge(itemId, stack.getAmount(), Integer::sum);
        }

        gridEntries.clear();
        gridEntries.addAll(shulkerEntries); // shulkers-with-contents first
        for (Map.Entry<String, Integer> e : sellableItems.entrySet()) {
            gridEntries.add(new AggregatedEntry(e.getKey(), e.getValue()));
        }

        int slot = 9; // Start at row 1
        for (GridEntry ge : gridEntries) {
            if (slot >= 45) break; // Stop before navigation row
            if (ge instanceof ShulkerEntry se) {
                renderShulkerEntry(slot, se, msg, lang);
                slot++;
                continue;
            }
            AggregatedEntry ae = (AggregatedEntry) ge;
            String itemId = ae.itemId();
            int amount = ae.amount();

            ShopItem shopItem = plugin.getShopPriceManager().getShopItem(itemId);
            String itemName = shopItem != null ? shopItem.getDisplayName() : itemId;

            double baseSellPrice = plugin.getShopPriceManager().getSellPrice(itemId);
            // Get a representative stack for durability/enchant calculations
            ItemStack repStack = plugin.getShopTransactionService().findFirstMatchingStack(player, shopItem);
            SellPriceBreakdown breakdown = plugin.getShopTransactionService()
                    .computeSellBreakdown(baseSellPrice, repStack);

            double total = Math.round(breakdown.finalSell() * amount * 100.0) / 100.0;

            List<String> lore = new ArrayList<>();
            lore.add("&7Amount: &f" + amount);
            lore.add("&7Price Each: &a" + msg.formatCurrency(breakdown.finalSell()));
            lore.add("&7Total: &a" + msg.formatCurrency(total));
            if (breakdown.enchantBonus() > 0 || breakdown.modBonus() > 0 || breakdown.wearPenalty() > 0) {
                lore.add("");
                lore.add("&eSell Breakdown:");
                lore.add("  &7Base: &f" + msg.formatCurrency(breakdown.baseSell()));
                if (breakdown.enchantBonus() > 0)
                    lore.add("  &7Enchant: &a+" + msg.formatCurrency(breakdown.enchantBonus()));
                if (breakdown.modBonus() > 0) {
                    java.util.Map<String, Long> modBreakdown = ToolModsHook.getModBreakdown(repStack);
                    for (var modEntry : modBreakdown.entrySet()) {
                        lore.add("  &7Mod: &a+" + msg.formatCurrency(pt.henrique.market.economy.EconomyManager.toDouble(modEntry.getValue()))
                                + " &8(" + modEntry.getKey() + ")");
                    }
                }
                if (breakdown.wearPenalty() > 0)
                    lore.add("  &7Wear: &c-" + msg.formatCurrency(breakdown.wearPenalty()));
                else if (breakdown.wearDescription().contains("Mending"))
                    lore.add("  &7Wear: &aNone (Mending)");
            }
            boolean selected = multiEnabled && session.isActive() && session.isSelected(itemId);
            lore.add("");
            if (multiEnabled && session.isActive()) {
                lore.add(msg.getRaw(lang, selected
                        ? "servershop.lore.multi-deselect-hint"
                        : "servershop.lore.multi-select-hint"));
                if (selected) lore.add(msg.getRaw(lang, "servershop.lore.multi-selected"));
            } else {
                lore.add("&eClick to sell!");
            }

            ItemStack baseStack = shopItem != null ? shopItem.createItemStack(Math.min(amount, 64))
                    : new ItemStack(Material.BARRIER);
            ItemBuilder itemBuilder = new ItemBuilder(baseStack).name("&f" + itemName).lore(lore);
            if (selected) itemBuilder.glow();
            inventory.setItem(slot, itemBuilder.build());
            slot++;
        }

        // Fill empty item slots with gray filler
        var grayFiller = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = slot; i < 45; i++) {
            inventory.setItem(i, grayFiller);
        }

        // ── Row 5: Navigation ─────────────────────────────────────
        for (int i = 45; i < 54; i++) inventory.setItem(i, grayFiller);

        // Sell Entire Inventory button
        boolean sellAllEnabled = plugin.getConfig().getBoolean("sell-inventory.sell-all.enabled", true);
        if (sellAllEnabled && !sellableItems.isEmpty()) {
            inventory.setItem(SLOT_SELL_ALL, new ItemBuilder(Material.TNT)
                    .name(msg.getRaw(lang, "servershop.buttons.sell-all"))
                    .lore(msg.getList(lang, "servershop.lore.sell-all"))
                    .build());
        }

        // Multi-select toggle + (when active) Clear / Continue
        if (multiEnabled) {
            boolean active = session.isActive();
            ItemBuilder toggle = new ItemBuilder(active ? Material.CHEST : Material.HOPPER)
                    .name(msg.getRaw(lang, active
                            ? "servershop.buttons.multi-select-on"
                            : "servershop.buttons.multi-select-off"));
            List<String> toggleLore = new ArrayList<>(msg.getList(lang, "servershop.lore.multi-select-toggle"));
            toggleLore.replaceAll(l -> l
                    .replace("{count}", String.valueOf(session.getSelectedCount()))
                    .replace("{percent}", String.valueOf(session.getPercent())));
            toggle.lore(toggleLore);
            if (active) toggle.glow();
            inventory.setItem(SLOT_MULTI_TOGGLE, toggle.build());

            if (active && !session.isEmpty()) {
                inventory.setItem(SLOT_MULTI_CLEAR, new ItemBuilder(Material.CAULDRON)
                        .name(msg.getRaw(lang, "servershop.buttons.multi-clear")).build());
                inventory.setItem(SLOT_MULTI_CONTINUE, new ItemBuilder(Material.LIME_WOOL)
                        .name(msg.getRaw(lang, "servershop.buttons.multi-continue"))
                        .lore(msg.getList(lang, "servershop.lore.multi-continue"))
                        .glow().build());
            }
        }

        inventory.setItem(SLOT_BACK, new ItemBuilder(Material.BARRIER)
                .name(msg.getRaw(lang, "servershop.buttons.back")).build());

        plugin.getGuiManager().registerGui(player.getUniqueId(), this);
        shopGuiManager.registerGui(player.getUniqueId(), this);
        player.openInventory(inventory);
    }

    /** Renders a single shulker-with-contents grid cell (rows 1–4). */
    private void renderShulkerEntry(int slot, ShulkerEntry se, MessageManager msg, String lang) {
        ItemStack shulker = se.snapshot();
        ShulkerSellService svc = plugin.getShulkerSellService();
        double netValue = svc.netOf(svc.scan(shulker).grossSellable());
        List<String> lore = new ArrayList<>(msg.getList(lang, "servershop.lore.shulker-entry"));
        lore.replaceAll(l -> l
                .replace("{count}", String.valueOf(ShulkerUtil.countTotalItems(shulker)))
                .replace("{value}", msg.formatCurrency(netValue)));
        inventory.setItem(slot, new ItemBuilder(shulker.clone())
                .name("&f" + MaterialUtil.formatName(shulker.getType()))
                .lore(lore).build());
    }

    /**
     * Populates an armor slot in the GUI.
     *
     * @param player        the player
     * @param guiSlot       the GUI slot index (0–4)
     * @param equippedStack the actual equipped ItemStack (may be null)
     * @param msg           message manager
     * @param allowEquipped whether clicking is allowed
     */
    private void populateArmorSlot(Player player, int guiSlot, ItemStack equippedStack,
                                    MessageManager msg, boolean allowEquipped) {
        String slotLabel = switch (guiSlot) {
            case SLOT_HELMET -> "Helmet";
            case SLOT_CHESTPLATE -> "Chestplate";
            case SLOT_LEGGINGS -> "Leggings";
            case SLOT_BOOTS -> "Boots";
            case SLOT_OFFHAND -> "Offhand/Shield";
            default -> "Gear";
        };

        if (equippedStack == null || equippedStack.getType().isAir()) {
            // Empty armor slot
            equippedItemIds[guiSlot] = null;
            equippedStacks[guiSlot] = null;
            inventory.setItem(guiSlot, new ItemBuilder(Material.YELLOW_STAINED_GLASS_PANE)
                    .name("&e" + slotLabel)
                    .lore("&7Not equipped")
                    .build());
            return;
        }

        String itemId = plugin.getShopPriceManager().identifyItem(equippedStack);
        boolean sellable = itemId != null && plugin.getShopPricingService().isSellable(itemId);

        // Reject BuildersDream creative dream items
        if (sellable && plugin.getBuildersDreamHook() != null && plugin.getBuildersDreamHook().isDreamItem(equippedStack)) {
            sellable = false;
        }

        equippedItemIds[guiSlot] = sellable ? itemId : null;
        equippedStacks[guiSlot] = equippedStack.clone();

        if (!sellable) {
            inventory.setItem(guiSlot, new ItemBuilder(equippedStack.clone())
                    .name("&e" + slotLabel + " &8(Not Sellable)")
                    .lore("&7This item cannot be sold to the server.")
                    .build());
            return;
        }

        double baseSellPrice = plugin.getShopPriceManager().getSellPrice(itemId);
        SellPriceBreakdown breakdown = plugin.getShopTransactionService()
                .computeSellBreakdown(baseSellPrice, equippedStack);

        List<String> lore = new ArrayList<>();
        lore.add("&e&l" + slotLabel + " (Equipped)");
        lore.add("");
        lore.add("&7Sell Price: &a" + msg.formatCurrency(breakdown.finalSell()));
        if (breakdown.enchantBonus() > 0 || breakdown.modBonus() > 0 || breakdown.wearPenalty() > 0) {
            lore.add("&eSell Breakdown:");
            lore.add("  &7Base: &f" + msg.formatCurrency(breakdown.baseSell()));
            if (breakdown.enchantBonus() > 0)
                lore.add("  &7Enchant: &a+" + msg.formatCurrency(breakdown.enchantBonus()));
            if (breakdown.modBonus() > 0) {
                java.util.Map<String, Long> modBreakdown = ToolModsHook.getModBreakdown(equippedStack);
                for (var modEntry : modBreakdown.entrySet()) {
                    lore.add("  &7Mod: &a+" + msg.formatCurrency(pt.henrique.market.economy.EconomyManager.toDouble(modEntry.getValue()))
                            + " &8(" + modEntry.getKey() + ")");
                }
            }
            if (breakdown.wearPenalty() > 0)
                lore.add("  &7Wear: &c-" + msg.formatCurrency(breakdown.wearPenalty())
                        + " &8(" + breakdown.wearDescription() + ")");
            else if (breakdown.wearDescription().contains("Mending"))
                lore.add("  &7Wear: &aNone (Mending)");
        }
        lore.add("");
        if (allowEquipped) {
            lore.add("&eClick to sell (requires confirmation)");
        } else {
            lore.add("&c⚠ Equipped gear selling disabled");
            lore.add("&7Remove from armor to sell normally.");
        }

        inventory.setItem(guiSlot, new ItemBuilder(equippedStack.clone())
                .name("&f" + equippedStack.getType().name().replace("_", " "))
                .lore(lore)
                .build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventory) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();

        // Prevent shift-click moving armor into the sell grid
        if (event.isShiftClick()) {
            event.setCancelled(true);
            return;
        }

        MultiSellSession session = shopGuiManager.getMultiSellSession(player.getUniqueId());
        boolean multiEnabled = plugin.getConfig().getBoolean("sell-inventory.multi-select.enabled", true);

        if (slot == SLOT_BACK) {
            SoundUtil.playClickSound(player);
            shopGuiManager.clearMultiSellSession(player.getUniqueId()); // leaving the sell flow
            shopGuiManager.openMainMenu(player);
            return;
        }

        // ── Multi-select: toggle mode (slot 46) ──────────────────
        if (multiEnabled && slot == SLOT_MULTI_TOGGLE) {
            SoundUtil.playClickSound(player);
            if (session.isActive()) {
                session.setActive(false);
                session.clearSelection();
            } else {
                session.setActive(true);
            }
            shopGuiManager.openSellInventory(player); // re-render
            return;
        }

        // ── Multi-select: clear selection (slot 48) ──────────────
        if (multiEnabled && slot == SLOT_MULTI_CLEAR) {
            if (session.isActive() && !session.isEmpty()) {
                SoundUtil.playClickSound(player);
                session.clearSelection();
                shopGuiManager.openSellInventory(player);
            }
            return;
        }

        // ── Multi-select: continue → percentage (slot 50) ────────
        if (multiEnabled && slot == SLOT_MULTI_CONTINUE) {
            if (session.isActive() && !session.isEmpty()) {
                SoundUtil.playClickSound(player);
                shopGuiManager.openMultiSellQuantity(player);
            } else {
                SoundUtil.playErrorSound(player);
                player.sendMessage(plugin.getMessageManager().getPrefixedP(player, "servershop.messages.multi-none-selected"));
            }
            return;
        }

        // ── Sell All button (slot 45) ────────────────────────────
        if (slot == SLOT_SELL_ALL) {
            boolean sellAllEnabled = plugin.getConfig().getBoolean("sell-inventory.sell-all.enabled", true);
            if (sellAllEnabled) {
                SoundUtil.playClickSound(player);
                shopGuiManager.openSellAllConfirm(player);
            }
            return;
        }

        // ── Armor row click (slots 0–4) ──────────────────────────
        if (slot >= 0 && slot <= 4) {
            handleArmorSlotClick(player, slot);
            return;
        }

        // ── Regular inventory items (rows 1–4, slots 9–44) ──────
        int itemIndex = slot - 9;
        if (itemIndex >= 0 && itemIndex < gridEntries.size()) {
            GridEntry ge = gridEntries.get(itemIndex);
            if (ge instanceof ShulkerEntry se) {
                // Shulkers-with-contents always open the chooser (even in multi-select mode).
                SoundUtil.playClickSound(player);
                shopGuiManager.openShulkerSellChooser(player, se.playerSlot());
                return;
            }
            AggregatedEntry ae = (AggregatedEntry) ge;
            if (multiEnabled && session.isActive()) {
                session.toggle(ae.itemId());
                SoundUtil.playClickSound(player);
                shopGuiManager.openSellInventory(player); // re-render selection state
            } else {
                SoundUtil.playClickSound(player);
                shopGuiManager.openSellOnlyDetail(player, ae.itemId());
            }
        }
    }

    private void handleArmorSlotClick(Player player, int guiSlot) {
        String itemId = equippedItemIds[guiSlot];
        if (itemId == null) {
            SoundUtil.playErrorSound(player);
            return;
        }

        boolean allowEquipped = plugin.getConfig().getBoolean("sell-inventory.allow-equipped-selling", false);

        if (!allowEquipped) {
            // Show warning
            SoundUtil.playErrorSound(player);
            player.sendMessage(plugin.getMessageManager().getPrefixedP(player,
                    "servershop.messages.sell-failed")
            );
            // Send a specific message about equipped gear
            MessageManager msg = plugin.getMessageManager();
            String lang = msg.getPlayerLanguage(player);
            player.sendMessage(TextUtil.colorize(
                    msg.getRaw(lang, "prefix") + "&cEquipped gear cannot be sold from here. Unequip it first."));
            return;
        }

        // Allow selling with 2-step confirmation
        if (pendingArmorConfirmSlot == guiSlot) {
            // Second click — sell the item
            pendingArmorConfirmSlot = -1;
            SoundUtil.playClickSound(player);
            shopGuiManager.openSellOnlyDetail(player, itemId);
        } else {
            // First click — show confirmation
            pendingArmorConfirmSlot = guiSlot;
            SoundUtil.playClickSound(player);
            player.sendMessage(TextUtil.colorize(
                    plugin.getMessageManager().getRawP(player, "prefix") +
                            "&eClick the equipped item again to confirm selling."));
        }
    }

    @Override public GuiType getType() { return GuiType.SHOP_SELL_INVENTORY; }
    @Override public @NotNull Inventory getInventory() { return inventory; }
}
