package pt.henrique.market.servershop.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pt.henrique.market.Market;

import java.util.ArrayList;
import java.util.List;

/**
 * The /shop command (alias /servershop).
 * Opens the server shop main GUI for players.
 * Supports "reload" subcommand for administrators.
 */
public class ShopCommand implements CommandExecutor, TabCompleter {

    private final Market plugin;

    public ShopCommand(Market plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, @NotNull String[] args) {

        // Handle reload subcommand
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("market.admin.reload")) {
                sender.sendMessage(plugin.getMessageManager().getPrefixed("messages.no-permission"));
                return true;
            }
            try {
                plugin.getShopPriceManager().reload();
                sender.sendMessage(plugin.getMessageManager().getPrefixed("servershop.messages.reload-success"));
            } catch (Exception e) {
                sender.sendMessage(plugin.getMessageManager().getPrefixed("servershop.messages.reload-failed"));
                plugin.getLogger().warning("[ServerShop] Reload failed: " + e.getMessage());
            }
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (!player.hasPermission("market.use")) {
            player.sendMessage(plugin.getMessageManager().getPrefixed("messages.no-permission"));
            return true;
        }

        if (!plugin.getEconomyManager().isReady()) {
            player.sendMessage(plugin.getMessageManager().getPrefixed("messages.economy-not-found"));
            return true;
        }

        // Open the server shop main menu
        plugin.getShopGuiManager().openMainMenu(player);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                  @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1 && sender.hasPermission("market.admin.reload")) {
            if ("reload".startsWith(args[0].toLowerCase())) {
                completions.add("reload");
            }
        }
        return completions;
    }
}

