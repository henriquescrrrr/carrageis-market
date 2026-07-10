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
import pt.henrique.market.servershop.ShulkerUtil;
import pt.henrique.market.servershop.model.ShopCategory;
import pt.henrique.market.util.ItemBuilder;
import pt.henrique.market.util.SoundUtil;
import pt.henrique.market.util.TextUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * The server shop main menu GUI showing all categories and utility buttons.
 * Layout (54 slots / 6 rows):
 *   Row 0: Filler
 *   Row 1-3: Category buttons (centered pyramid)
 *   Row 4: Filler
 *   Row 5: [...] [Sell Hand] [Search] [Sell Inv] [...] [Back]
 */
public class ShopMainMenuGui implements MarketGui {

    private final Market plugin;
    private final ShopGuiManager shopGuiManager;
    private Inventory inventory;
    private List<ShopCategory> visibleCategories;

    // Bottom bar: Sell Hand immediately LEFT of Search, Sell Inv immediately RIGHT
    private static final int SLOT_SELL_CHEST = 47;
    private static final int SLOT_SELL_HAND = 48;
    private static final int SLOT_SEARCH = 49;
    private static final int SLOT_SELL_INV = 50;
    private static final int SLOT_BACK = 53;

    public ShopMainMenuGui(Market plugin, ShopGuiManager shopGuiManager) {
        this.plugin = plugin;
        this.shopGuiManager = shopGuiManager;
    }

    public void open(Player player) {
        MessageManager msg = plugin.getMessageManager();
        String lang = msg.getPlayerLanguage(player);
        String title = msg.getRaw(lang, "servershop.titles.main-menu");
        inventory = Bukkit.createInventory(this, 54, TextUtil.colorize(title));

        // Fill background
        var fillerItem = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) inventory.setItem(i, fillerItem);

        // Filter categories: hide disabled if configured
        List<ShopCategory> allCategories = plugin.getShopPriceManager().getCategories();
        boolean hideDisabled = plugin.getConfig().getBoolean("server-shop.hide-disabled-categories", true);
        visibleCategories = new ArrayList<>();
        for (ShopCategory cat : allCategories) {
            if (hideDisabled && !cat.isBuyEnabled() && !cat.isSellEnabled()) continue;
            visibleCategories.add(cat);
        }

        // Place category buttons in pyramid layout
        int[] categorySlots = getCategorySlots(visibleCategories.size());
        for (int i = 0; i < visibleCategories.size() && i < categorySlots.length; i++) {
            ShopCategory category = visibleCategories.get(i);
            String catName = msg.getRaw(lang, "servershop.category-names." + category.getId());
            int itemCount = category.getItemCount();

            List<String> lore = new ArrayList<>(msg.getList(lang, "servershop.lore.category"));
            lore.replaceAll(line -> line.replace("{count}", String.valueOf(itemCount)));

            inventory.setItem(categorySlots[i], new ItemBuilder(category.getIcon())
                    .name("&a&l" + catName)
                    .lore(lore)
                    .build());
        }

        // Sell hand button
        if (plugin.getConfig().getBoolean("server-shop.enable-sell-hand", true)) {
            inventory.setItem(SLOT_SELL_HAND, new ItemBuilder(Material.GOLD_INGOT)
                    .name(msg.getRaw(lang, "servershop.buttons.sell-hand"))
                    .lore(msg.getList(lang, "servershop.lore.sell-hand"))
                    .build());
        }

        // Search button (center)
        inventory.setItem(SLOT_SEARCH, new ItemBuilder(Material.COMPASS)
                .name(msg.getRaw(lang, "servershop.buttons.search"))
                .lore(msg.getList(lang, "servershop.lore.search"))
                .build());

        // Sell inventory button
        if (plugin.getConfig().getBoolean("server-shop.enable-sell-all", true)) {
            inventory.setItem(SLOT_SELL_INV, new ItemBuilder(Material.CHEST)
                    .name(msg.getRaw(lang, "servershop.buttons.sell-inventory"))
                    .lore(msg.getList(lang, "servershop.lore.sell-inventory"))
                    .build());
        }

        // Sell chest button
        if (plugin.getConfig().getBoolean("server-shop.enable-sell-chest", true)) {
            List<String> chestLore = new ArrayList<>();
            String rawLore = msg.getRaw(lang, "servershop.lore.sell-chest-lore");
            if (rawLore != null) {
                for (String part : rawLore.split("\\|")) {
                    chestLore.add(part);
                }
            }
            inventory.setItem(SLOT_SELL_CHEST, new ItemBuilder(Material.ENDER_CHEST)
                    .name(msg.getRaw(lang, "servershop.buttons.sell-chest"))
                    .lore(chestLore)
                    .build());
        }

        // Back to hub button (BARRIER at bottom-right)
        inventory.setItem(SLOT_BACK, new ItemBuilder(Material.BARRIER)
                .name(msg.getRaw(lang, "servershop.buttons.back"))
                .build());

