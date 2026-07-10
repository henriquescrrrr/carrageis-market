package pt.henrique.market.servershop;

import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

/**
 * Helpers for reading and rewriting the contents of a shulker box {@link ItemStack}.
 * <p>
 * A shulker box stores its inventory inside a {@link BlockStateMeta} whose block
 * state is a {@link ShulkerBox}. {@code getBlockState()} returns a detached
 * snapshot, so reading contents never mutates the original item; to persist a
 * change we mutate the snapshot's inventory and write it back via
 * {@code setBlockState} + {@code setItemMeta}.
 * <p>
 * Shulker boxes are unstackable (max stack size 1), so each one occupies its own
 * inventory slot with amount 1.
 */
public final class ShulkerUtil {

    /** Number of slots inside a shulker box. */
    public static final int SHULKER_SIZE = 27;

    private ShulkerUtil() {}

    /** @return true if the stack is any shulker box (all 16 colors + uncolored). */
    public static boolean isShulkerBox(ItemStack stack) {
        return stack != null && !stack.getType().isAir()
                && stack.getType().name().endsWith("SHULKER_BOX");
    }

    /**
     * Returns the shulker's internal contents as a detached snapshot array.
     * Reading does not mutate the original item. Returns an empty array if the
     * stack is not a shulker box.
     */
    public static ItemStack[] getContents(ItemStack shulker) {
        if (!isShulkerBox(shulker)) return new ItemStack[0];
        if (!(shulker.getItemMeta() instanceof BlockStateMeta bsm)) return new ItemStack[0];
        if (!(bsm.getBlockState() instanceof ShulkerBox box)) return new ItemStack[0];
        return box.getInventory().getContents();
    }

    /** @return true if the shulker box contains at least one item. */
    public static boolean hasContents(ItemStack shulker) {
        for (ItemStack c : getContents(shulker)) {
            if (c != null && !c.getType().isAir()) return true;
        }
        return false;
    }

    /** @return total number of items (summed amounts) inside the shulker box. */
    public static int countTotalItems(ItemStack shulker) {
        int total = 0;
        for (ItemStack c : getContents(shulker)) {
            if (c != null && !c.getType().isAir()) total += c.getAmount();
        }
        return total;
    }

    /** @return number of occupied slots inside the shulker box. */
    public static int countNonEmptySlots(ItemStack shulker) {
        int n = 0;
        for (ItemStack c : getContents(shulker)) {
            if (c != null && !c.getType().isAir()) n++;
        }
        return n;
    }

    /** Returns a clone of the shulker with its internal inventory emptied. */
    public static ItemStack emptiedClone(ItemStack shulker) {
        ItemStack clone = shulker.clone();
        if (clone.getItemMeta() instanceof BlockStateMeta bsm
                && bsm.getBlockState() instanceof ShulkerBox box) {
            box.getInventory().clear();
            bsm.setBlockState(box);
            clone.setItemMeta(bsm);
        }
        return clone;
    }

    /**
     * Returns a clone of the shulker whose internal contents are replaced by the
     * given array (length must be ≤ {@link #SHULKER_SIZE}; shorter arrays clear
     * the remaining slots).
     */
    public static ItemStack withContents(ItemStack shulker, ItemStack[] newContents) {
        ItemStack clone = shulker.clone();
        if (clone.getItemMeta() instanceof BlockStateMeta bsm
                && bsm.getBlockState() instanceof ShulkerBox box) {
            box.getInventory().setContents(newContents);
            bsm.setBlockState(box);
            clone.setItemMeta(bsm);
        }
        return clone;
    }

    /**
     * @return true if this stack is itself a shulker box that contains items.
     * Such nested, packed shulkers are treated as non-sellable (always returned)
     * so their contents are never silently destroyed.
     */
    public static boolean isNestedShulkerWithContents(ItemStack stack) {
        return isShulkerBox(stack) && hasContents(stack);
    }
}
