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

/**
 * GUI for viewing and withdrawing pending earnings from sales.
 */
public class EarningsGui implements MarketGui {

    private final Market plugin;
    private final GuiManager guiManager;
    private Inventory inventory;
    private Player player;
    private double pendingAmount;

    /** Where the player navigated from — determines Back behaviour */
    private NavigationOrigin origin = NavigationOrigin.MAIN_MENU;

    private static final int EARNINGS_DISPLAY_SLOT = 13;
    private static final int WITHDRAW_SLOT = 31;
    private static final int BACK_SLOT = 53;

    public EarningsGui(Market plugin, GuiManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    /**
     * Opens the earnings GUI with origin tracking.
     */
    public void open(Player player, NavigationOrigin origin) {
        this.player = player;
        this.origin = origin;

        loadAndOpen();
    }

    public void open(Player player) {
        this.player = player;

        loadAndOpen();
    }

    private void loadAndOpen() {
        plugin.getEarningsService().getPendingEarnings(player.getUniqueId())
                .thenAccept(amount -> {
                    this.pendingAmount = amount;

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        buildGui();
                        player.openInventory(inventory);
                        guiManager.registerGui(player.getUniqueId(), this);
                    });
                });
    }

    private void buildGui() {
        var msgManager = plugin.getMessageManager();

        String title = TextUtil.colorizeToString(msgManager.getRaw("gui-titles.earnings"));
        inventory = Bukkit.createInventory(this, 54, title);

        // Fill with glass
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, filler);
        }

        // Earnings display
        inventory.setItem(EARNINGS_DISPLAY_SLOT, new ItemBuilder(Material.EMERALD_BLOCK)
                .name("&a&lPending Earnings")
                .lore(
                        "",
                        "&7Total: &a" + msgManager.formatCurrency(pendingAmount),
                        "",
                        "&7This is money from your sales",
                        "&7waiting to be withdrawn."
                )
                .glow(pendingAmount > 0)
                .build());

        // Withdraw button
        if (pendingAmount > 0) {
            inventory.setItem(WITHDRAW_SLOT, new ItemBuilder(Material.GOLD_BLOCK)
                    .name(msgManager.getButton("withdraw"))
                    .lore(
                            "&7Click to withdraw all earnings",
                            "&7Amount: &a" + msgManager.formatCurrency(pendingAmount)
                    )
                    .glow()
                    .build());
        } else {
            inventory.setItem(WITHDRAW_SLOT, new ItemBuilder(Material.BARRIER)
                    .name("&cNo Earnings")
                    .lore("&7You have no pending earnings to withdraw.")
                    .build());
        }

        // Back button
        inventory.setItem(BACK_SLOT, new ItemBuilder(Material.BARRIER)
                .name(msgManager.getButton("back"))
                .build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        if (slot == BACK_SLOT) {
            navigateBack(player);
            return;
        }

        if (slot == WITHDRAW_SLOT && pendingAmount > 0) {
            withdrawEarnings(player);
        }
    }

    /**
     * Navigate back based on origin.
     */
    private void navigateBack(Player player) {
        switch (origin) {
            case COMMUNITY_MENU -> guiManager.openCommunityMenu(player);
            case AUCTIONS_MENU -> guiManager.openAuctionsMenu(player, NavigationOrigin.MAIN_MENU);
            default -> guiManager.openMainMenu(player); // MAIN_MENU and any other
        }
    }

    private void withdrawEarnings(Player player) {
        var msgManager = plugin.getMessageManager();

        plugin.getEarningsService().withdrawAll(player)
                .thenAccept(result -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (result.isSuccess()) {
                            // Fetch balance asynchronously
                            plugin.getEconomyManager().getBalanceCents(player.getUniqueId())
                                    .thenAccept(balanceCents -> {
                                        Bukkit.getScheduler().runTask(plugin, () -> {
                                            double newBalance = pt.henrique.market.economy.EconomyManager.toDouble(balanceCents);
                                            player.sendMessage(msgManager.getPrefixed("messages.earnings-withdrawn",
                                                    java.util.Map.of(
                                                            "amount", msgManager.formatCurrency(result.getAmount()),
                                                            "balance", msgManager.formatCurrency(newBalance)
                                                    )));
                                            playSound(player, plugin.getConfigManager().getSuccessSound());
                                            open(player); // Refresh
                                        });
                                    });
                        } else {
                            if ("no_earnings".equals(result.getError())) {
                                player.sendMessage(msgManager.getPrefixed("messages.earnings-empty"));
                            } else {
                                player.sendMessage(msgManager.getPrefixed("messages.earnings-empty"));
                            }
                            playSound(player, plugin.getConfigManager().getErrorSound());
                        }
                    });
                });
    }

    private void playSound(Player player, String soundName) {
        pt.henrique.market.util.SoundUtil.playSound(player, soundName);
    }

    @Override
    public GuiType getType() {
        return GuiType.EARNINGS;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

