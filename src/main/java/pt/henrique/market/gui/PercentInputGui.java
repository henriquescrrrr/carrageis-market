package pt.henrique.market.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import pt.henrique.market.Market;
import pt.henrique.market.util.ItemBuilder;
import pt.henrique.market.util.SoundUtil;
import pt.henrique.market.util.TextUtil;

/**
 * GUI-based percentage picker (1–100), modelled on {@link NumberInputGui} but
 * using whole-number percentages instead of currency values.
 *
 * <p>Used by the multi-select sell flow to choose a custom percentage. Like
 * {@link NumberInputGui}, the inventory is closed before the callback fires, so
 * the caller is responsible for reopening the previous screen on both confirm
 * and cancel.</p>
 *
 * Layout (54-slot chest):
 * <pre>
 * │  .   .   .   .  [DISPLAY] .   .   .  │ Row 1: Value
 * │ -25 -10  -5  -1  .  +1  +5 +10 +25  │ Row 3: Adjust
 * │  .  MIN  .   .   .   .   .  MAX  .   │ Row 4: Presets
 * │ BACK .   .   .  CONFIRM .   .   .    │ Row 5: Actions
 * </pre>
 */
public class PercentInputGui implements MarketGui {

    private final Market plugin;
    private final GuiManager guiManager;
    private final PercentInputCallback callback;
    private final String title;

    private Inventory inventory;
    private Player player;
    private int currentValue;

    private static final int MIN_VALUE = 1;
    private static final int MAX_VALUE = 100;

    // ==================== LAYOUT CONSTANTS ====================
    private static final int DISPLAY_SLOT = 13;

    // Row 3: Decrease buttons (LEFT) - slots 27-30
    private static final int SUB_25_SLOT = 27;
    private static final int SUB_10_SLOT = 28;
    private static final int SUB_5_SLOT = 29;
    private static final int SUB_1_SLOT = 30;

    // Row 3: Increase buttons (RIGHT) - slots 32-35
    private static final int ADD_1_SLOT = 32;
    private static final int ADD_5_SLOT = 33;
    private static final int ADD_10_SLOT = 34;
    private static final int ADD_25_SLOT = 35;

    // Row 4: Preset buttons
    private static final int SET_MIN_SLOT = 37;
    private static final int SET_MAX_SLOT = 43;

    // Row 5: Action buttons
    private static final int BACK_SLOT = 45;
    private static final int CONFIRM_SLOT = 49;
    // ===========================================================

    @FunctionalInterface
    public interface PercentInputCallback {
        /** @param percent the chosen percentage (1–100), or -1 if cancelled. */
        void onComplete(int percent);
    }

