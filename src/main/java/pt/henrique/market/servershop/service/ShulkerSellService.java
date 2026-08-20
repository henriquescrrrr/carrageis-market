package pt.henrique.market.servershop.service;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pt.henrique.market.Market;
import pt.henrique.market.config.MessageManager;
import pt.henrique.market.servershop.SellVariantScanner;
import pt.henrique.market.servershop.ShulkerUtil;
import pt.henrique.market.servershop.model.SellQuote;
import pt.henrique.market.servershop.model.SellVariant;
import pt.henrique.market.util.SoundUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles selling a shulker box (an inventory ItemStack) with three options:
 * sell the box only (returning contents), sell box + sellable contents, or sell
 * the sellable contents only (keeping the box).
 * <p>
 * The box is removed <b>by slot</b>, so a different shulker of the same colour is
 * never taken instead of the one the player picked. Each operation
 * mutates the inventory first, then deposits synchronously, and on deposit failure
 * rolls back via a storage snapshot, the same anti-dupe order used by {@code sellVariant}.
 * Items are only dropped on the ground after a successful deposit (and only on the
 * green-confirmed overflow path), so a cancel/failure never loses or duplicates items.
 */
public class ShulkerSellService {

    private final Market plugin;

    public ShulkerSellService(Market plugin) {
        this.plugin = plugin;
    }

    public enum ShulkerSellOption { BOX_ONLY, BOX_AND_CONTENTS, CONTENTS_ONLY }

    /** Classification of a shulker's contents. */
    public record ContentsScan(
            List<SellVariant> sellable,        // grouped per distinct item (for value + display)
            List<ItemStack> nonSellable,       // exact clones to return (no-price / dream / packed nested shulker)
            double grossSellable,              // pre-tax value of sellable contents
            int sellableUnits
    ) {}

    /** Per-option quote for GUI display. */
    public record OptionQuote(boolean available, double boxGross, double contentsGross,
                              double totalNet, int returnedItems) {}

    /* ════════════════════════════════════════════════════════
     *  Public API
     * ════════════════════════════════════════════════════════ */

    /** Net value (after tax) of a gross amount. */
    public double netOf(double gross) {
        return gross - plugin.getShopPricingService().calculateTax(gross);
    }

    /**
     * Classifies the shulker's contents into sellable/non-sellable buckets.
     * <p>
     * Sellable contents are grouped per distinct item, so an enchanted tool inside
     * the box is valued on its own merits rather than at the price of the plain one
     * next to it.
     */
    public ContentsScan scan(ItemStack shulker) {
        ItemStack[] contents = ShulkerUtil.getContents(shulker);
        List<ItemStack> nonSellable = new ArrayList<>();
        for (ItemStack c : contents) {
            if (c == null || c.getType().isAir()) continue;
            if (!isSellableContent(c)) nonSellable.add(c.clone());
        }

        // Packed nested shulkers are never unpacked, so they are filtered out above
        // and the scan below only ever sees the loose contents.
        ItemStack[] sellableOnly = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            ItemStack c = contents[i];
            if (c != null && !c.getType().isAir() && isSellableContent(c)) sellableOnly[i] = c;
        }
        List<SellVariant> sellable = plugin.getSellVariantScanner()
                .scan(sellableOnly, SellVariantScanner.Options.showAll(false)).variants();

