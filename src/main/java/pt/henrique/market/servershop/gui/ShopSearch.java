package pt.henrique.market.servershop.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pt.henrique.market.Market;

import java.util.function.Consumer;

/**
 * Starts a shop search from any screen, honouring {@code server-shop.search-mode}.
 * <p>
 * Every entry point goes through here so the search behaves the same everywhere:
 * the category and result screens used to force the sign editor while the main
 * menu already respected the setting, which meant the same button asked for input
 * two different ways depending on where you clicked it.
 */
public final class ShopSearch {

    private ShopSearch() {}

    /**
     * Prompts the player for a query and runs {@code onQuery} with it on the main
     * thread. An empty query, "cancel", or a timeout runs {@code onCancel} instead.
     */
    public static void prompt(Market plugin, Player player,
                              Consumer<String> onQuery, Runnable onCancel) {
        player.closeInventory();

        Consumer<String> handler = query -> {
            if (query == null || query.isBlank()) {
                Bukkit.getScheduler().runTask(plugin, onCancel);
                return;
            }
            String trimmed = query.trim();
            Bukkit.getScheduler().runTask(plugin, () -> onQuery.accept(trimmed));
        };

        String mode = plugin.getConfig().getString("server-shop.search-mode", "chat");
        if ("sign".equalsIgnoreCase(mode)) {
            plugin.getShopSearchSignHandler().openSearchSign(player, handler);
        } else {
            plugin.getShopSearchSignHandler().openChatSearch(player, handler);
        }
    }
}
