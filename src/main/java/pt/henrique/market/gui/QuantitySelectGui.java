package pt.henrique.market.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import pt.henrique.market.Market;
import pt.henrique.market.util.ItemBuilder;
import pt.henrique.market.util.InventoryUtil;
import pt.henrique.market.util.SoundUtil;
import pt.henrique.market.util.TextUtil;

/**
 * GUI for selecting quantity of an item to list or auction.
 * Only shown for stackable items; unstackable items skip this step.
 *
 * Layout (54-slot chest):
 * ┌─────────────────────────────────────────────────────┐
 * │  .   .   .   .  INFO  .   .   .   .  │ Row 0        │
 * │  .   .   .   .  ITEM  .   .   .   .  │ Row 1: Item  │
 * │  .   .   .   . DISPLAY .   .   .   . │ Row 2: Qty   │
 * │ -32 -16  -8  -1  .  +1   +8  +16 +32 │ Row 3: Adjust│
 * │  .  MIN  .   .   .   .   .  MAX  .   │ Row 4: Preset│
 * │ BACK .   .   .  CONFIRM .   .   .   .│ Row 5: Action│
 * └─────────────────────────────────────────────────────┘
 */
public class QuantitySelectGui implements MarketGui {

    private final Market plugin;
    private final GuiManager guiManager;
    private final QuantityCallback callback;
    private final ItemStack selectedItem;
    private final int maxQuantity;

    private Inventory inventory;
    private Player player;
    private int currentQuantity;

    // ==================== LAYOUT CONSTANTS ====================
    private static final int INFO_SLOT = 4;           // Top center info
    private static final int ITEM_DISPLAY_SLOT = 13;  // Item preview
    private static final int QUANTITY_DISPLAY_SLOT = 22; // Current quantity display

    // Row 3: Decrease buttons (LEFT side) - slots 27-30
    private static final int SUB_32_SLOT = 27;   // -32
    private static final int SUB_16_SLOT = 28;   // -16
    private static final int SUB_8_SLOT = 29;    // -8
    private static final int SUB_1_SLOT = 30;    // -1

    // Row 3: Increase buttons (RIGHT side) - slots 32-35
    private static final int ADD_1_SLOT = 32;    // +1
    private static final int ADD_8_SLOT = 33;    // +8
    private static final int ADD_16_SLOT = 34;   // +16
    private static final int ADD_32_SLOT = 35;   // +32

    // Row 4: Preset buttons
    private static final int SET_MIN_SLOT = 37;  // Set to 1
    private static final int SET_MAX_SLOT = 43;  // Set to max

    // Row 5: Action buttons
    private static final int BACK_SLOT = 45;     // Cancel/Back
    private static final int CONFIRM_SLOT = 49;  // Confirm
    // ===========================================================

    @FunctionalInterface
    public interface QuantityCallback {
        void onComplete(int quantity);
    }

