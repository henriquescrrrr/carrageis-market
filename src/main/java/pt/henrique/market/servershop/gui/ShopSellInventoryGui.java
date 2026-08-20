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
import pt.henrique.market.servershop.ShulkerUtil;
import pt.henrique.market.servershop.model.SellVariant;
import pt.henrique.market.servershop.service.EnchantPriceUtil;
import pt.henrique.market.servershop.service.ShopTransactionService;
import pt.henrique.market.servershop.service.ShulkerSellService;
import pt.henrique.market.util.ItemBuilder;
import pt.henrique.market.util.SoundUtil;
import pt.henrique.market.util.TextUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI that displays sellable items from the player's inventory.
 * <p>
 * Entries are one per <em>item</em>, not one per material: a Power V bow and a
 * plain bow are two separate cells, each showing the real item and its own price,
 * and clicking one only ever sells that one.
 * <p>
 * <b>Layout (54 slots / 6 rows):</b>
 * <ul>
 *   <li>Row 0 (slots 0–8): <b>Equipped Gear</b> — Helmet (0), Chestplate (1),
 *       Leggings (2), Boots (3), Offhand/Shield (4), then yellow filler (5–8).</li>
 *   <li>Rows 1–4 (slots 9–44): one page of sellable items, 36 per page.</li>
 *   <li>Row 5 (slots 45–53): sell-all, multi-select controls, paging, back.</li>
 * </ul>
 * <p>
 * By default, equipped gear cannot be sold (shows a warning on click).
 * Config toggle: {@code sell-inventory.allow-equipped-selling: false}
 */
public class ShopSellInventoryGui implements MarketGui {

    private static final int PAGE_SIZE = 36;
    private static final int GRID_START = 9;

    private final Market plugin;
    private final ShopGuiManager shopGuiManager;
    private final int page;
    /** When set, only items of this material are listed. Null = show everything. */
    private final Material filter;
    private Inventory inventory;

    /** Entries shown on this page, indexed by (slot − 9). */
    private final List<GridEntry> gridEntries = new ArrayList<>();
    private int totalPages = 1;

    private sealed interface GridEntry permits VariantEntry, ShulkerEntry {}
    /** One distinct sellable item. */
    private record VariantEntry(SellVariant variant) implements GridEntry {}
    /** A single shulker box that contains items, tied to its player-inventory slot. */
    private record ShulkerEntry(int playerSlot, ItemStack snapshot) implements GridEntry {}

    private static final int SLOT_HELMET = 0;
    private static final int SLOT_CHESTPLATE = 1;
    private static final int SLOT_LEGGINGS = 2;
    private static final int SLOT_BOOTS = 3;
    private static final int SLOT_OFFHAND = 4;
    private static final int ARMOR_ROW_END = 8;
    private static final int SLOT_SELL_ALL = 45;
    private static final int SLOT_MULTI_TOGGLE = 46;
    private static final int SLOT_PREV = 47;
    private static final int SLOT_MULTI_CLEAR = 48;
    private static final int SLOT_PAGE_INFO = 49;
    private static final int SLOT_MULTI_CONTINUE = 50;
    private static final int SLOT_NEXT = 51;
    private static final int SLOT_CLEAR_FILTER = 52;
    private static final int SLOT_BACK = 53;

    /** Equipped variants per armour slot, null when nothing sellable is worn there. */
    private final SellVariant[] equippedVariants = new SellVariant[5];
    /** Player-inventory slot backing each armour cell: helmet 39 … offhand 40. */
    private static final int[] ARMOR_INVENTORY_SLOTS = {39, 38, 37, 36, 40};
    /** Which armour cell is awaiting a confirmation click (-1 = none). */
    private int pendingArmorConfirmSlot = -1;

    public ShopSellInventoryGui(Market plugin, ShopGuiManager shopGuiManager) {
        this(plugin, shopGuiManager, 0, null);
    }

