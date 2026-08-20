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
import pt.henrique.market.servershop.MaterialUtil;
import pt.henrique.market.servershop.model.ShopItem;
import pt.henrique.market.servershop.model.ShopPriceEntry;
import pt.henrique.market.util.ItemBuilder;
import pt.henrique.market.util.SoundUtil;
import pt.henrique.market.util.TextUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Paginated GUI showing search results in the server shop.
 */
public class ShopSearchResultsGui implements MarketGui {

    private final Market plugin;
    private final ShopGuiManager shopGuiManager;
    private final String query;
    private final int page;
    private Inventory inventory;
    private List<String> results;

    private static final int SLOT_INFO = 45, SLOT_PREV = 48, SLOT_SEARCH = 49, SLOT_NEXT = 50, SLOT_BACK = 53;
    private static final int ITEMS_PER_PAGE = 45;

    public ShopSearchResultsGui(Market plugin, ShopGuiManager shopGuiManager, String query, int page) {
        this.plugin = plugin;
        this.shopGuiManager = shopGuiManager;
        this.query = query;
        this.page = page;
    }

    public void open(Player player) {
        MessageManager msg = plugin.getMessageManager();
        String lang = msg.getPlayerLanguage(player);
        String title = msg.getRaw(lang, "servershop.titles.search-results").replace("{query}", query);
        inventory = Bukkit.createInventory(this, 54, TextUtil.colorize(title));

        results = MaterialUtil.searchItems(query, plugin.getShopPriceManager().getItemRegistry(),
                        msg.getSearchAliases(lang)).stream()
                .filter(id -> plugin.getShopPriceManager().getPriceEntry(id) != null)
                .toList();

        if (results.isEmpty()) {
            player.sendMessage(msg.getPrefixed(lang, "servershop.messages.search-no-results", "query", query));
            shopGuiManager.openMainMenu(player);
            return;
        }

        int totalPages = Math.max(1, (int) Math.ceil((double) results.size() / ITEMS_PER_PAGE));
        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, results.size());

        for (int i = start; i < end; i++) {
            String itemId = results.get(i);
            ShopPriceEntry entry = plugin.getShopPriceManager().getPriceEntry(itemId);
            if (entry == null) continue;
            inventory.setItem(i - start, buildIcon(itemId, entry, msg, lang));
        }

        var fillerItem = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 45; i < 54; i++) inventory.setItem(i, fillerItem);

        inventory.setItem(SLOT_BACK, new ItemBuilder(Material.BARRIER).name(msg.getRaw(lang, "servershop.buttons.back")).build());
        if (page > 0) inventory.setItem(SLOT_PREV, new ItemBuilder(Material.ARROW).name(msg.getRaw(lang, "servershop.buttons.prev-page")).build());
        inventory.setItem(SLOT_INFO, new ItemBuilder(Material.PAPER).name("&7Page " + (page+1) + "/" + totalPages).lore("&7Found " + results.size() + " items").build());
        if (page < totalPages - 1) inventory.setItem(SLOT_NEXT, new ItemBuilder(Material.ARROW).name(msg.getRaw(lang, "servershop.buttons.next-page")).build());
        inventory.setItem(SLOT_SEARCH, new ItemBuilder(Material.COMPASS).name(msg.getRaw(lang, "servershop.buttons.search")).build());

        plugin.getGuiManager().registerGui(player.getUniqueId(), this);
        shopGuiManager.registerGui(player.getUniqueId(), this);
        player.openInventory(inventory);
    }

    private ItemStack buildIcon(String itemId, ShopPriceEntry entry, MessageManager msg, String lang) {
        ShopItem shopItem = plugin.getShopPriceManager().getShopItem(itemId);
        String itemName = shopItem != null ? shopItem.getDisplayName() : itemId;
        List<String> lore = new ArrayList<>(msg.getList(lang, "servershop.lore.item-entry"));
        lore.replaceAll(l -> l.replace("{buy_price}", msg.formatCurrency(entry.buyPrice()))
                .replace("{sell_price}", msg.formatCurrency(entry.sellPrice()))
                .replace("{spread}", String.valueOf(entry.getSpreadPercent())));

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
            case SLOT_PREV -> { if (page > 0) { SoundUtil.playClickSound(player); shopGuiManager.openSearchResults(player, query, page - 1); } return; }
            case SLOT_NEXT -> {
                int tp = (int) Math.ceil((double) results.size() / ITEMS_PER_PAGE);
                if (page < tp - 1) { SoundUtil.playClickSound(player); shopGuiManager.openSearchResults(player, query, page + 1); }
                return;
            }
            case SLOT_SEARCH -> {
                SoundUtil.playClickSound(player);
                ShopSearch.prompt(plugin, player,
                        query -> shopGuiManager.openSearchResults(player, query, 0),
                        () -> shopGuiManager.openMainMenu(player));
                return;
            }
        }

        if (slot >= 0 && slot < ITEMS_PER_PAGE) {
            int idx = page * ITEMS_PER_PAGE + slot;
            if (idx < results.size()) { SoundUtil.playClickSound(player); shopGuiManager.openItemDetail(player, results.get(idx)); }
        }
    }

    @Override public GuiType getType() { return GuiType.SHOP_SEARCH_RESULTS; }
    @Override public @NotNull Inventory getInventory() { return inventory; }
}