        plugin.getGuiManager().registerGui(player.getUniqueId(), this);
        shopGuiManager.registerGui(player.getUniqueId(), this);
        player.openInventory(inventory);
        SoundUtil.playClickSound(player);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventory) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();

        // Check category clicks
        int[] categorySlots = getCategorySlots(visibleCategories.size());
        for (int i = 0; i < visibleCategories.size() && i < categorySlots.length; i++) {
            if (slot == categorySlots[i]) {
                SoundUtil.playClickSound(player);
                shopGuiManager.openCategory(player, visibleCategories.get(i), 0);
                return;
            }
        }

        switch (slot) {
            case SLOT_SELL_HAND -> handleSellHand(player);
            case SLOT_SELL_INV -> {
                SoundUtil.playClickSound(player);
                shopGuiManager.openSellInventory(player);
            }
            case SLOT_SELL_CHEST -> {
                if (plugin.getConfig().getBoolean("server-shop.enable-sell-chest", true)) {
                    SoundUtil.playClickSound(player);
                    plugin.getChestSellService().startSession(player);
                }
            }
            case SLOT_SEARCH -> startSearch(player);
            case SLOT_BACK -> {
                SoundUtil.playClickSound(player);
                plugin.getGuiManager().openMainMenu(player);
            }
        }
    }

    private void handleSellHand(Player player) {
        var heldItem = player.getInventory().getItemInMainHand();
        if (heldItem.getType().isAir()) {
            player.sendMessage(plugin.getMessageManager().getPrefixedP(player, "servershop.messages.sell-hand-empty"));
            SoundUtil.playErrorSound(player);
            return;
        }
        // Reject BuildersDream creative dream items
        if (plugin.getBuildersDreamHook() != null && plugin.getBuildersDreamHook().isDreamItem(heldItem)) {
            player.sendMessage(plugin.getMessageManager().getPrefixedP(player, "messages.dream-item-blocked"));
            SoundUtil.playErrorSound(player);
            return;
        }
        // Shulker box with contents → open the 3-option shulker sell chooser
        if (plugin.getConfig().getBoolean("sell-inventory.shulker-sell.enabled", true)
                && ShulkerUtil.isShulkerBox(heldItem) && ShulkerUtil.hasContents(heldItem)) {
            SoundUtil.playClickSound(player);
            shopGuiManager.openShulkerSellChooser(player, player.getInventory().getHeldItemSlot());
            return;
        }
        String itemId = plugin.getShopPriceManager().identifyItem(heldItem);
        if (itemId == null || !plugin.getShopPricingService().isSellable(itemId)) {
            player.sendMessage(plugin.getMessageManager().getPrefixedP(player, "servershop.messages.sell-hand-not-sellable"));
            SoundUtil.playErrorSound(player);
            return;
        }
        SoundUtil.playClickSound(player);
        shopGuiManager.openSellOnlyDetail(player, itemId);
    }

    private void startSearch(Player player) {
        SoundUtil.playClickSound(player);
        player.closeInventory();

        String searchMode = plugin.getConfig().getString("server-shop.search-mode", "chat");

        if ("sign".equalsIgnoreCase(searchMode)) {
            // Legacy sign editor mode
            plugin.getShopSearchSignHandler().openSearchSign(player, (query) -> {
                if (query == null || query.isBlank()) {
                    Bukkit.getScheduler().runTask(plugin, () -> shopGuiManager.openMainMenu(player));
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> shopGuiManager.openSearchResults(player, query.trim(), 0));
            });
        } else {
            // Chat-based search (default) — Enter key naturally submits
            plugin.getShopSearchSignHandler().openChatSearch(player, (query) -> {
                if (query == null || query.isBlank()) {
                    Bukkit.getScheduler().runTask(plugin, () -> shopGuiManager.openMainMenu(player));
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> shopGuiManager.openSearchResults(player, query.trim(), 0));
            });
        }
    }

    /**
     * Pyramid-style category slot layout.
     * Ensures redstone (typically the 7th category) lands at slot 4 in its row.
     */
    private int[] getCategorySlots(int count) {
        return switch (count) {
            case 1 -> new int[]{22};
            case 2 -> new int[]{21, 23};
            case 3 -> new int[]{20, 22, 24};
            case 4 -> new int[]{20, 22, 24, 31};
            case 5 -> new int[]{20, 22, 24, 30, 32};
            case 6 -> new int[]{20, 22, 24, 29, 31, 33};
            case 7 -> new int[]{19, 21, 23, 25, 29, 31, 33};
            case 8 -> new int[]{19, 21, 23, 25, 28, 30, 32, 34};
            case 9 -> new int[]{10, 13, 16, 20, 22, 24, 29, 31, 33};
            case 10 -> new int[]{10, 13, 16, 20, 22, 24, 29, 31, 33, 34};
            case 11 -> new int[]{10, 12, 14, 16, 19, 21, 23, 25, 30, 32, 34};
            case 12 -> new int[]{10, 12, 14, 16, 19, 21, 23, 25, 28, 30, 32, 34};
            case 13 -> new int[]{10, 12, 14, 16, 19, 21, 22, 23, 25, 28, 30, 32, 34};
            case 14 -> new int[]{10, 12, 14, 16, 19, 21, 22, 23, 25, 28, 29, 30, 32, 34};
            case 15 -> new int[]{10, 11, 12, 14, 16, 19, 21, 22, 23, 25, 28, 29, 30, 32, 34};
            default -> {
                int[] slots = new int[Math.min(count, 21)];
                int idx = 0;
                for (int row = 1; row <= 3 && idx < slots.length; row++)
                    for (int col = 1; col <= 7 && idx < slots.length; col++)
                        slots[idx++] = row * 9 + col;
                yield slots;
            }
        };
    }

    @Override
    public GuiType getType() { return GuiType.SHOP_MAIN_MENU; }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }
}
