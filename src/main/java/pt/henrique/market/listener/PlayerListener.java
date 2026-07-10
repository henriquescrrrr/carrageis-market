package pt.henrique.market.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pt.henrique.market.Market;
import pt.henrique.market.model.SaleNotification;

import java.util.Map;

/**
 * Listener for player join/quit events.
 * Notifies players of pending items and sale notifications on join.
 */
public class PlayerListener implements Listener {

    private final Market plugin;

    public PlayerListener(Market plugin) {
        this.plugin = plugin;
    }

    /**
     * Notifies players of pending claims and earnings when they join.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Delay notification slightly to ensure player is fully loaded
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            notifyPendingItems(player);
        }, 40L); // 2 second delay
    }

    /**
     * Cleans up when a player quits.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Close any open market GUI to prevent issues
        if (plugin.getGuiManager().hasGuiOpen(player.getUniqueId())) {
            player.closeInventory();
        }
    }

    /**
     * Notifies a player about pending claims and offline sale notifications.
     */
    private void notifyPendingItems(Player player) {
        var msgManager = plugin.getMessageManager();

        // Check for pending claim items
        plugin.getClaimService().countPlayerClaimItems(player.getUniqueId())
                .thenAccept(claimCount -> {
                    if (claimCount > 0) {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (player.isOnline()) {
                                player.sendMessage(msgManager.getPrefixedP(player, "messages.claim-items",
                                        Map.of("count", String.valueOf(claimCount))));
                            }
                        });
                    }
                });

        // Check for pending sale notifications (items sold while offline)
        plugin.getDatabaseManager().getSaleNotifications(player.getUniqueId())
                .thenAccept(notifications -> {
                    if (!notifications.isEmpty()) {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (player.isOnline()) {
                                for (SaleNotification sale : notifications) {
                                    player.sendMessage(msgManager.getPrefixedP(player, "messages.item-sold-offline",
                                            Map.of(
                                                    "item", sale.getItemName(),
                                                    "price", sale.getFormattedPrice(),
                                                    "buyer", sale.getBuyerName()
                                            )));
                                }
                                plugin.getDatabaseManager().clearSaleNotifications(player.getUniqueId());
                            }
                        });
                    }
                });

        // Check for any failed deposits stored as pending earnings (fallback)
        plugin.getEarningsService().getPendingEarnings(player.getUniqueId())
                .thenAccept(earnings -> {
                    if (earnings > 0) {
                        // Attempt to deposit the failed earnings now
                        plugin.getEconomyManager().depositCents(
                                player.getUniqueId(),
                                pt.henrique.market.economy.EconomyManager.toCents(earnings),
                                "Market: Recovering failed deposit"
                        ).thenAccept(success -> {
                            if (success) {
                                plugin.getDatabaseManager().withdrawAllEarnings(player.getUniqueId());
                                plugin.getServer().getScheduler().runTask(plugin, () -> {
                                    if (player.isOnline()) {
                                        player.sendMessage(msgManager.getPrefixedP(player, "messages.earnings-recovered",
                                                "amount", msgManager.formatCurrency(earnings)));
                                    }
                                });
                            }
                        });
                    }
                });
    }
}
