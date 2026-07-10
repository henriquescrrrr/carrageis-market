package pt.henrique.market.servershop.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import pt.henrique.market.Market;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Handles sign-based search input for the server shop.
 * Opens a sign editor for the player to type their search query.
 * <p>
 * The temporary sign is placed 1 block below the player's feet so it stays
 * within the client's interaction range (~6 blocks). It is protected against
 * destruction while active and cleaned up via {@link SignChangeEvent},
 * {@link PlayerQuitEvent}, or a 30-second safety timeout.
 */
public class ShopSearchSignHandler implements Listener {

    private final Market plugin;
    private final Map<UUID, Consumer<String>> pendingCallbacks = new ConcurrentHashMap<>();
    private final Map<UUID, SignRestoreData> pendingOriginalBlocks = new ConcurrentHashMap<>();

    // ── Chat-based search ────────────────────────────────────────
    private final Map<UUID, Consumer<String>> pendingChatCallbacks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> pendingChatTimeouts = new ConcurrentHashMap<>();

    /** Known sign prompt / decoration texts across locales — filtered out of query results. */
    private static final Set<String> SIGN_FILTER = Set.of(
            "search:", "pesquisar:", "recherche :",
            "type item name", "escreve o nome", "tapez le nom",
            "above", "em cima", "au-dessus",
            "═══════════"
    );

    public ShopSearchSignHandler(Market plugin) {
        this.plugin = plugin;
    }

    // ══════════════════════════════════════════════════════════════
    //  Open search sign
    // ══════════════════════════════════════════════════════════════