    public QuantitySelectGui(Market plugin, GuiManager guiManager,
                             QuantityCallback callback, ItemStack selectedItem,
                             int maxQuantity) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.callback = callback;
        this.selectedItem = selectedItem;
        this.maxQuantity = maxQuantity;
        this.currentQuantity = Math.min(selectedItem.getAmount(), maxQuantity);
    }

    public void open(Player player) {
        this.player = player;

        var msgManager = plugin.getMessageManager();
        String title = TextUtil.colorizeToString(msgManager.getRaw("gui-titles.quantity-select"));
        inventory = Bukkit.createInventory(this, 54, title);

        buildGui();
        player.openInventory(inventory);
        guiManager.registerGui(player.getUniqueId(), this);
    }

    private void buildGui() {
        var msgManager = plugin.getMessageManager();

        // Fill with glass
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, filler);
        }

        // Info panel
        inventory.setItem(INFO_SLOT, new ItemBuilder(Material.OAK_SIGN)
                .name(msgManager.getRaw("quantity-select.title"))
                .lore(
                        msgManager.getRaw("quantity-select.info-line-1"),
                        msgManager.getRaw("quantity-select.info-line-2"),
                        "",
                        msgManager.getRaw("quantity-select.available").replace("{amount}", String.valueOf(maxQuantity))
                )
                .build());

        // Item preview
        ItemStack displayItem = selectedItem.clone();
        displayItem.setAmount(currentQuantity);
        inventory.setItem(ITEM_DISPLAY_SLOT, new ItemBuilder(displayItem)
                .addLore(java.util.List.of(
                        "",
                        msgManager.getRaw("quantity-select.selected").replace("{amount}", String.valueOf(currentQuantity))
                ))
                .build());

        // Quantity display
        updateQuantityDisplay();

        // === DECREASE BUTTONS (LEFT SIDE) ===
        inventory.setItem(SUB_32_SLOT, new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                .name("&c-32")
                .lore(msgManager.getRaw("quantity-select.click-adjust").replace("{amount}", "-32"))
                .amount(32)
                .build());

        inventory.setItem(SUB_16_SLOT, new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                .name("&c-16")
                .lore(msgManager.getRaw("quantity-select.click-adjust").replace("{amount}", "-16"))
                .amount(16)
                .build());

        inventory.setItem(SUB_8_SLOT, new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                .name("&c-8")
                .lore(msgManager.getRaw("quantity-select.click-adjust").replace("{amount}", "-8"))
                .amount(8)
                .build());

        inventory.setItem(SUB_1_SLOT, new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                .name("&c-1")
                .lore(msgManager.getRaw("quantity-select.click-adjust").replace("{amount}", "-1"))
                .build());

        // === INCREASE BUTTONS (RIGHT SIDE) ===
        inventory.setItem(ADD_1_SLOT, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                .name("&a+1")
                .lore(msgManager.getRaw("quantity-select.click-adjust").replace("{amount}", "+1"))
                .build());

        inventory.setItem(ADD_8_SLOT, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                .name("&a+8")
                .lore(msgManager.getRaw("quantity-select.click-adjust").replace("{amount}", "+8"))
                .amount(8)
                .build());

        inventory.setItem(ADD_16_SLOT, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                .name("&a+16")
                .lore(msgManager.getRaw("quantity-select.click-adjust").replace("{amount}", "+16"))
                .amount(16)
                .build());

        inventory.setItem(ADD_32_SLOT, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                .name("&a+32")
                .lore(msgManager.getRaw("quantity-select.click-adjust").replace("{amount}", "+32"))
                .amount(32)
                .build());

        // === PRESET BUTTONS ===
        inventory.setItem(SET_MIN_SLOT, new ItemBuilder(Material.ORANGE_STAINED_GLASS_PANE)
                .name(msgManager.getRaw("quantity-select.set-minimum"))
                .lore(msgManager.getRaw("quantity-select.set-to").replace("{amount}", "1"))
                .build());

        inventory.setItem(SET_MAX_SLOT, new ItemBuilder(Material.ORANGE_STAINED_GLASS_PANE)
                .name(msgManager.getRaw("quantity-select.set-maximum"))
                .lore(msgManager.getRaw("quantity-select.set-to").replace("{amount}", String.valueOf(maxQuantity)))
                .build());

        // === ACTION BUTTONS ===
        inventory.setItem(BACK_SLOT, new ItemBuilder(Material.RED_WOOL)
                .name(msgManager.getButton("back"))
                .lore(msgManager.getRaw("quantity-select.back-lore"))
                .build());

        updateConfirmButton();
    }

    private void updateQuantityDisplay() {
        var msgManager = plugin.getMessageManager();

        inventory.setItem(QUANTITY_DISPLAY_SLOT, new ItemBuilder(Material.PAPER)
                .name("&6&l" + msgManager.getRaw("quantity-select.quantity-label") + ": " + currentQuantity)
                .lore(
                        "",
                        msgManager.getRaw("quantity-select.minimum").replace("{amount}", "1"),
                        msgManager.getRaw("quantity-select.maximum").replace("{amount}", String.valueOf(maxQuantity)),
                        "",
                        msgManager.getRaw("quantity-select.use-buttons")
                )
                .amount(Math.min(currentQuantity, 64))
                .glow()
                .build());

        // Update item display amount
        ItemStack displayItem = selectedItem.clone();
        displayItem.setAmount(Math.min(currentQuantity, 64));
        inventory.setItem(ITEM_DISPLAY_SLOT, new ItemBuilder(displayItem)
                .addLore(java.util.List.of(
                        "",
                        msgManager.getRaw("quantity-select.selected").replace("{amount}", String.valueOf(currentQuantity))
                ))
                .build());
    }

    private void updateConfirmButton() {
        var msgManager = plugin.getMessageManager();
        inventory.setItem(CONFIRM_SLOT, new ItemBuilder(Material.LIME_WOOL)
                .name(msgManager.getButton("confirm"))
                .lore(msgManager.getRaw("quantity-select.confirm-lore").replace("{amount}", String.valueOf(currentQuantity)))
                .build());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();

        switch (slot) {
            // Decrease buttons
            case SUB_32_SLOT -> adjustQuantity(-32);
            case SUB_16_SLOT -> adjustQuantity(-16);
            case SUB_8_SLOT -> adjustQuantity(-8);
            case SUB_1_SLOT -> adjustQuantity(-1);

            // Increase buttons
            case ADD_1_SLOT -> adjustQuantity(1);
            case ADD_8_SLOT -> adjustQuantity(8);
            case ADD_16_SLOT -> adjustQuantity(16);
            case ADD_32_SLOT -> adjustQuantity(32);

            // Preset buttons
            case SET_MIN_SLOT -> {
                currentQuantity = 1;
                updateQuantityDisplay();
                updateConfirmButton();
                SoundUtil.playSound(player, plugin.getConfigManager().getClickSound());
            }
            case SET_MAX_SLOT -> {
                currentQuantity = maxQuantity;
                updateQuantityDisplay();
                updateConfirmButton();
                SoundUtil.playSound(player, plugin.getConfigManager().getClickSound());
            }

            // Action buttons
            case CONFIRM_SLOT -> {
                // Validate quantity is still valid
                int currentAvailable = countAvailableItems(player);
                if (currentQuantity > currentAvailable) {
                    player.sendMessage(plugin.getMessageManager().getPrefixedP(player, "messages.quantity-changed"));
                    currentQuantity = Math.min(currentQuantity, currentAvailable);
                    if (currentQuantity < 1) {
                        player.sendMessage(plugin.getMessageManager().getPrefixedP(player, "messages.item-no-longer-available"));
                        SoundUtil.playSound(player, plugin.getConfigManager().getErrorSound());
                        player.closeInventory();
                        return;
                    }
                    updateQuantityDisplay();
                    updateConfirmButton();
                    SoundUtil.playSound(player, plugin.getConfigManager().getErrorSound());
                    return;
                }

                SoundUtil.playSound(player, plugin.getConfigManager().getSuccessSound());
                player.closeInventory();
                callback.onComplete(currentQuantity);
            }
            case BACK_SLOT -> {
                player.closeInventory();
                callback.onComplete(-1); // Signal cancellation
            }
        }
    }

    private void adjustQuantity(int delta) {
        int newQuantity;
        // If at minimum (1) and incrementing by more than 1, set directly to the
        // increment value so +8 gives 8 instead of 9, +16 gives 16, etc.
        if (delta > 1 && currentQuantity == 1) {
            newQuantity = delta;
        } else {
            newQuantity = currentQuantity + delta;
        }

        // Clamp to 1..maxQuantity
        newQuantity = Math.max(1, Math.min(maxQuantity, newQuantity));

        currentQuantity = newQuantity;
        updateQuantityDisplay();
        updateConfirmButton();
        SoundUtil.playSound(player, plugin.getConfigManager().getClickSound(), 0.3f, 1.2f);
    }

    /**
     * Counts how many of the selected item the player currently has.
     * Uses strict item comparison including all metadata.
     */
    private int countAvailableItems(Player player) {
        return InventoryUtil.countSimilarItems(player.getInventory(), selectedItem);
    }

    @Override
    public GuiType getType() {
        return GuiType.QUANTITY_SELECT;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

