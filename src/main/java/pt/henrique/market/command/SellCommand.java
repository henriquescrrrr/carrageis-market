package pt.henrique.market.command;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pt.henrique.market.Market;
import java.util.Collections;
import java.util.List;
public class SellCommand implements CommandExecutor, TabCompleter {
    private final Market plugin;
    public SellCommand(Market plugin) {
        this.plugin = plugin;
    }
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessageManager().get("messages.player-only"));
            return true;
        }
        if (!player.hasPermission("market.sell")) {
            player.sendMessage(plugin.getMessageManager().getPrefixedP(player, "messages.no-permission"));
            return true;
        }
        if (!plugin.getEconomyManager().isReady()) {
            player.sendMessage(plugin.getMessageManager().getPrefixedP(player, "messages.economy-not-found"));
            return true;
        }
        plugin.getShopGuiManager().openSellInventory(player);
        return true;
    }
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        return Collections.emptyList();
    }
}