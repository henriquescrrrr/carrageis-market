package pt.henrique.market.servershop;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import pt.henrique.market.Market;
import pt.henrique.market.hook.ToolModsHook;
import pt.henrique.market.servershop.model.SellQuote;
import pt.henrique.market.servershop.model.SellVariant;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Groups the sellable contents of an inventory into {@link SellVariant}s.
 * <p>
 * Grouping is by item identity, not by material, so an enchanted tool never hides
 * behind a plain one of the same type. Every sell flow — the sell GUI, sell-all,
 * multi-select, chest and shulker sales — scans through here, which is what keeps
 * their prices and their removals talking about the same items.
 */
public class SellVariantScanner {

    private final Market plugin;

    public SellVariantScanner(Market plugin) {
        this.plugin = plugin;
    }

    /**
     * What a given scan should leave alone.
     *
     * @param skipShulkersWithContents  kept for callers; any stack holding other stacks is
     *                                  skipped regardless, see {@link #scan}
     * @param skipProtectedGear         enchanted, renamed or tool-modded items
     * @param skipDamagedGear           anything already worn
     */
    public record Options(boolean skipShulkersWithContents,
                          boolean skipProtectedGear,
                          boolean skipDamagedGear) {

        /** The sell GUI and container sales show everything; the player picks one entry. */
        public static Options showAll(boolean skipShulkersWithContents) {
            return new Options(skipShulkersWithContents, false, false);
        }
    }

    /**
     * The variants found, plus how many items were deliberately left out so a GUI
     * can say so instead of silently shrinking the total.
     */
    public record Result(List<SellVariant> variants, int protectedItems) {
        public boolean isEmpty() { return variants.isEmpty(); }
        public int totalUnits() {
            int n = 0;
            for (SellVariant v : variants) n += v.amount();
            return n;
        }
        public double grossValue() {
            double total = 0;
            for (SellVariant v : variants) total += v.totalValue();
            return Math.round(total * 100.0) / 100.0;
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Entry points
    // ══════════════════════════════════════════════════════════════

    /** Scans the player's 36 storage slots. Slot indices are player-inventory indices. */
    public Result scanStorage(Player player, Options options) {
        return scan(player.getInventory().getStorageContents(), options);
    }

    /**
     * Scans the player's whole inventory, honouring the sell-all config toggles for
     * the hotbar (slots 0–8) and worn gear (36–40).
     */
    public Result scanForSellAll(Player player) {
        boolean includeHotbar = plugin.getConfig().getBoolean("sell-inventory.sell-all.include-hotbar", true);
        boolean includeArmour = plugin.getConfig().getBoolean("sell-inventory.sell-all.include-armor-and-shield", false);
        Options options = new Options(
                plugin.getConfig().getBoolean("sell-inventory.shulker-sell.enabled", true),
                plugin.getConfig().getBoolean("sell-inventory.sell-all.skip-special-gear", true),
                plugin.getConfig().getBoolean("sell-inventory.sell-all.skip-damaged-gear", false));

        ItemStack[] contents = player.getInventory().getContents();
        ItemStack[] eligible = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            if (!includeHotbar && i <= 8) continue;
            if (!includeArmour && i >= 36 && i <= 40) continue;
            eligible[i] = contents[i];
        }
        return scan(eligible, options);
    }

    /**
     * The slots {@link #scanForSellAll} drew from, so the sale removes items from
     * exactly the places the preview counted them.
     */
    public IntPredicate sellAllSlots() {
        boolean includeHotbar = plugin.getConfig().getBoolean("sell-inventory.sell-all.include-hotbar", true);
        boolean includeArmour = plugin.getConfig().getBoolean("sell-inventory.sell-all.include-armor-and-shield", false);
        return slot -> {
            if (slot < 0 || slot > 40) return false;
            if (!includeHotbar && slot <= 8) return false;
            return includeArmour || slot < 36;
        };
    }

    /** Scans any container inventory (chest, barrel, ender chest, shulker). */
    public Result scanContainer(Inventory inventory, Options options) {
        ItemStack[] contents = new ItemStack[inventory.getSize()];
        for (int i = 0; i < contents.length; i++) contents[i] = inventory.getItem(i);
        return scan(contents, options);
    }

