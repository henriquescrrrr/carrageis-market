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
import pt.henrique.market.servershop.ShulkerUtil;
import pt.henrique.market.servershop.service.ShulkerSellService;
import pt.henrique.market.servershop.service.ShulkerSellService.ContentsScan;
import pt.henrique.market.servershop.service.ShulkerSellService.OptionQuote;
import pt.henrique.market.servershop.service.ShulkerSellService.ShulkerSellOption;
import pt.henrique.market.util.ItemBuilder;
import pt.henrique.market.util.SoundUtil;
import pt.henrique.market.util.TextUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Chooser presented when selling a shulker box that contains items. Offers three
 * options: sell the empty box (contents returned), sell box + contents, or sell
 * the contents only (box kept).
 * <p>
 * The shulker is identified by its <b>player-inventory slot index</b>; the live
 * stack is re-fetched and re-validated on open and on every click, so the chooser
 * never acts on the wrong (or a vanished) box.
 */
public class ShulkerSellChooserGui implements MarketGui {

    private final Market plugin;
    private final ShopGuiManager shopGuiManager;
    private final int playerSlot;
    private Inventory inventory;

    private static final int SLOT_BOX = 4;
    private static final int SLOT_BOX_ONLY = 11;
    private static final int SLOT_BOX_CONTENTS = 13;
    private static final int SLOT_CONTENTS_ONLY = 15;
    private static final int SLOT_BACK = 26;

    public ShulkerSellChooserGui(Market plugin, ShopGuiManager shopGuiManager, int playerSlot) {
        this.plugin = plugin;
        this.shopGuiManager = shopGuiManager;
        this.playerSlot = playerSlot;
    }

    public void open(Player player) {
        ItemStack live = player.getInventory().getItem(playerSlot);
        if (!ShulkerUtil.isShulkerBox(live) || !ShulkerUtil.hasContents(live)) {
            SoundUtil.playErrorSound(player);
            player.sendMessage(plugin.getMessageManager().getPrefixedP(player, "servershop.messages.shulker-gone"));
            shopGuiManager.openSellInventory(player);
            return;
        }

        MessageManager msg = plugin.getMessageManager();
        String lang = msg.getPlayerLanguage(player);
        String title = msg.getRaw(lang, "servershop.titles.shulker-sell-chooser");
        inventory = Bukkit.createInventory(this, 27, TextUtil.colorize(title));

        var filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 27; i++) inventory.setItem(i, filler);

        ShulkerSellService service = plugin.getShulkerSellService();
        ContentsScan scan = service.scan(live);

        // ── Shulker + summary ────────────────────────────────
        List<String> summary = new ArrayList<>(msg.getList(lang, "servershop.lore.shulker-summary"));
        summary.replaceAll(l -> l
                .replace("{count}", String.valueOf(ShulkerUtil.countTotalItems(live)))
                .replace("{value}", msg.formatCurrency(service.netOf(scan.grossSellable()))));
        inventory.setItem(SLOT_BOX, new ItemBuilder(live.clone())
                .name("&f" + MaterialUtil.formatName(live.getType()))
                .lore(summary).build());

        // ── Options ──────────────────────────────────────────
        renderOption(player, SLOT_BOX_ONLY, ShulkerSellOption.BOX_ONLY, Material.CHEST,
                "servershop.buttons.shulker-sell-box-only", "servershop.lore.shulker-box-only", live, msg, lang);
        renderOption(player, SLOT_BOX_CONTENTS, ShulkerSellOption.BOX_AND_CONTENTS, Material.EMERALD_BLOCK,
                "servershop.buttons.shulker-sell-box-contents", "servershop.lore.shulker-box-contents", live, msg, lang);
        renderOption(player, SLOT_CONTENTS_ONLY, ShulkerSellOption.CONTENTS_ONLY, Material.GOLD_INGOT,
                "servershop.buttons.shulker-sell-contents-only", "servershop.lore.shulker-contents-only", live, msg, lang);

        inventory.setItem(SLOT_BACK, new ItemBuilder(Material.BARRIER)
                .name(msg.getRaw(lang, "servershop.buttons.back")).build());

        plugin.getGuiManager().registerGui(player.getUniqueId(), this);
        shopGuiManager.registerGui(player.getUniqueId(), this);
        player.openInventory(inventory);
    }

    private void renderOption(Player player, int slot, ShulkerSellOption option, Material icon,
                              String nameKey, String loreKey, ItemStack live, MessageManager msg, String lang) {
        OptionQuote quote = plugin.getShulkerSellService().quote(live, option);
        if (!quote.available()) {
            inventory.setItem(slot, new ItemBuilder(Material.GRAY_CONCRETE)
                    .name(msg.getRaw(lang, "servershop.buttons.shulker-sell-disabled"))
                    .lore(msg.getList(lang, "servershop.lore.shulker-sell-disabled-lore"))
                    .build());
            return;
        }
        List<String> lore = new ArrayList<>(msg.getList(lang, loreKey));
        lore.replaceAll(l -> l
                .replace("{total}", msg.formatCurrency(quote.totalNet()))
                .replace("{returned}", String.valueOf(quote.returnedItems())));
        inventory.setItem(slot, new ItemBuilder(icon)
                .name(msg.getRaw(lang, nameKey)).lore(lore).build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() != inventory) return;
        if (event.isShiftClick()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();

        if (slot == SLOT_BACK) {
            SoundUtil.playClickSound(player);
            shopGuiManager.openSellInventory(player);
            return;
        }

        ShulkerSellOption option = switch (slot) {
            case SLOT_BOX_ONLY -> ShulkerSellOption.BOX_ONLY;
            case SLOT_BOX_CONTENTS -> ShulkerSellOption.BOX_AND_CONTENTS;
            case SLOT_CONTENTS_ONLY -> ShulkerSellOption.CONTENTS_ONLY;
            default -> null;
        };
        if (option == null) return;

        ItemStack live = player.getInventory().getItem(playerSlot);
        if (!ShulkerUtil.isShulkerBox(live) || !ShulkerUtil.hasContents(live)) {
            SoundUtil.playErrorSound(player);
            player.sendMessage(plugin.getMessageManager().getPrefixedP(player, "servershop.messages.shulker-gone"));
            shopGuiManager.openSellInventory(player);
            return;
        }
        if (!plugin.getShulkerSellService().quote(live, option).available()) {
            SoundUtil.playErrorSound(player);
            return;
        }
        SoundUtil.playClickSound(player);
        plugin.getShulkerSellService().execute(player, playerSlot, option);
    }

    @Override public GuiType getType() { return GuiType.SHOP_SHULKER_SELL_CHOOSER; }
    @Override public @NotNull Inventory getInventory() { return inventory; }
}