    public ShopSellInventoryGui(Market plugin, ShopGuiManager shopGuiManager, int page, Material filter) {
        this.plugin = plugin;
        this.shopGuiManager = shopGuiManager;
        this.page = Math.max(0, page);
        this.filter = filter;
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
                .name(msg.getRaw(lang, "servershop.buttons.equipped-gear")).build();
        for (int i = 5; i <= ARMOR_ROW_END; i++) inventory.setItem(i, yellowFiller);

        ItemStack[] armor = player.getInventory().getArmorContents(); // boots, leggings, chestplate, helmet
        ItemStack offhand = player.getInventory().getItemInOffHand();
        populateArmorSlot(player, SLOT_HELMET, armor.length > 3 ? armor[3] : null, msg, lang, allowEquipped);
        populateArmorSlot(player, SLOT_CHESTPLATE, armor.length > 2 ? armor[2] : null, msg, lang, allowEquipped);
        populateArmorSlot(player, SLOT_LEGGINGS, armor.length > 1 ? armor[1] : null, msg, lang, allowEquipped);
        populateArmorSlot(player, SLOT_BOOTS, armor.length > 0 ? armor[0] : null, msg, lang, allowEquipped);
        populateArmorSlot(player, SLOT_OFFHAND,
                (offhand != null && !offhand.getType().isAir()) ? offhand : null, msg, lang, allowEquipped);

        // ── Collect entries ───────────────────────────────────────
        boolean shulkerSellEnabled = plugin.getConfig().getBoolean("sell-inventory.shulker-sell.enabled", true);
        List<GridEntry> all = new ArrayList<>();

        // Shulkers with contents come first: they open their own chooser.
        if (shulkerSellEnabled) {
            ItemStack[] storage = player.getInventory().getStorageContents();
            for (int i = 0; i < storage.length; i++) {
                ItemStack stack = storage[i];
                if (stack == null || stack.getType().isAir()) continue;
                if (plugin.getBuildersDreamHook() != null && plugin.getBuildersDreamHook().isDreamItem(stack)) continue;
                if (!ShulkerUtil.isShulkerBox(stack) || !ShulkerUtil.hasContents(stack)) continue;
                if (filter != null && stack.getType() != filter) continue;
                all.add(new ShulkerEntry(i, stack.clone()));
            }
        }

        SellVariantScanner.Result scan = plugin.getSellVariantScanner()
                .scanStorage(player, SellVariantScanner.Options.showAll(shulkerSellEnabled));
        for (SellVariant variant : scan.variants()) {
            if (filter != null && variant.template().getType() != filter) continue;
            all.add(new VariantEntry(variant));
        }

        totalPages = Math.max(1, (int) Math.ceil((double) all.size() / PAGE_SIZE));
        int currentPage = Math.min(page, totalPages - 1);
        int from = currentPage * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, all.size());

        gridEntries.clear();
        gridEntries.addAll(all.subList(from, to));

        int slot = GRID_START;
        for (GridEntry entry : gridEntries) {
            if (entry instanceof ShulkerEntry shulker) {
                renderShulkerEntry(slot, shulker, msg, lang);
            } else {
                renderVariantEntry(slot, ((VariantEntry) entry).variant(), session, multiEnabled, msg, lang);
            }
            slot++;
        }