    public PercentInputGui(Market plugin, GuiManager guiManager,
                           PercentInputCallback callback, int currentValue, String title) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.callback = callback;
        this.currentValue = clamp(currentValue);
        this.title = title;
    }

    public void open(Player player) {
        this.player = player;
        inventory = Bukkit.createInventory(this, 54, TextUtil.colorizeToString(title));
        buildGui();
        player.openInventory(inventory);
        guiManager.registerGui(player.getUniqueId(), this);
    }

    private void buildGui() {
        var msg = plugin.getMessageManager();

        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) inventory.setItem(i, filler);

        updateDisplay();

        // === DECREASE BUTTONS (LEFT) ===
        inventory.setItem(SUB_25_SLOT, adjustBtn(Material.RED_STAINED_GLASS_PANE, "&c-25", -25, msg));
        inventory.setItem(SUB_10_SLOT, adjustBtn(Material.RED_STAINED_GLASS_PANE, "&c-10", -10, msg));
        inventory.setItem(SUB_5_SLOT, adjustBtn(Material.RED_STAINED_GLASS_PANE, "&c-5", -5, msg));
        inventory.setItem(SUB_1_SLOT, adjustBtn(Material.RED_STAINED_GLASS_PANE, "&c-1", -1, msg));

        // === INCREASE BUTTONS (RIGHT) ===
        inventory.setItem(ADD_1_SLOT, adjustBtn(Material.LIME_STAINED_GLASS_PANE, "&a+1", 1, msg));
        inventory.setItem(ADD_5_SLOT, adjustBtn(Material.LIME_STAINED_GLASS_PANE, "&a+5", 5, msg));
        inventory.setItem(ADD_10_SLOT, adjustBtn(Material.LIME_STAINED_GLASS_PANE, "&a+10", 10, msg));
        inventory.setItem(ADD_25_SLOT, adjustBtn(Material.LIME_STAINED_GLASS_PANE, "&a+25", 25, msg));

        // === PRESET BUTTONS ===
        inventory.setItem(SET_MIN_SLOT, new ItemBuilder(Material.ORANGE_STAINED_GLASS_PANE)
                .name(msg.getRaw("percent-input.set-minimum"))
                .lore(msg.getRaw("percent-input.set-to").replace("{value}", String.valueOf(MIN_VALUE)))
                .build());
        inventory.setItem(SET_MAX_SLOT, new ItemBuilder(Material.ORANGE_STAINED_GLASS_PANE)
                .name(msg.getRaw("percent-input.set-maximum"))
                .lore(msg.getRaw("percent-input.set-to").replace("{value}", String.valueOf(MAX_VALUE)))
                .build());

        // === ACTION BUTTONS ===
        inventory.setItem(BACK_SLOT, new ItemBuilder(Material.RED_WOOL)
                .name(msg.getButton("cancel"))
                .lore(msg.getRaw("percent-input.cancel-lore"))
                .build());
        // CONFIRM is rendered in updateDisplay()
    }

    private ItemStack adjustBtn(Material mat, String name, int delta, pt.henrique.market.config.MessageManager msg) {
        String amt = (delta > 0 ? "+" : "") + delta;
        return new ItemBuilder(mat)
                .name(name)
                .lore(msg.getRaw("percent-input.click-adjust").replace("{amount}", amt))
                .build();
    }

    private void updateDisplay() {
        var msg = plugin.getMessageManager();
        inventory.setItem(DISPLAY_SLOT, new ItemBuilder(Material.PAPER)
                .name(msg.getRaw("percent-input.display-title").replace("{value}", String.valueOf(currentValue)))
                .lore(
                        "",
                        msg.getRaw("percent-input.minimum").replace("{value}", String.valueOf(MIN_VALUE)),
                        msg.getRaw("percent-input.maximum").replace("{value}", String.valueOf(MAX_VALUE)),
                        "",
                        msg.getRaw("percent-input.use-buttons")
                )
                .amount(Math.max(1, Math.min(64, currentValue)))
                .glow()
                .build());

        inventory.setItem(CONFIRM_SLOT, new ItemBuilder(Material.LIME_WOOL)
                .name(msg.getButton("confirm"))
                .lore(msg.getRaw("percent-input.confirm-lore").replace("{value}", String.valueOf(currentValue)))
                .build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.isShiftClick()) return;
        if (!(event.getWhoClicked() instanceof Player p)) return;
        int slot = event.getRawSlot();

        switch (slot) {
            case SUB_25_SLOT -> adjust(-25);
            case SUB_10_SLOT -> adjust(-10);
            case SUB_5_SLOT -> adjust(-5);
            case SUB_1_SLOT -> adjust(-1);
            case ADD_1_SLOT -> adjust(1);
            case ADD_5_SLOT -> adjust(5);
            case ADD_10_SLOT -> adjust(10);
            case ADD_25_SLOT -> adjust(25);
            case SET_MIN_SLOT -> {
                currentValue = MIN_VALUE;
                updateDisplay();
                SoundUtil.playSound(p, plugin.getConfigManager().getClickSound());
            }
            case SET_MAX_SLOT -> {
                currentValue = MAX_VALUE;
                updateDisplay();
                SoundUtil.playSound(p, plugin.getConfigManager().getClickSound());
            }
            case CONFIRM_SLOT -> {
                SoundUtil.playSound(p, plugin.getConfigManager().getSuccessSound());
                p.closeInventory();
                callback.onComplete(currentValue);
            }
            case BACK_SLOT -> {
                p.closeInventory();
                callback.onComplete(-1); // Signal cancellation
            }
        }
    }

    private void adjust(int delta) {
        currentValue = clamp(currentValue + delta);
        updateDisplay();
        SoundUtil.playSound(player, plugin.getConfigManager().getClickSound(), 0.3f, 1.2f);
    }

    private static int clamp(int value) {
        return Math.max(MIN_VALUE, Math.min(MAX_VALUE, value));
    }

    @Override
    public GuiType getType() {
        return GuiType.PERCENT_INPUT;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
