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
import pt.henrique.market.util.ItemBuilder;
import pt.henrique.market.util.SoundUtil;
import pt.henrique.market.util.TextUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * The three ways to sell to the server, offered up front.
 * <p>
 * All three existed already, but only from buttons inside the shop menu —
 * {@code /sell} went straight to the inventory grid, so the hand and container
 * routes were invisible to anyone who reached for the command.
 *
 * <pre>
 *   Row 0: filler
 *   Row 1: [11] inventory   [13] held item   [15] nearby container
 *   Row 2: filler, [22] hint, [26] back
 * </pre>
 */
public class SellModeChooserGui implements MarketGui {

    private final Market plugin;
    private final ShopGuiManager shopGuiManager;
    private Inventory inventory;

    private static final int SLOT_INVENTORY = 11;
    private static final int SLOT_HAND = 13;
    private static final int SLOT_CONTAINER = 15;
    private static final int SLOT_HINT = 22;
    private static final int SLOT_BACK = 26;

    public SellModeChooserGui(Market plugin, ShopGuiManager shopGuiManager) {
        this.plugin = plugin;
        this.shopGuiManager = shopGuiManager;
    }

    public void open(Player player) {
        MessageManager msg = plugin.getMessageManager();
        String lang = msg.getPlayerLanguage(player);
        inventory = Bukkit.createInventory(this, 27,
                TextUtil.colorize(msg.getRaw(lang, "servershop.titles.sell-mode-chooser")));

        var filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 27; i++) inventory.setItem(i, filler);

        inventory.setItem(SLOT_INVENTORY, option(Material.CHEST,
                msg.getRaw(lang, "servershop.buttons.sell-mode-inventory"),
                msg.getList(lang, "servershop.lore.sell-mode-inventory"),
                isInventoryEnabled(), msg, lang));

        inventory.setItem(SLOT_HAND, option(Material.GOLD_INGOT,
                msg.getRaw(lang, "servershop.buttons.sell-mode-hand"),
                msg.getList(lang, "servershop.lore.sell-mode-hand"),
                isHandEnabled(), msg, lang));

        inventory.setItem(SLOT_CONTAINER, option(Material.ENDER_CHEST,
                msg.getRaw(lang, "servershop.buttons.sell-mode-container"),
                msg.getList(lang, "servershop.lore.sell-mode-container"),
                isContainerEnabled(), msg, lang));

        inventory.setItem(SLOT_HINT, new ItemBuilder(Material.PAPER)
                .name(msg.getRaw(lang, "servershop.buttons.sell-mode-hint"))
                .lore(msg.getList(lang, "servershop.lore.sell-mode-hint"))
                .build());

        inventory.setItem(SLOT_BACK, new ItemBuilder(Material.BARRIER)
                .name(msg.getRaw(lang, "servershop.buttons.back")).build());

        plugin.getGuiManager().registerGui(player.getUniqueId(), this);
        shopGuiManager.registerGui(player.getUniqueId(), this);
        player.openInventory(inventory);
        SoundUtil.playClickSound(player);
    }

    /** Builds one option, greyed out with a reason when its feature is switched off. */
    private org.bukkit.inventory.ItemStack option(Material icon, String name, List<String> lore,
                                                  boolean enabled, MessageManager msg, String lang) {
        List<String> finalLore = new ArrayList<>(lore);
        if (!enabled) {
            finalLore.add("");
            finalLore.add(msg.getRaw(lang, "servershop.lore.sell-mode-disabled"));
        }
        return new ItemBuilder(enabled ? icon : Material.GRAY_DYE)
                .name((enabled ? "" : "&8") + name).lore(finalLore).build();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventory) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        switch (event.getRawSlot()) {
            case SLOT_INVENTORY -> {
                if (!isInventoryEnabled()) { deny(player); return; }
                SoundUtil.playClickSound(player);
                shopGuiManager.openSellInventory(player);
            }
            case SLOT_HAND -> {
                if (!isHandEnabled()) { deny(player); return; }
                player.closeInventory();
                shopGuiManager.startSellHand(player, SellVariantDetailGui.Origin.SELL_INVENTORY);
            }
            case SLOT_CONTAINER -> {
                if (!isContainerEnabled()) { deny(player); return; }
                SoundUtil.playClickSound(player);
                plugin.getChestSellService().startSession(player);
            }
            case SLOT_BACK -> {
                SoundUtil.playClickSound(player);
                shopGuiManager.openMainMenu(player);
            }
            default -> { /* filler */ }
        }
    }

    private void deny(Player player) {
        SoundUtil.playErrorSound(player);
        player.sendMessage(plugin.getMessageManager().getPrefixedP(player,
                "servershop.messages.sell-mode-disabled"));
    }

    private boolean isInventoryEnabled() {
        return plugin.getConfig().getBoolean("server-shop.enable-sell-all", true);
    }

    private boolean isHandEnabled() {
        return plugin.getConfig().getBoolean("server-shop.enable-sell-hand", true);
    }

    private boolean isContainerEnabled() {
        return plugin.getConfig().getBoolean("server-shop.enable-sell-chest", true);
    }

    @Override public GuiType getType() { return GuiType.SHOP_SELL_CHOOSER; }
    @Override public @NotNull Inventory getInventory() { return inventory; }
}