        var grayFiller = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = slot; i < 45; i++) inventory.setItem(i, grayFiller);
        for (int i = 45; i < 54; i++) inventory.setItem(i, grayFiller);

        // ── Row 5: actions ────────────────────────────────────────
        boolean sellAllEnabled = plugin.getConfig().getBoolean("sell-inventory.sell-all.enabled", true);
        if (sellAllEnabled && !all.isEmpty()) {
            inventory.setItem(SLOT_SELL_ALL, new ItemBuilder(Material.TNT)
                    .name(msg.getRaw(lang, "servershop.buttons.sell-all"))
                    .lore(msg.getList(lang, "servershop.lore.sell-all"))
                    .build());
        }

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

        if (currentPage > 0) {
            inventory.setItem(SLOT_PREV, new ItemBuilder(Material.ARROW)
                    .name(msg.getRaw(lang, "servershop.buttons.prev-page")).build());
        }
        if (currentPage < totalPages - 1) {
            inventory.setItem(SLOT_NEXT, new ItemBuilder(Material.ARROW)
                    .name(msg.getRaw(lang, "servershop.buttons.next-page")).build());
        }

        List<String> pageLore = new ArrayList<>();
        pageLore.add(msg.getRaw(lang, "servershop.lore.sell-page-info")
                .replace("{page}", String.valueOf(currentPage + 1))
                .replace("{pages}", String.valueOf(totalPages))
                .replace("{types}", String.valueOf(all.size())));
        if (filter != null) {
            pageLore.add(msg.getRaw(lang, "servershop.lore.sell-filter-active")
                    .replace("{item}", pt.henrique.market.servershop.MaterialUtil.formatName(filter)));
        }
        inventory.setItem(SLOT_PAGE_INFO, new ItemBuilder(Material.PAPER)
                .name(msg.getRaw(lang, "servershop.buttons.sell-page-info")).lore(pageLore).build());

        if (filter != null) {
            inventory.setItem(SLOT_CLEAR_FILTER, new ItemBuilder(Material.LIGHT_GRAY_DYE)
                    .name(msg.getRaw(lang, "servershop.buttons.sell-clear-filter")).build());
        }

        inventory.setItem(SLOT_BACK, new ItemBuilder(Material.BARRIER)
                .name(msg.getRaw(lang, "servershop.buttons.back")).build());

        plugin.getGuiManager().registerGui(player.getUniqueId(), this);
        shopGuiManager.registerGui(player.getUniqueId(), this);
        player.openInventory(inventory);
    }

    /* ════════════════════════════════════════════════════════
     *  Rendering
     * ════════════════════════════════════════════════════════ */

    /** Renders one distinct sellable item, showing the real stack so its tooltip is intact. */
    private void renderVariantEntry(int slot, SellVariant variant, MultiSellSession session,
                                    boolean multiEnabled, MessageManager msg, String lang) {
        List<String> lore = new ArrayList<>();
        lore.add(msg.getRaw(lang, "servershop.lore.variant-amount")
                .replace("{amount}", String.valueOf(variant.amount())));
        lore.add(msg.getRaw(lang, "servershop.lore.variant-unit-price")
                .replace("{price}", msg.formatCurrency(variant.unitPrice())));
        lore.add(msg.getRaw(lang, "servershop.lore.variant-stack-total")
                .replace("{total}", msg.formatCurrency(variant.totalValue())));

        String enchants = EnchantPriceUtil.describeEnchants(variant.template());
        if (!enchants.isEmpty()) {
            lore.add("");
            lore.add(msg.getRaw(lang, "servershop.lore.variant-enchants").replace("{enchants}", enchants));
        }

        boolean selected = multiEnabled && session.isActive() && session.isSelected(variant.template());
        lore.add("");
        if (multiEnabled && session.isActive()) {
            lore.add(msg.getRaw(lang, selected
                    ? "servershop.lore.multi-deselect-hint"
                    : "servershop.lore.multi-select-hint"));
            if (selected) lore.add(msg.getRaw(lang, "servershop.lore.multi-selected"));
        } else {
            lore.add(msg.getRaw(lang, "servershop.lore.variant-click-to-sell"));
        }

        ItemBuilder builder = new ItemBuilder(variant.createStack(Math.min(variant.amount(), 64)))
                .name("&f" + variant.displayName()).lore(lore);
        if (selected) builder.glow();
        inventory.setItem(slot, builder.build());
    }

    /** Renders a single shulker-with-contents grid cell. */
    private void renderShulkerEntry(int slot, ShulkerEntry entry, MessageManager msg, String lang) {
        ItemStack shulker = entry.snapshot();
        ShulkerSellService svc = plugin.getShulkerSellService();
        double netValue = svc.netOf(svc.scan(shulker).grossSellable());
        List<String> lore = new ArrayList<>(msg.getList(lang, "servershop.lore.shulker-entry"));
        lore.replaceAll(l -> l
                .replace("{count}", String.valueOf(ShulkerUtil.countTotalItems(shulker)))
                .replace("{value}", msg.formatCurrency(netValue)));
        inventory.setItem(slot, new ItemBuilder(shulker.clone())
                .name("&f" + pt.henrique.market.servershop.MaterialUtil.formatName(shulker.getType()))
                .lore(lore).build());
    }

    /**
     * Populates an armour cell in row 0.
     *
     * @param guiSlot       the GUI slot index (0–4)
     * @param equippedStack the actual equipped ItemStack (may be null)
     */
    private void populateArmorSlot(Player player, int guiSlot, ItemStack equippedStack,
                                   MessageManager msg, String lang, boolean allowEquipped) {
        String slotLabel = msg.getRaw(lang, "servershop.buttons.gear-slot-" + guiSlot);

        if (equippedStack == null || equippedStack.getType().isAir()) {
            equippedVariants[guiSlot] = null;
            inventory.setItem(guiSlot, new ItemBuilder(Material.YELLOW_STAINED_GLASS_PANE)
                    .name("&e" + slotLabel)
                    .lore(msg.getRaw(lang, "servershop.lore.gear-empty"))
                    .build());
            return;
        }

        SellVariant variant = plugin.getSellVariantScanner()
                .variantOf(equippedStack, ARMOR_INVENTORY_SLOTS[guiSlot]);
        equippedVariants[guiSlot] = variant;

        if (variant == null) {
            inventory.setItem(guiSlot, new ItemBuilder(equippedStack.clone())
                    .name("&e" + slotLabel)
                    .lore(msg.getRaw(lang, "servershop.lore.gear-not-sellable"))
                    .build());
            return;
        }

        List<String> lore = new ArrayList<>();
        lore.add("&e&l" + slotLabel);
        lore.add("");
        lore.add(msg.getRaw(lang, "servershop.lore.variant-unit-price")
                .replace("{price}", msg.formatCurrency(variant.unitPrice())));
        String enchants = EnchantPriceUtil.describeEnchants(equippedStack);
        if (!enchants.isEmpty()) {
            lore.add(msg.getRaw(lang, "servershop.lore.variant-enchants").replace("{enchants}", enchants));
        }
        lore.add("");
        lore.add(msg.getRaw(lang, allowEquipped
                ? "servershop.lore.gear-click-to-sell"
                : "servershop.lore.gear-selling-disabled"));

        inventory.setItem(guiSlot, new ItemBuilder(equippedStack.clone())
                .name("&f" + variant.displayName()).lore(lore).build());
    }

    /* ════════════════════════════════════════════════════════
     *  Clicks
     * ════════════════════════════════════════════════════════ */

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventory) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.isShiftClick()) return;
        int slot = event.getRawSlot();

        MultiSellSession session = shopGuiManager.getMultiSellSession(player.getUniqueId());
        boolean multiEnabled = plugin.getConfig().getBoolean("sell-inventory.multi-select.enabled", true);
        int currentPage = Math.min(page, Math.max(0, totalPages - 1));

        switch (slot) {
            case SLOT_BACK -> {
                SoundUtil.playClickSound(player);
                shopGuiManager.clearMultiSellSession(player.getUniqueId()); // leaving the sell flow
                shopGuiManager.openMainMenu(player);
                return;
            }
            case SLOT_PREV -> {
                if (currentPage > 0) {
                    SoundUtil.playClickSound(player);
                    shopGuiManager.openSellInventory(player, currentPage - 1, filter);
                }
                return;
            }
            case SLOT_NEXT -> {
                if (currentPage < totalPages - 1) {
                    SoundUtil.playClickSound(player);
                    shopGuiManager.openSellInventory(player, currentPage + 1, filter);
                }
                return;
            }
            case SLOT_CLEAR_FILTER -> {
                if (filter != null) {
                    SoundUtil.playClickSound(player);
                    shopGuiManager.openSellInventory(player);
                }
                return;
            }
            case SLOT_SELL_ALL -> {
                if (plugin.getConfig().getBoolean("sell-inventory.sell-all.enabled", true)) {
                    SoundUtil.playClickSound(player);
                    shopGuiManager.openSellAllConfirm(player);
                }
                return;
            }
            case SLOT_MULTI_TOGGLE -> {
                if (!multiEnabled) return;
                SoundUtil.playClickSound(player);
                if (session.isActive()) {
                    session.setActive(false);
                    session.clearSelection();
                } else {
                    session.setActive(true);
                }
                shopGuiManager.openSellInventory(player, currentPage, filter);
                return;
            }
            case SLOT_MULTI_CLEAR -> {
                if (multiEnabled && session.isActive() && !session.isEmpty()) {
                    SoundUtil.playClickSound(player);
                    session.clearSelection();
                    shopGuiManager.openSellInventory(player, currentPage, filter);
                }
                return;
            }
            case SLOT_MULTI_CONTINUE -> {
                if (!multiEnabled) return;
                if (session.isActive() && !session.isEmpty()) {
                    SoundUtil.playClickSound(player);
                    shopGuiManager.openMultiSellQuantity(player);
                } else {
                    SoundUtil.playErrorSound(player);
                    player.sendMessage(plugin.getMessageManager().getPrefixedP(player,
                            "servershop.messages.multi-none-selected"));
                }
                return;
            }
            default -> { /* fall through to grid / armour handling */ }
        }

        if (slot >= 0 && slot <= 4) {
            handleArmorSlotClick(player, slot);
            return;
        }

        int index = slot - GRID_START;
        if (index < 0 || index >= gridEntries.size()) return;

        GridEntry entry = gridEntries.get(index);
        if (entry instanceof ShulkerEntry shulker) {
            // Shulkers-with-contents always open the chooser (even in multi-select mode).
            SoundUtil.playClickSound(player);
            shopGuiManager.openShulkerSellChooser(player, shulker.playerSlot());
            return;
        }

        SellVariant variant = ((VariantEntry) entry).variant();
        if (multiEnabled && session.isActive()) {
            session.toggle(variant.template());
            SoundUtil.playClickSound(player);
            shopGuiManager.openSellInventory(player, currentPage, filter);
        } else {
            SoundUtil.playClickSound(player);
            shopGuiManager.openSellVariantDetail(player, variant,
                    ShopTransactionService.STORAGE_SLOTS, SellVariantDetailGui.Origin.SELL_INVENTORY);
        }
    }

    private void handleArmorSlotClick(Player player, int guiSlot) {
        SellVariant variant = equippedVariants[guiSlot];
        if (variant == null) {
            SoundUtil.playErrorSound(player);
            return;
        }

        if (!plugin.getConfig().getBoolean("sell-inventory.allow-equipped-selling", false)) {
            SoundUtil.playErrorSound(player);
            player.sendMessage(plugin.getMessageManager().getPrefixedP(player,
                    "servershop.messages.gear-selling-disabled"));
            return;
        }

        if (pendingArmorConfirmSlot == guiSlot) {
            pendingArmorConfirmSlot = -1;
            SoundUtil.playClickSound(player);
            // Restrict removal to the one worn slot, so an identical spare in the
            // backpack is never taken instead of the equipped piece.
            shopGuiManager.openSellVariantDetail(player, variant,
                    ShopTransactionService.onlySlot(ARMOR_INVENTORY_SLOTS[guiSlot]),
                    SellVariantDetailGui.Origin.SELL_INVENTORY);
        } else {
            pendingArmorConfirmSlot = guiSlot;
            SoundUtil.playClickSound(player);
            player.sendMessage(plugin.getMessageManager().getPrefixedP(player,
                    "servershop.messages.gear-confirm-click"));
        }
    }

    @Override public GuiType getType() { return GuiType.SHOP_SELL_INVENTORY; }
    @Override public @NotNull Inventory getInventory() { return inventory; }
}
