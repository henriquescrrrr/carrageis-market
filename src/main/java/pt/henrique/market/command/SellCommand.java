package pt.henrique.market.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pt.henrique.market.Market;
import pt.henrique.market.servershop.gui.SellVariantDetailGui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /sell} (also {@code /vender}, {@code /venda}, {@code /s}).
 * <p>
 * With no argument it opens the chooser with all three ways to sell. The shop has
 * always had them, but the bare command used to jump straight to the inventory
 * grid, which left the other two reachable only from inside the shop menu.
 * Sub-commands skip the chooser: {@code /sell hand}, {@code /sell container}.
 */
public class SellCommand implements CommandExecutor, TabCompleter {

    private static final List<String> MODES =
            List.of("inventory", "hand", "container", "inventario", "mao", "bau");

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

        String mode = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        switch (mode) {
            case "" -> plugin.getShopGuiManager().openSellModeChooser(player);
            case "inventory", "inventario", "inv", "i" -> plugin.getShopGuiManager().openSellInventory(player);
            case "hand", "mao", "mão", "h", "m" ->
                    plugin.getShopGuiManager().startSellHand(player, SellVariantDetailGui.Origin.SELL_INVENTORY);
            case "container", "chest", "bau", "baú", "c", "b" -> {
                if (!plugin.getConfig().getBoolean("server-shop.enable-sell-chest", true)) {
                    player.sendMessage(plugin.getMessageManager().getPrefixedP(player,
                            "servershop.messages.sell-mode-disabled"));
                    return true;
                }
                plugin.getChestSellService().startSession(player);
            }
            default -> plugin.getShopGuiManager().openSellModeChooser(player);
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String mode : MODES) {
            if (mode.startsWith(prefix)) matches.add(mode);
        }
        return matches;
    }
}
