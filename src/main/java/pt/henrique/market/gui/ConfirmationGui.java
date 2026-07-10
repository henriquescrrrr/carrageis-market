package pt.henrique.market.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import pt.henrique.market.Market;
import pt.henrique.market.util.ItemBuilder;
import pt.henrique.market.util.TextUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Confirmation dialog GUI for important actions like purchases and cancellations.
 */
public class ConfirmationGui implements MarketGui {

    private final Market plugin;
    private final GuiManager guiManager;
    private final ConfirmCallback callback;
    private final String title;
    private final String[] infoLines;

    private Inventory inventory;

    private static final int INFO_SLOT = 13;
    private static final int CONFIRM_SLOT = 29;
    private static final int CANCEL_SLOT = 33;

    @FunctionalInterface
    public interface ConfirmCallback {
        void onComplete(boolean confirmed);
    }

    public ConfirmationGui(Market plugin, GuiManager guiManager,
                           ConfirmCallback callback, String title, String... infoLines) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.callback = callback;
        this.title = title;
        this.infoLines = infoLines;
    }

    public void open(Player player) {
        inventory = Bukkit.createInventory(this, 45, TextUtil.colorizeToString(title));
        buildGui();
        player.openInventory(inventory);
        guiManager.registerGui(player.getUniqueId(), this);
    }

    private void buildGui() {
        var msgManager = plugin.getMessageManager();

        // Fill with glass
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 45; i++) {
            inventory.setItem(i, filler);
        }

        // Info display
        List<String> lore = new ArrayList<>();
        for (String line : infoLines) {
            lore.add(line);
        }

        inventory.setItem(INFO_SLOT, new ItemBuilder(Material.PAPER)
                .name("&e&lConfirm Action")
                .lore(lore)
                .build());

        // Confirm button
        inventory.setItem(CONFIRM_SLOT, new ItemBuilder(Material.LIME_WOOL)
                .name(msgManager.getButton("confirm"))
                .lore("&aClick to confirm")
                .glow()
                .build());

        // Cancel button
        inventory.setItem(CANCEL_SLOT, new ItemBuilder(Material.RED_WOOL)
                .name(msgManager.getButton("cancel"))
                .lore("&cClick to cancel")
                .build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        if (slot == CONFIRM_SLOT) {
            playSound(player, plugin.getConfigManager().getSuccessSound());
            player.closeInventory();
            callback.onComplete(true);
        } else if (slot == CANCEL_SLOT) {
            player.closeInventory();
            callback.onComplete(false);
        }
    }

    private void playSound(Player player, String soundName) {
        pt.henrique.market.util.SoundUtil.playSound(player, soundName);
    }

    @Override
    public GuiType getType() {
        return GuiType.CONFIRMATION;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