        double gross = 0;
        int units = 0;
        for (SellVariant variant : sellable) {
            gross += variant.totalValue();
            units += variant.amount();
        }
        return new ContentsScan(sellable, nonSellable, Math.round(gross * 100.0) / 100.0, units);
    }

    /** Computes the quote for one option (for the chooser GUI lore). */
    public OptionQuote quote(ItemStack shulker, ShulkerSellOption option) {
        ContentsScan scan = scan(shulker);
        double boxGross = boxGross(shulker);
        return switch (option) {
            case BOX_ONLY -> new OptionQuote(boxGross > 0, boxGross, 0,
                    netOf(boxGross), ShulkerUtil.countTotalItems(shulker));
            case BOX_AND_CONTENTS -> {
                double gross = boxGross + scan.grossSellable();
                yield new OptionQuote(boxGross > 0, boxGross, scan.grossSellable(),
                        netOf(gross), totalItems(scan.nonSellable()));
            }
            case CONTENTS_ONLY -> new OptionQuote(scan.grossSellable() > 0, 0, scan.grossSellable(),
                    netOf(scan.grossSellable()), 0);
        };
    }

    /**
     * Entry point invoked by the chooser GUI. Validates, computes the overflow
     * for inventory-returning options, and either executes inline or opens a
     * red/green confirmation when items would drop.
     */
    public void execute(Player player, int slot, ShulkerSellOption option) {
        MessageManager msg = plugin.getMessageManager();
        ItemStack live = player.getInventory().getItem(slot);
        if (!ShulkerUtil.isShulkerBox(live) || !ShulkerUtil.hasContents(live)) {
            fail(player, "servershop.messages.shulker-gone");
            return;
        }
        ItemStack original = live.clone();
        OptionQuote quote = quote(live, option);
        if (!quote.available()) {
            SoundUtil.playErrorSound(player);
            player.sendMessage(msg.getPrefixedP(player, "servershop.messages.shulker-not-sellable"));
            plugin.getShopGuiManager().openShulkerSellChooser(player, slot);
            return;
        }

        if (option == ShulkerSellOption.CONTENTS_ONLY) {
            doContentsOnly(player, slot, original);
            return;
        }

        // BOX_ONLY / BOX_AND_CONTENTS return items to the player → check for overflow.
        List<ItemStack> returnables = (option == ShulkerSellOption.BOX_ONLY)
                ? cloneNonEmpty(ShulkerUtil.getContents(live))
                : scan(live).nonSellable();
        ItemStack[] snapshot = cloneArray(player.getInventory().getStorageContents());
        int overflow = probeOverflow(player, slot, returnables, snapshot);

        if (overflow <= 0) {
            doBoxSale(player, slot, option, original);
            return;
        }

        boolean confirmDrop = plugin.getConfig().getBoolean("sell-inventory.shulker-sell.confirm-overflow-drop", true);
        if (!confirmDrop) {
            String action = plugin.getConfig().getString("server-shop.full-inventory-action", "drop");
            if (action.equalsIgnoreCase("cancel")) {
                SoundUtil.playErrorSound(player);
                player.sendMessage(msg.getPrefixedP(player, "servershop.messages.shulker-sale-cancelled"));
                plugin.getShopGuiManager().openShulkerSellChooser(player, slot);
            } else {
                doBoxSale(player, slot, option, original);
            }
            return;
        }

        // Red/green confirmation before dropping overflow on the ground.
        final int slotF = slot;
        final ShulkerSellOption optionF = option;
        final ItemStack originalF = original;
        String lang = msg.getPlayerLanguage(player);
        String title = msg.getRaw(lang, "servershop.titles.shulker-sell-chooser");
        String warn = msg.getRaw(lang, "servershop.messages.shulker-drop-warning")
                .replace("{count}", String.valueOf(overflow));
        plugin.getGuiManager().openConfirmation(player, confirmed -> {
            if (confirmed) {
                doBoxSale(player, slotF, optionF, originalF);
            } else {
                SoundUtil.playClickSound(player);
                player.sendMessage(msg.getPrefixedP(player, "servershop.messages.shulker-sale-cancelled"));
                plugin.getShopGuiManager().openShulkerSellChooser(player, slotF);
            }
        }, title, warn, "");
    }

    /* ════════════════════════════════════════════════════════
     *  Execution
     * ════════════════════════════════════════════════════════ */

    /** Executes BOX_ONLY / BOX_AND_CONTENTS: box removed by slot, items returned, money deposited. */
    private void doBoxSale(Player player, int slot, ShulkerSellOption option, ItemStack original) {
        MessageManager msg = plugin.getMessageManager();
        ItemStack live = player.getInventory().getItem(slot);
        if (!ShulkerUtil.isShulkerBox(live) || !live.isSimilar(original)) {
            fail(player, "servershop.messages.shulker-gone");
            return;
        }

        ContentsScan scan = scan(live);
        double boxGross = boxGross(live);
        double gross = boxGross + (option == ShulkerSellOption.BOX_AND_CONTENTS ? scan.grossSellable() : 0);
        if (gross <= 0) {
            fail(player, "servershop.messages.shulker-not-sellable");
            return;
        }
        double net = netOf(gross);
        String boxId = boxId(live);

        // Daily sell cap — checked before any inventory mutation
        if (exceedsDailyCap(player, net)) {
            fail(player, "servershop.messages.daily-sell-cap");
            return;
        }

        List<ItemStack> returnables = (option == ShulkerSellOption.BOX_ONLY)
                ? cloneNonEmpty(ShulkerUtil.getContents(live))
                : scan.nonSellable();
        ItemStack[] snapshot = cloneArray(player.getInventory().getStorageContents());

        // Mutate: remove the box, give back the returnables (overflow kept aside, not yet dropped).
        player.getInventory().setItem(slot, null);
        Map<Integer, ItemStack> overflow = player.getInventory()
                .addItem(toArray(cloneList(returnables)));

        boolean ok = net <= 0 || plugin.getEconomyManager().deposit(player.getUniqueId(), net);
        if (!ok) {
            player.getInventory().setStorageContents(snapshot); // full rollback
            fail(player, "servershop.messages.shulker-not-sellable");
            return;
        }

        if (net > 0) plugin.getShopTransactionService().recordDailySell(player, net);

        // Commit: drop overflow (only happens after a successful deposit + green confirm).
        for (ItemStack left : overflow.values()) {
            if (left != null && !left.getType().isAir()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        }

        // Logging
        if (boxId != null) {
            plugin.getShopTransactionService().logShopSale(player, boxId, 1, boxGross, boxGross,
                    plugin.getShopPricingService().calculateTax(boxGross));
        }
        if (option == ShulkerSellOption.BOX_AND_CONTENTS) logSellable(player, scan);

        SoundUtil.playPurchaseSound(player);
        String key = option == ShulkerSellOption.BOX_ONLY
                ? "servershop.messages.shulker-sold-box"
                : "servershop.messages.shulker-sold-box-contents";
        player.sendMessage(msg.getPrefixedP(player, key, "total", msg.formatCurrency(net)));
        plugin.getShopGuiManager().openSellInventory(player);
    }

    /** Executes CONTENTS_ONLY: sellable contents removed from the box, box kept in place. */
    private void doContentsOnly(Player player, int slot, ItemStack original) {
        MessageManager msg = plugin.getMessageManager();
        ItemStack live = player.getInventory().getItem(slot);
        if (!ShulkerUtil.isShulkerBox(live) || !live.isSimilar(original)) {
            fail(player, "servershop.messages.shulker-gone");
            return;
        }
        ContentsScan scan = scan(live);
        if (scan.grossSellable() <= 0) {
            SoundUtil.playErrorSound(player);
            player.sendMessage(msg.getPrefixedP(player, "servershop.messages.shulker-not-sellable"));
            plugin.getShopGuiManager().openShulkerSellChooser(player, slot);
            return;
        }
        double net = netOf(scan.grossSellable());

        // Daily sell cap — checked before any inventory mutation
        if (exceedsDailyCap(player, net)) {
            fail(player, "servershop.messages.daily-sell-cap");
            return;
        }

        // Rebuild the box's contents keeping only the non-sellable items.
        ItemStack[] contents = ShulkerUtil.getContents(live);
        ItemStack[] kept = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            ItemStack c = contents[i];
            if (c == null || c.getType().isAir()) continue;
            kept[i] = isSellableContent(c) ? null : c;
        }
        ItemStack newBox = ShulkerUtil.withContents(live, kept);
        player.getInventory().setItem(slot, newBox);

        boolean ok = net <= 0 || plugin.getEconomyManager().deposit(player.getUniqueId(), net);
        if (!ok) {
            player.getInventory().setItem(slot, original); // rollback the box
            fail(player, "servershop.messages.shulker-not-sellable");
            return;
        }

        if (net > 0) plugin.getShopTransactionService().recordDailySell(player, net);
        logSellable(player, scan);
        SoundUtil.playPurchaseSound(player);
        player.sendMessage(msg.getPrefixedP(player, "servershop.messages.shulker-sold-contents",
                "total", msg.formatCurrency(net)));
        plugin.getShopGuiManager().openSellInventory(player);
    }

    /**
     * Simulates returning {@code returnables} to the player (after vacating the
     * box slot) and counts how many items would not fit, then fully reverts.
     */
    private int probeOverflow(Player player, int slot, List<ItemStack> returnables, ItemStack[] snapshot) {
        player.getInventory().setItem(slot, null);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(toArray(cloneList(returnables)));
        int overflow = 0;
        for (ItemStack l : leftovers.values()) {
            if (l != null && !l.getType().isAir()) overflow += l.getAmount();
        }
        player.getInventory().setStorageContents(snapshot); // revert probe
        return overflow;
    }

    /* ════════════════════════════════════════════════════════
     *  Helpers
     * ════════════════════════════════════════════════════════ */

    /**
     * Whether one item inside the box can be sold.
     * <p>
     * Quoted the same way as anywhere else, so a box's contents are valued exactly
     * as they would be loose in the inventory — an enchanted book with several
     * enchantments included.
     */
    private boolean isSellableContent(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        if (plugin.getBuildersDreamHook() != null && plugin.getBuildersDreamHook().isDreamItem(stack)) return false;
        if (ShulkerUtil.isNestedShulkerWithContents(stack)) return false; // never destroy a packed nested shulker
        return unitValue(stack) > 0;
    }

    /** Sell value of one unit of {@code stack}, or 0 when it cannot be sold. */
    private double unitValue(ItemStack stack) {
        SellQuote quote = plugin.getShopPricingService().quoteSell(stack);
        if (quote == null) return 0;
        return plugin.getShopTransactionService()
                .computeAdjustedSellPrice(quote.baseSell(), stack, quote.composite());
    }

    private double boxGross(ItemStack shulker) {
        return unitValue(ShulkerUtil.emptiedClone(shulker));
    }

    private String boxId(ItemStack shulker) {
        SellQuote quote = plugin.getShopPricingService().quoteSell(ShulkerUtil.emptiedClone(shulker));
        return quote != null ? quote.itemId() : null;
    }

    private void logSellable(Player player, ContentsScan scan) {
        for (SellVariant variant : scan.sellable()) {
            plugin.getShopTransactionService().logShopSale(player, variant.itemId(), variant.amount(),
                    variant.unitPrice(), variant.totalValue(),
                    plugin.getShopPricingService().calculateTax(variant.totalValue()));
        }
    }

    private void fail(Player player, String messageKey) {
        SoundUtil.playErrorSound(player);
        player.sendMessage(plugin.getMessageManager().getPrefixedP(player, messageKey));
        plugin.getShopGuiManager().openSellInventory(player);
    }

    /** True when a net deposit of {@code net} would exceed the player's daily sell cap. */
    private boolean exceedsDailyCap(Player player, double net) {
        return net > 0 && pt.henrique.market.economy.EconomyManager.toCents(net)
                > plugin.getShopTransactionService().getRemainingDailyCapCents(player);
    }

    private int totalItems(List<ItemStack> items) {
        int n = 0;
        for (ItemStack i : items) if (i != null && !i.getType().isAir()) n += i.getAmount();
        return n;
    }

    private List<ItemStack> cloneNonEmpty(ItemStack[] arr) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack i : arr) if (i != null && !i.getType().isAir()) out.add(i.clone());
        return out;
    }

    private List<ItemStack> cloneList(List<ItemStack> in) {
        List<ItemStack> out = new ArrayList<>(in.size());
        for (ItemStack i : in) out.add(i == null ? null : i.clone());
        return out;
    }

    private ItemStack[] cloneArray(ItemStack[] arr) {
        ItemStack[] out = new ItemStack[arr.length];
        for (int i = 0; i < arr.length; i++) out[i] = arr[i] == null ? null : arr[i].clone();
        return out;
    }

    private ItemStack[] toArray(List<ItemStack> list) {
        return list.toArray(new ItemStack[0]);
    }
}
