package pt.henrique.market.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pt.henrique.market.Market;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Unified /market command (aliases: /m, /shop, /cmarket, /servershop).
 * Opens the Market Hub GUI. Supports "reload" subcommand for admins.
 */
public class MarketCommand implements CommandExecutor, TabCompleter {

    private final Market plugin;

    public MarketCommand(Market plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        // Handle reload subcommand
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("market.admin.reload")) {
                if (sender instanceof Player p) {
                    sender.sendMessage(plugin.getMessageManager().getPrefixedP(p, "messages.no-permission"));
                } else {
                    sender.sendMessage(plugin.getMessageManager().getPrefixed("messages.no-permission"));
                }
                return true;
            }
            plugin.reload();
            if (sender instanceof Player p) {
                sender.sendMessage(plugin.getMessageManager().getPrefixedP(p, "messages.reload-success"));
            } else {
                sender.sendMessage(plugin.getMessageManager().getPrefixed("messages.reload-success"));
            }
            return true;
        }

        // Only players can open the GUI
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessageManager().get("messages.player-only"));
            return true;
        }

        // Check basic permission
        if (!player.hasPermission("market.use")) {
            player.sendMessage(plugin.getMessageManager().getPrefixedP(player, "messages.no-permission"));
            return true;
        }

        // Open the unified Market Hub GUI
        plugin.getGuiManager().openMainMenu(player);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1 && sender.hasPermission("market.admin.reload")) {
            if ("reload".startsWith(args[0].toLowerCase())) {
                completions.add("reload");
            }
        }
        return completions;
    }
}