    /**
     * Values a single stack on its own, for the sell-from-hand flow.
     *
     * @return the variant, or {@code null} when the item cannot be sold
     */
    public SellVariant variantOf(ItemStack stack, int slot) {
        if (stack == null || stack.getType().isAir()) return null;
        if (isDreamItem(stack)) return null;
        if (ContainerItemUtil.isNestingItemWithContents(stack)) return null;
        SellVariant variant = build(stack);
        if (variant == null) return null;
        variant.add(slot, stack.getAmount());
        return variant;
    }

    // ══════════════════════════════════════════════════════════════
    //  Core
    // ══════════════════════════════════════════════════════════════

    /**
     * Walks {@code contents} and merges each stack into the variant it belongs to.
     * Null entries are skipped, so callers can blank out slots they want excluded
     * while keeping the indices meaningful.
     */
    public Result scan(ItemStack[] contents, Options options) {
        List<SellVariant> variants = new ArrayList<>();
        int protectedItems = 0;

        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType().isAir()) continue;
            if (isDreamItem(stack)) continue;
            // A stack holding other stacks is priced as its empty shell, and selling it
            // removes the payload with it. Filled shulker boxes have their own chooser;
            // bundles, loaded sulfur cube buckets and loaded crossbows have no quote at
            // all. None of them is ever offered here, whatever the options say.
            if (ContainerItemUtil.isNestingItemWithContents(stack)) continue;
            if (options.skipProtectedGear() && isProtectedGear(stack)) {
                protectedItems += stack.getAmount();
                continue;
            }
            if (options.skipDamagedGear() && isDamaged(stack)) {
                protectedItems += stack.getAmount();
                continue;
            }

            SellVariant existing = find(variants, stack);
            if (existing != null) {
                existing.add(slot, stack.getAmount());
                continue;
            }
            SellVariant variant = build(stack);
            if (variant == null) continue;
            variant.add(slot, stack.getAmount());
            variants.add(variant);
        }

        return new Result(variants, protectedItems);
    }

    /** The variant in {@code variants} that {@code stack} belongs to, or null. */
    public static SellVariant find(List<SellVariant> variants, ItemStack stack) {
        for (SellVariant v : variants) {
            if (v.matches(stack)) return v;
        }
        return null;
    }

    /** Prices a stack and wraps it as a variant, or returns null if it has no value. */
    private SellVariant build(ItemStack stack) {
        SellQuote quote = plugin.getShopPricingService().quoteSell(stack);
        if (quote == null) return null;
        double unitPrice = plugin.getShopTransactionService()
                .computeAdjustedSellPrice(quote.baseSell(), stack, quote.composite());
        if (unitPrice <= 0) return null;
        return new SellVariant(quote, stack, unitPrice);
    }

    // ══════════════════════════════════════════════════════════════
    //  Classification
    // ══════════════════════════════════════════════════════════════

    /**
     * Items a bulk sale should not touch: anything enchanted, renamed, given lore
     * or fitted with ToolMods. These are the items players build and care about,
     * and a single mis-click on "sell everything" is how they get lost.
     */
    public static boolean isProtectedGear(ItemStack stack) {
        if (stack == null) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        if (meta.hasDisplayName() || meta.hasLore()) return true;
        if (meta.hasEnchants()) return true;
        if (meta instanceof EnchantmentStorageMeta storage && storage.hasStoredEnchants()) return true;
        return ToolModsHook.hasMods(stack);
    }

    /** True for a tool or armour piece that has already taken damage. */
    public static boolean isDamaged(ItemStack stack) {
        if (stack == null) return false;
        if (stack.getType().getMaxDurability() <= 0) return false;
        return stack.getItemMeta() instanceof Damageable damageable && damageable.getDamage() > 0;
    }

    private boolean isDreamItem(ItemStack stack) {
        return plugin.getBuildersDreamHook() != null && plugin.getBuildersDreamHook().isDreamItem(stack);
    }
}