    /**
     * Opens a sign editor for the player to type a search query.
     * When the player closes the sign, the callback is invoked with the query string.
     *
     * @param player   the player who is searching
     * @param callback receives the search query (or null if empty/cancelled)
     */
    public void openSearchSign(Player player, Consumer<String> callback) {
        pendingCallbacks.put(player.getUniqueId(), callback);

        String lang = plugin.getMessageManager().getPlayerLanguage(player);
        String promptLine = plugin.getMessageManager().getRaw(lang, "servershop.messages.search-sign-prompt");
        String hintLine = plugin.getMessageManager().getRaw(lang, "servershop.messages.search-sign-line2");

        // Place the sign 1 block below the player's feet so it stays within the
        // client's ~6-block interaction range (avoids premature editor close).
        int signY = Math.max(player.getWorld().getMinHeight(),
                player.getLocation().getBlockY() - 1);
        Location signLoc = new Location(
                player.getWorld(),
                player.getLocation().getBlockX(),
                signY,
                player.getLocation().getBlockZ()
        );

        Block block = signLoc.getBlock();
        BlockData originalData = block.getBlockData().clone();
        pendingOriginalBlocks.put(player.getUniqueId(), new SignRestoreData(signLoc, originalData));

        block.setType(Material.OAK_SIGN);

        Sign sign = (Sign) block.getState();
        sign.setWaxed(false); // CRITICAL — must not be waxed or Paper treats it as non-editable
        var side = sign.getSide(Side.FRONT);
        side.line(0, Component.text("═══════════"));
        side.line(1, Component.text(promptLine != null ? promptLine : "Search:").color(NamedTextColor.GOLD));
        side.line(2, Component.text(hintLine != null ? hintLine : "Type item name").color(NamedTextColor.GRAY));
        side.line(3, Component.text("═══════════"));
        sign.update(true, false);

        // Open the sign editor — must happen AFTER update, BEFORE block is restored
        player.openSign(sign, Side.FRONT);

        // Safety fallback — restore block after 30 seconds if SignChangeEvent never fires
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            UUID uuid = player.getUniqueId();
            if (pendingOriginalBlocks.containsKey(uuid)) {
                restoreBlock(uuid);
                Consumer<String> cb = pendingCallbacks.remove(uuid);
                if (cb != null) {
                    cb.accept(null); // timed out — treat as cancelled
                }
            }
        }, 30 * 20L); // 30 seconds
    }

    // ══════════════════════════════════════════════════════════════
    //  Sign change — player submitted or closed the sign editor
    // ══════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSignChange(SignChangeEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Consumer<String> callback = pendingCallbacks.remove(uuid);
        if (callback == null) return;

        event.setCancelled(true); // Don't actually modify the sign
        restoreBlock(uuid);       // Restore the original block NOW

        // Collect text from all lines, skipping prompt / decoration lines
        StringBuilder query = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            Component line = event.line(i);
            if (line == null) continue;
            String text = PlainTextComponentSerializer.plainText().serialize(line).trim();
            if (!text.isEmpty() && !SIGN_FILTER.contains(text.toLowerCase())) {
                if (!query.isEmpty()) query.append(" ");
                query.append(text);
            }
        }

        String finalQuery = query.toString().trim();
        if (finalQuery.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null));
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(finalQuery));
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Chat-based search — player types query in chat, Enter submits
    // ══════════════════════════════════════════════════════════════

    /**
     * Opens a chat-based search prompt. The player types their query in chat
     * and presses Enter to submit. Typing "cancel" (any case) aborts.
     *
     * @param player   the player who is searching
     * @param callback receives the search query (or null if cancelled/timed out)
     */
    public void openChatSearch(Player player, Consumer<String> callback) {
        UUID uuid = player.getUniqueId();

        // Cancel any existing chat search session
        cancelChatSearch(uuid, false);

        pendingChatCallbacks.put(uuid, callback);

        // Send prompt message
        String lang = plugin.getMessageManager().getPlayerLanguage(player);
        player.sendMessage(plugin.getMessageManager().getPrefixed(lang, "servershop.messages.search-chat-prompt"));

        // Safety timeout — 30 seconds
        BukkitTask timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Consumer<String> cb = pendingChatCallbacks.remove(uuid);
            pendingChatTimeouts.remove(uuid);
            if (cb != null) {
                player.sendMessage(plugin.getMessageManager().getPrefixed(lang, "servershop.messages.search-chat-timeout"));
                cb.accept(null);
            }
        }, 30 * 20L);
        pendingChatTimeouts.put(uuid, timeoutTask);
    }

    /**
     * Returns true if the player currently has a pending chat search.
     */
    public boolean hasPendingChatSearch(UUID uuid) {
        return pendingChatCallbacks.containsKey(uuid);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Consumer<String> callback = pendingChatCallbacks.remove(uuid);
        if (callback == null) return;

        // Cancel the chat message so it doesn't broadcast
        event.setCancelled(true);

        // Cancel the timeout task
        BukkitTask timeout = pendingChatTimeouts.remove(uuid);
        if (timeout != null) timeout.cancel();

        // Extract plain text from the chat component
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        // Check for cancel keyword
        if (text.equalsIgnoreCase("cancel")) {
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null));
            return;
        }

        // Pass the query to the callback on the main thread
        String query = text.isEmpty() ? null : text;
        Bukkit.getScheduler().runTask(plugin, () -> callback.accept(query));
    }

    /**
     * Cancels a pending chat search for the given player.
     */
    private void cancelChatSearch(UUID uuid, boolean notify) {
        Consumer<String> cb = pendingChatCallbacks.remove(uuid);
        BukkitTask timeout = pendingChatTimeouts.remove(uuid);
        if (timeout != null) timeout.cancel();
        // Don't invoke callback on cancel — the caller handles re-opening
    }

    // ══════════════════════════════════════════════════════════════
    //  Sign protection — prevent breaking the temporary sign
    // ══════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isProtectedSignLocation(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockDamage(BlockDamageEvent event) {
        if (isProtectedSignLocation(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /**
     * Checks whether the given location matches any pending search sign.
     */
    private boolean isProtectedSignLocation(Location loc) {
        for (SignRestoreData data : pendingOriginalBlocks.values()) {
            if (data.location().getWorld().equals(loc.getWorld())
                    && data.location().getBlockX() == loc.getBlockX()
                    && data.location().getBlockY() == loc.getBlockY()
                    && data.location().getBlockZ() == loc.getBlockZ()) {
                return true;
            }
        }
        return false;
    }

    // ══════════════════════════════════════════════════════════════
    //  Cleanup
    // ══════════════════════════════════════════════════════════════

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pendingCallbacks.remove(uuid);
        restoreBlock(uuid);
        cancelChatSearch(uuid, false);
    }

    /**
     * Restores the original block at the sign location for the given player.
     */
    private void restoreBlock(UUID uuid) {
        SignRestoreData data = pendingOriginalBlocks.remove(uuid);
        if (data != null) {
            data.location().getBlock().setBlockData(data.originalData());
        }
    }

    private record SignRestoreData(Location location, BlockData originalData) {}
}

