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
 * GUI-based numeric input to replace chat input.
 * Players can increment/decrement values using buttons.
 *
 * Layout (54-slot chest):
 * ┌─────────────────────────────────────────────────────┐
 * │  .   .   .   .   .   .   .   .   .  │ Row 0: Empty  │
 * │  .   .   .   .  [DISPLAY] .   .   . │ Row 1: Value  │
 * │  .   .   .   .   .   .   .   .   .  │ Row 2: Empty  │
 * │ -1K -100 -10  -1  .  +1  +10 +100 +1K│ Row 3: Adjust │
 * │  .  MIN  .   .   .   .   .  MAX  .  │ Row 4: Presets│
 * │ BACK .   .   .  CONFIRM .   .   .   │ Row 5: Actions│
 * └─────────────────────────────────────────────────────┘
 */
public class NumberInputGui implements MarketGui {

    private final Market plugin;
    private final GuiManager guiManager;
    private final NumberInputCallback callback;
    private final double minValue;
    private final double maxValue;
    private final String title;

    private Inventory inventory;
    private Player player;
    private double currentValue;

    // ==================== LAYOUT CONSTANTS ====================
    // Row 1: Current value display (center)
    private static final int DISPLAY_SLOT = 13;

    // Row 3: Decrease buttons (LEFT side) - slots 27-30
    private static final int SUB_1000_SLOT = 27;  // -1,000
    private static final int SUB_100_SLOT = 28;   // -100
    private static final int SUB_10_SLOT = 29;    // -10
    private static final int SUB_1_SLOT = 30;     // -1

    // Row 3: Increase buttons (RIGHT side) - slots 32-35
    private static final int ADD_1_SLOT = 32;     // +1
    private static final int ADD_10_SLOT = 33;    // +10
    private static final int ADD_100_SLOT = 34;   // +100
    private static final int ADD_1000_SLOT = 35;  // +1,000

    // Row 4: Preset buttons
    private static final int SET_MIN_SLOT = 37;   // Set to minimum
    private static final int SET_MAX_SLOT = 43;   // Set to maximum

    // Row 5: Action buttons
    private static final int BACK_SLOT = 45;      // Cancel/Back (bottom-left)
    private static final int CONFIRM_SLOT = 49;   // Confirm (bottom-center)
    // ===========================================================

    @FunctionalInterface
    public interface NumberInputCallback {
        void onComplete(double value);
    }

    public NumberInputGui(Market plugin, GuiManager guiManager,
                          NumberInputCallback callback, double currentValue,
                          double minValue, double maxValue, String title) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.callback = callback;
        this.currentValue = currentValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
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
        var msgManager = plugin.getMessageManager();

