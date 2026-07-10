package pt.henrique.market.servershop.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pt.henrique.market.Market;
import pt.henrique.market.servershop.service.ChestSellService;

/**
 * Listener that handles player interactions during an active Sell Chest session.
 * <ul>
 *   <li>Right-click on a block → pass to {@link ChestSellService}</li>
 *   <li>Move too far → cancel session</li>
 *   <li>Take damage → cancel session</li>
 *   <li>Disconnect → clean up session</li>
 * </ul>
 */
public class ChestSellListener implements Listener {

    private final Market plugin;

    public ChestSellListener(Market plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        Player player = event.getPlayer();
        ChestSellService service = plugin.getChestSellService();
        if (!service.hasActiveSession(player.getUniqueId())) return;

        // Prevent the normal container GUI from opening
        event.setCancelled(true);

        service.onContainerClick(player, event.getClickedBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only check if they actually moved blocks (not just head rotation)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        ChestSellService service = plugin.getChestSellService();
        if (!service.hasActiveSession(player.getUniqueId())) return;

        if (service.hasMovedTooFar(player)) {
            service.cancelSession(player, "servershop.messages.sell-chest-cancelled");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ChestSellService service = plugin.getChestSellService();
        if (!service.hasActiveSession(player.getUniqueId())) return;

        service.cancelSession(player, "servershop.messages.sell-chest-cancelled");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getChestSellService().handleQuit(event.getPlayer().getUniqueId());
    }
}

