package pt.henrique.market.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import pt.henrique.market.Market;
import pt.henrique.market.util.ItemBuilder;
import pt.henrique.market.util.SoundUtil;
import pt.henrique.market.util.TextUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The unified Market Hub GUI — the central entry point for all market features.
 * Contains 6 clickable elements; everything else is filler.
 *
 * Layout (54 slots / 6 rows):
 *   Row 0: Filler
 *   Row 1: [   ] [ Shop ] [   ] [ Community ] [   ] [ Auctions ] [   ]
 *   Row 2: Filler
 *   Row 3: [              ] [ Claim Items ] [              ]
 *   Row 4: Filler
 *   Row 5: [Exit] [filler...] [Player Head]
 *
 * Slot map:
 *   Shop tab           = slot 20  (row 2 center-left)
 *   Community Market    = slot 22  (row 2 center)
 *   Auctions tab        = slot 24  (row 2 center-right)
 *   Claim Items         = slot 31  (row 3 center)
 *   Exit button         = slot 45  (bottom-left)
 *   Player head         = slot 53  (bottom-right)
 */
public class MainMenuGui implements MarketGui {

    private final Market plugin;
    private final GuiManager guiManager;
    private Inventory inventory;
    private Player player;

    // ── Slot constants ──────────────────────────────────────────────
    /** Server Shop tab — row 2, column 2 */
    private static final int SLOT_SERVER_MARKET = 20;
    /** Community Market tab — row 2, column 4 (center) */
    private static final int SLOT_COMMUNITY_MARKET = 22;
    /** Auctions tab — row 2, column 6 */
    private static final int SLOT_AUCTIONS = 24;
    /** Claim Items — row 3, center */
    private static final int SLOT_CLAIM = 31;
    /** Player head — bottom-left corner */
    private static final int SLOT_PLAYER_HEAD = 45;
    /** Exit / close button — bottom-right corner */
    private static final int SLOT_EXIT = 53;

    public MainMenuGui(Market plugin, GuiManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    /**
     * Opens the hub menu for a player.
     */
    public void open(Player player) {
        this.player = player;

        String lang = plugin.getMessageManager().getPlayerLanguage(player);
        String title = TextUtil.colorizeToString(
                plugin.getMessageManager().getRaw(lang, "gui-titles.main-menu"));
        inventory = Bukkit.createInventory(this, 54, title);

        // Fill every slot with non-clickable glass panes
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .name(" ")
                .build();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, filler);
        }

