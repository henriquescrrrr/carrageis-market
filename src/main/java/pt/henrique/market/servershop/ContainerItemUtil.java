package pt.henrique.market.servershop;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BundleContents;
import io.papermc.paper.datacomponent.item.ChargedProjectiles;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import io.papermc.paper.datacomponent.item.SulfurCubeContent;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Single answer to "does this stack contain other stacks, and if so which ones".
 * <p>
 * As of 26.2 an {@link ItemStack} can carry other stacks in four distinct ways,
 * none of which share an API:
 * <ol>
 *   <li>Shulker boxes and other block-entity items: {@link BlockStateMeta} whose
 *       block state is a container, or the {@code minecraft:container} component.</li>
 *   <li>Bundles: {@link BundleMeta#getItems()} / {@code minecraft:bundle_contents}.</li>
 *   <li>{@code SULFUR_CUBE_BUCKET}: {@code minecraft:sulfur_cube_content}, reached
 *       through {@link SulfurCubeContent#absorbedItem()}. Neither a
 *       {@link BlockStateMeta} nor a {@link BundleMeta}.</li>
 *   <li>Crossbows: {@code minecraft:charged_projectiles}.</li>
 * </ol>
 * The server's own {@code ItemStack.validateComponents} recurses into
 * {@code CONTAINER}, {@code BUNDLE_CONTENTS} and {@code CHARGED_PROJECTILES} but
 * <b>not</b> {@code SULFUR_CUBE_CONTENT}, so a sulfur cube bucket payload is never
 * sanitised by the server. Every pricing, selling, listing and anti-dupe check must
 * go through {@link #payloadOf(ItemStack)} rather than testing for a shulker box.
 * <p>
 * The shulker-box read/rewrite helpers below stay shulker-specific: they back the
 * three-option shulker sell flow, which needs the 27-slot inventory shape. Buckets,
 * bundles and crossbows have no such shape and are never opened, only refused.
 */
public final class ContainerItemUtil {

    /** Number of slots inside a shulker box. */
    public static final int SHULKER_SIZE = 27;

    /**
     * Levels of nesting inspected by {@link #flattenPayload(ItemStack)}.
     * Vanilla forbids shulker-in-shulker, so one level is enough today; the cap
     * exists so a datapack or plugin that deepens the nesting cannot walk past
     * the guard, and so a malformed cyclic payload cannot loop forever.
     */
    public static final int MAX_NESTING_DEPTH = 3;

    private ContainerItemUtil() {}

    // ══════════════════════════════════════════════════════════════
    //  General nesting
    // ══════════════════════════════════════════════════════════════

    /**
     * Returns the stacks held directly inside this one, across all four nesting
     * classes. Empty when the stack holds nothing or is not a container.
     * The returned stacks are the live snapshots handed back by the API; callers
     * that keep them should clone.
     */
    public static List<ItemStack> payloadOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return List.of();

        List<ItemStack> out = new ArrayList<>();

        // getItemMeta() clones, and this runs once per inventory slot in the sell
        // scans, so the meta routes are gated on the type name first. The component
        // checks below are cheap map lookups and cover everything the gate misses,
        // including block-entity items that are not shulker boxes.
        String typeName = stack.getType().name();
        if (typeName.endsWith("SHULKER_BOX")) {
            if (stack.getItemMeta() instanceof BlockStateMeta bsm
                    && bsm.getBlockState() instanceof ShulkerBox box) {
                addNonEmpty(out, box.getInventory().getContents());
            }
        } else if (typeName.endsWith("BUNDLE")) {
            if (stack.getItemMeta() instanceof BundleMeta bundle) {
                addNonEmpty(out, bundle.getItems());
            }
        }

        if (out.isEmpty() && stack.hasData(DataComponentTypes.CONTAINER)) {
            ItemContainerContents container = stack.getData(DataComponentTypes.CONTAINER);
            if (container != null) addNonEmpty(out, container.contents());
        }
        if (out.isEmpty() && stack.hasData(DataComponentTypes.BUNDLE_CONTENTS)) {
            BundleContents bundle = stack.getData(DataComponentTypes.BUNDLE_CONTENTS);
            if (bundle != null) addNonEmpty(out, bundle.contents());
        }
        if (stack.hasData(DataComponentTypes.SULFUR_CUBE_CONTENT)) {
            SulfurCubeContent cube = stack.getData(DataComponentTypes.SULFUR_CUBE_CONTENT);
            if (cube != null) addNonEmpty(out, List.of(cube.absorbedItem()));
        }
        if (stack.hasData(DataComponentTypes.CHARGED_PROJECTILES)) {
            ChargedProjectiles charged = stack.getData(DataComponentTypes.CHARGED_PROJECTILES);
            if (charged != null) addNonEmpty(out, charged.projectiles());
        }

        return out;
    }

    /**
     * True when this stack carries other stacks. Such items are refused by every
     * server-shop path: their payload has no price and would be destroyed with the
     * container, and a container priced as empty is a value-laundering route.
     */
    public static boolean isNestingItemWithContents(ItemStack stack) {
        return !payloadOf(stack).isEmpty();
    }

    /**
     * Every stack reachable from this one, breadth-first, down to
     * {@link #MAX_NESTING_DEPTH} levels. The stack itself is not included.
     * Used by the blacklist checks, which must see a banned material or a banned
     * lore keyword wherever it is hidden.
     */
    public static List<ItemStack> flattenPayload(ItemStack stack) {
        List<ItemStack> out = new ArrayList<>();
        Deque<ItemStack> frontier = new ArrayDeque<>(payloadOf(stack));
        int depth = 0;
        while (!frontier.isEmpty() && depth < MAX_NESTING_DEPTH) {
            int levelSize = frontier.size();
            for (int i = 0; i < levelSize; i++) {
                ItemStack current = frontier.poll();
                if (current == null) continue;
                out.add(current);
                if (depth + 1 < MAX_NESTING_DEPTH) frontier.addAll(payloadOf(current));
            }
            depth++;
        }
        return out;
    }

    /** Total number of items (summed amounts) held inside this stack, one level deep. */
    public static int countTotalItems(ItemStack stack) {
        int total = 0;
        for (ItemStack c : payloadOf(stack)) total += c.getAmount();
        return total;
    }

    /** Number of occupied slots inside this stack, one level deep. */
    public static int countNonEmptySlots(ItemStack stack) {
        return payloadOf(stack).size();
    }

    // ══════════════════════════════════════════════════════════════
    //  Shulker boxes: read and rewrite
    // ══════════════════════════════════════════════════════════════

    /** @return true if the stack is any shulker box (all 16 colors + uncolored). */
    public static boolean isShulkerBox(ItemStack stack) {
        return stack != null && !stack.getType().isAir()
                && stack.getType().name().endsWith("SHULKER_BOX");
    }

    /**
     * Returns the shulker's internal contents as a detached snapshot array.
     * {@code getBlockState()} returns a copy, so reading never mutates the original
     * item. Returns an empty array if the stack is not a shulker box.
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
     * given array (length must be at most {@link #SHULKER_SIZE}; shorter arrays
     * clear the remaining slots).
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

    // ══════════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════════

    private static void addNonEmpty(List<ItemStack> out, ItemStack[] items) {
        if (items == null) return;
        for (ItemStack i : items) {
            if (i != null && !i.getType().isAir()) out.add(i);
        }
    }

    private static void addNonEmpty(List<ItemStack> out, List<ItemStack> items) {
        if (items == null) return;
        for (ItemStack i : items) {
            if (i != null && !i.getType().isAir()) out.add(i);
        }
    }
}
