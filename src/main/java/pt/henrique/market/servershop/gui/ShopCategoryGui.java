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
import pt.henrique.market.servershop.model.ShopCategory;
import pt.henrique.market.servershop.model.ShopItem;
import pt.henrique.market.servershop.model.ShopPriceEntry;
import pt.henrique.market.util.ItemBuilder;
import pt.henrique.market.util.SoundUtil;
import pt.henrique.market.util.TextUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Paginated GUI showing all items in a single server shop category.
 */
public class ShopCategoryGui implements MarketGui {

    private final Market plugin;
    private final ShopGuiManager shopGuiManager;
    private final ShopCategory category;
    private final int page;
    private Inventory inventory;
    private List<String> itemIds;

    private static final int SLOT_INFO = 45;
    private static final int SLOT_PREV = 48;
    private static final int SLOT_SEARCH = 49;
    private static final int SLOT_NEXT = 50;
    private static final int SLOT_BACK = 53;
    private static final int ITEMS_PER_PAGE = 45;

    public ShopCategoryGui(Market plugin, ShopGuiManager shopGuiManager,
                            ShopCategory category, int page) {
        this.plugin = plugin;
        this.shopGuiManager = shopGuiManager;
        this.category = category;
        this.page = page;
    }

    public void open(Player player) {
        MessageManager msg = plugin.getMessageManager();
        String lang = msg.getPlayerLanguage(player);
        String catName = msg.getRaw(lang, "servershop.category-names." + category.getId());
        String title = msg.getRaw(lang, "servershop.titles.category").replace("{category}", catName);
        inventory = Bukkit.createInventory(this, 54, TextUtil.colorize(title));

        itemIds = plugin.getShopPriceManager().getCategoryItems(category);
        int totalPages = Math.max(1, (int) Math.ceil((double) itemIds.size() / ITEMS_PER_PAGE));
        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, itemIds.size());

        for (int i = startIndex; i < endIndex; i++) {
            String itemId = itemIds.get(i);
            ShopPriceEntry entry = plugin.getShopPriceManager().getPriceEntry(itemId);
            if (entry == null) continue;
            inventory.setItem(i - startIndex, buildItemIcon(itemId, entry, msg, lang));
        }

        // Bottom nav
        var fillerItem = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 45; i < 54; i++) inventory.setItem(i, fillerItem);

        inventory.setItem(SLOT_BACK, new ItemBuilder(Material.BARRIER)
                .name(msg.getRaw(lang, "servershop.buttons.back")).build());

        if (page > 0)
            inventory.setItem(SLOT_PREV, new ItemBuilder(Material.ARROW)
                    .name(msg.getRaw(lang, "servershop.buttons.prev-page")).build());

        inventory.setItem(SLOT_INFO, new ItemBuilder(Material.PAPER)
                .name("&7Page " + (page + 1) + "/" + totalPages)
                .lore("&7" + itemIds.size() + " items").build());

        if (page < totalPages - 1)
            inventory.setItem(SLOT_NEXT, new ItemBuilder(Material.ARROW)
                    .name(msg.getRaw(lang, "servershop.buttons.next-page")).build());

        inventory.setItem(SLOT_SEARCH, new ItemBuilder(Material.COMPASS)
                .name(msg.getRaw(lang, "servershop.buttons.search")).build());

        plugin.getGuiManager().registerGui(player.getUniqueId(), this);
        shopGuiManager.registerGui(player.getUniqueId(), this);
        player.openInventory(inventory);
    }

    private ItemStack buildItemIcon(String itemId, ShopPriceEntry entry, MessageManager msg, String lang) {
        ShopItem shopItem = plugin.getShopPriceManager().getShopItem(itemId);
        String itemName = shopItem != null ? shopItem.getDisplayName() : itemId;
        String buyStr = msg.formatCurrency(entry.buyPrice());
        String sellStr = msg.formatCurrency(entry.sellPrice());
        String spread = String.valueOf(entry.getSpreadPercent());

        List<String> lore;
        if (entry.buyEnabled() && entry.sellEnabled()) {
            lore = new ArrayList<>(msg.getList(lang, "servershop.lore.item-entry"));
            lore.replaceAll(l -> l.replace("{buy_price}", buyStr).replace("{sell_price}", sellStr).replace("{spread}", spread));
        } else if (entry.buyEnabled()) {
            lore = new ArrayList<>(msg.getList(lang, "servershop.lore.item-entry-buy-only"));
            lore.replaceAll(l -> l.replace("{buy_price}", buyStr));
        } else {
            lore = new ArrayList<>(msg.getList(lang, "servershop.lore.item-entry-sell-only"));
            lore.replaceAll(l -> l.replace("{sell_price}", sellStr));
        }

        // Create the proper ItemStack (with metadata for enchanted books, potions, etc.)
        ItemStack baseStack = shopItem != null ? shopItem.createItemStack(1) : new ItemStack(Material.BARRIER);
        return new ItemBuilder(baseStack).name("&f" + itemName).lore(lore).build();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventory) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();

        switch (slot) {
            case SLOT_BACK -> { SoundUtil.playClickSound(player); shopGuiManager.openMainMenu(player); return; }
            case SLOT_PREV -> { if (page > 0) { SoundUtil.playClickSound(player); shopGuiManager.openCategory(player, category, page - 1); } return; }
            case SLOT_NEXT -> {
                int totalPages = (int) Math.ceil((double) itemIds.size() / ITEMS_PER_PAGE);
                if (page < totalPages - 1) { SoundUtil.playClickSound(player); shopGuiManager.openCategory(player, category, page + 1); }
                return;
            }
            case SLOT_SEARCH -> {
                SoundUtil.playClickSound(player);
                ShopSearch.prompt(plugin, player,
                        query -> shopGuiManager.openSearchResults(player, query, 0),
                        () -> shopGuiManager.openCategory(player, category, page));
                return;
            }
        }

        if (slot >= 0 && slot < ITEMS_PER_PAGE) {
            int index = page * ITEMS_PER_PAGE + slot;
            if (index < itemIds.size()) {
                SoundUtil.playClickSound(player);
                shopGuiManager.openItemDetail(player, itemIds.get(index));
            }
        }
    }

    @Override public GuiType getType() { return GuiType.SHOP_CATEGORY; }
    @Override public @NotNull Inventory getInventory() { return inventory; }
}