        // Fill all slots with glass panes
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, filler);
        }

        // Current value display (center, row 1)
        updateDisplay();

        // === DECREASE BUTTONS (LEFT SIDE) ===
        inventory.setItem(SUB_1000_SLOT, new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                .name("&c-1,000")
                .lore(msgManager.getRaw("number-input.click-adjust").replace("{amount}", "-1,000"),
                      msgManager.getRaw("number-input.shift-click").replace("{amount}", "-10,000"))
                .build());

        inventory.setItem(SUB_100_SLOT, new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                .name("&c-100")
                .lore(msgManager.getRaw("number-input.click-adjust").replace("{amount}", "-100"),
                      msgManager.getRaw("number-input.shift-click").replace("{amount}", "-1,000"))
                .build());

        inventory.setItem(SUB_10_SLOT, new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                .name("&c-10")
                .lore(msgManager.getRaw("number-input.click-adjust").replace("{amount}", "-10"),
                      msgManager.getRaw("number-input.shift-click").replace("{amount}", "-100"))
                .build());

        inventory.setItem(SUB_1_SLOT, new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                .name("&c-1")
                .lore(msgManager.getRaw("number-input.click-adjust").replace("{amount}", "-1"),
                      msgManager.getRaw("number-input.shift-click").replace("{amount}", "-10"))
                .build());

        // === INCREASE BUTTONS (RIGHT SIDE) ===
        inventory.setItem(ADD_1_SLOT, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                .name("&a+1")
                .lore(msgManager.getRaw("number-input.click-adjust").replace("{amount}", "+1"),
                      msgManager.getRaw("number-input.shift-click").replace("{amount}", "+10"))
                .build());

        inventory.setItem(ADD_10_SLOT, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                .name("&a+10")
                .lore(msgManager.getRaw("number-input.click-adjust").replace("{amount}", "+10"),
                      msgManager.getRaw("number-input.shift-click").replace("{amount}", "+100"))
                .build());

        inventory.setItem(ADD_100_SLOT, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                .name("&a+100")
                .lore(msgManager.getRaw("number-input.click-adjust").replace("{amount}", "+100"),
                      msgManager.getRaw("number-input.shift-click").replace("{amount}", "+1,000"))
                .build());

        inventory.setItem(ADD_1000_SLOT, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                .name("&a+1,000")
                .lore(msgManager.getRaw("number-input.click-adjust").replace("{amount}", "+1,000"),
                      msgManager.getRaw("number-input.shift-click").replace("{amount}", "+10,000"))
                .build());

        // === PRESET BUTTONS ===
        inventory.setItem(SET_MIN_SLOT, new ItemBuilder(Material.ORANGE_STAINED_GLASS_PANE)
                .name(msgManager.getRaw("number-input.set-minimum"))
                .lore(msgManager.getRaw("number-input.set-to").replace("{value}", msgManager.formatCurrency(minValue)))
                .build());

        inventory.setItem(SET_MAX_SLOT, new ItemBuilder(Material.ORANGE_STAINED_GLASS_PANE)
                .name(msgManager.getRaw("number-input.set-maximum"))
                .lore(msgManager.getRaw("number-input.set-to").replace("{value}", msgManager.formatCurrency(maxValue)))
                .build());

        // === ACTION BUTTONS ===
        inventory.setItem(BACK_SLOT, new ItemBuilder(Material.RED_WOOL)
                .name(msgManager.getButton("cancel"))
                .lore(msgManager.getRaw("number-input.cancel-lore"))
                .build());

        inventory.setItem(CONFIRM_SLOT, new ItemBuilder(Material.LIME_WOOL)
                .name(msgManager.getButton("confirm"))
                .lore(msgManager.getRaw("number-input.confirm-lore").replace("{value}", msgManager.formatCurrency(currentValue)))
                .build());
    }

    private void updateDisplay() {
        var msgManager = plugin.getMessageManager();
        inventory.setItem(DISPLAY_SLOT, new ItemBuilder(Material.GOLD_INGOT)
                .name(msgManager.getRaw("number-input.display-title").replace("{value}", msgManager.formatCurrency(currentValue)))
                .lore(
                        "",
                        msgManager.getRaw("number-input.minimum").replace("{value}", msgManager.formatCurrency(minValue)),
                        msgManager.getRaw("number-input.maximum").replace("{value}", msgManager.formatCurrency(maxValue)),
                        "",
                        msgManager.getRaw("number-input.use-buttons")
                )
                .glow()
                .build());

        // Also update confirm button lore
        inventory.setItem(CONFIRM_SLOT, new ItemBuilder(Material.LIME_WOOL)
                .name(plugin.getMessageManager().getButton("confirm"))
                .lore(msgManager.getRaw("number-input.confirm-lore").replace("{value}", msgManager.formatCurrency(currentValue)))
                .build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        boolean shift = event.isShiftClick();

        // Multiplier for shift-click (10x)
        double multiplier = shift ? 10 : 1;

        switch (slot) {
            // Decrease buttons
            case SUB_1000_SLOT -> adjustValue(-1000 * multiplier);
            case SUB_100_SLOT -> adjustValue(-100 * multiplier);
            case SUB_10_SLOT -> adjustValue(-10 * multiplier);
            case SUB_1_SLOT -> adjustValue(-1 * multiplier);

            // Increase buttons
            case ADD_1_SLOT -> adjustValue(1 * multiplier);
            case ADD_10_SLOT -> adjustValue(10 * multiplier);
            case ADD_100_SLOT -> adjustValue(100 * multiplier);
            case ADD_1000_SLOT -> adjustValue(1000 * multiplier);

            // Preset buttons
            case SET_MIN_SLOT -> {
                currentValue = minValue;
                updateDisplay();
                SoundUtil.playSound(player, plugin.getConfigManager().getClickSound());
            }
            case SET_MAX_SLOT -> {
                currentValue = maxValue;
                updateDisplay();
                SoundUtil.playSound(player, plugin.getConfigManager().getClickSound());
            }

            // Action buttons
            case CONFIRM_SLOT -> {
                SoundUtil.playSound(player, plugin.getConfigManager().getSuccessSound());
                player.closeInventory();
                callback.onComplete(currentValue);
            }
            case BACK_SLOT -> {
                player.closeInventory();
                callback.onComplete(-1); // Signal cancellation
            }
        }
    }

    private void adjustValue(double delta) {
        double newValue = currentValue + delta;

        // Clamp to min/max
        newValue = Math.max(minValue, Math.min(maxValue, newValue));

        // Round to 2 decimal places
        currentValue = Math.round(newValue * 100.0) / 100.0;

        updateDisplay();
        SoundUtil.playSound(player, plugin.getConfigManager().getClickSound(), 0.3f, 1.2f);
    }

    @Override
    public GuiType getType() {
        return GuiType.NUMBER_INPUT;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