        // Build the 7 interactive items (async for balance / earnings / claim data)
        buildMenuAsync();
    }

    /**
     * Fetches async data (balance, claim count) then builds the menu on the main thread.
     */
    private void buildMenuAsync() {
        var claimCountFuture = plugin.getClaimService().countPlayerClaimItems(player.getUniqueId());
        var balanceFuture = plugin.getEconomyManager().getBalanceCents(player.getUniqueId());

        claimCountFuture.thenCombine(balanceFuture, (claimCount, balanceCents) -> {
            double balance = pt.henrique.market.economy.EconomyManager.toDouble(balanceCents);
            Bukkit.getScheduler().runTask(plugin, () -> {
                buildMenu(claimCount, balance);
                player.openInventory(inventory);
                guiManager.registerGui(player.getUniqueId(), this);
                SoundUtil.playSound(player, plugin.getConfigManager().getClickSound());
            });
            return null;
        });
    }

    /**
     * Places exactly 6 interactive items into the inventory.
     */
    private void buildMenu(int claimCount, double balance) {
        var msgManager = plugin.getMessageManager();
        String lang = msgManager.getPlayerLanguage(player);

        // ── 1) Server Shop tab ──────────────────────────────────────
        inventory.setItem(SLOT_SERVER_MARKET, new ItemBuilder(Material.EMERALD_BLOCK)
                .name(msgManager.getButton(lang, "server-market"))
                .lore(msgManager.getLore(lang, "server-market"))
                .build());

        // ── 2) Community Market tab ─────────────────────────────────
        inventory.setItem(SLOT_COMMUNITY_MARKET, new ItemBuilder(Material.CHEST)
                .name(msgManager.getButton(lang, "community-market"))
                .lore(msgManager.getLore(lang, "community-market"))
                .build());

        // ── 3) Auctions tab ─────────────────────────────────────────
        inventory.setItem(SLOT_AUCTIONS, new ItemBuilder(Material.GOLD_INGOT)
                .name(msgManager.getButton(lang, "auctions"))
                .lore(msgManager.getLore(lang, "auctions"))
                .build());

        // ── 4) Claim Items (row 3 center) ───────────────────────────
        inventory.setItem(SLOT_CLAIM, new ItemBuilder(Material.ENDER_CHEST)
                .name(msgManager.getButton(lang, "claim-items"))
                .lore(msgManager.getLore(lang, "claim-items", Map.of(
                        "count", String.valueOf(claimCount)
                )))
                .glow(claimCount > 0)
                .build());

        // ── 5) Exit button (bottom-left) ────────────────────────────
        inventory.setItem(SLOT_EXIT, new ItemBuilder(Material.BARRIER)
                .name(msgManager.getButton(lang, "exit"))
                .lore(msgManager.getLore(lang, "exit"))
                .build());

        // ── 6) Player head (bottom-right) ───────────────────────────

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
        if (skullMeta != null) {
            skullMeta.setOwningPlayer(player);
            skullMeta.displayName(TextUtil.colorize(
                    msgManager.getButton(lang, "player-profile")
                            .replace("{player}", player.getName())));

            List<String> loreSrc = msgManager.getLore(lang, "player-profile", Map.of(
                    "balance", msgManager.formatCurrency(balance)
            ));
            List<net.kyori.adventure.text.Component> loreComponents = new ArrayList<>();
            for (String line : loreSrc) {
                loreComponents.add(TextUtil.colorize(line));
            }
            skullMeta.lore(loreComponents);
            head.setItemMeta(skullMeta);
        }
        inventory.setItem(SLOT_PLAYER_HEAD, head);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        if (event.getCurrentItem() == null) return;

        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        switch (slot) {
            // ── Primary navigation ──────────────────────────────────
            case SLOT_SERVER_MARKET -> {
                if (player.hasPermission("market.server")) {
                    plugin.getShopGuiManager().openMainMenu(player);
                } else {
                    noPermission(player);
                }
            }
            case SLOT_COMMUNITY_MARKET -> {
                if (player.hasPermission("market.community")) {
                    guiManager.openCommunityMenu(player);
                } else {
                    noPermission(player);
                }
            }
            case SLOT_AUCTIONS -> {
                if (player.hasPermission("market.auctions")) {
                    guiManager.openAuctionsMenu(player, NavigationOrigin.MAIN_MENU);
                } else {
                    noPermission(player);
                }
            }
            case SLOT_CLAIM -> {
                if (player.hasPermission("market.claim")) {
                    guiManager.openClaim(player, NavigationOrigin.MAIN_MENU);
                } else {
                    noPermission(player);
                }
            }

            // ── Exit (close GUI) ────────────────────────────────────
            case SLOT_EXIT -> player.closeInventory();

            // ── Player head (optional profile / no-op) ──────────────
            case SLOT_PLAYER_HEAD -> {
                // Currently a visual element only; could open a profile GUI later
                SoundUtil.playSound(player, plugin.getConfigManager().getClickSound());
            }
        }
    }

    private void noPermission(Player player) {
        player.sendMessage(plugin.getMessageManager().getPrefixedP(player, "messages.no-permission"));
        SoundUtil.playSound(player, plugin.getConfigManager().getErrorSound());
    }

    @Override
    public GuiType getType() {
        return GuiType.MAIN_MENU;
    }

    @Override
    public @org.jetbrains.annotations.NotNull Inventory getInventory() {
        return inventory;
    }
}
