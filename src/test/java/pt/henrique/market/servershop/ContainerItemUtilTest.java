package pt.henrique.market.servershop;

import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every server-shop sell path refuses a stack that holds other stacks, because the
 * shop prices it as its empty shell and would delete the payload with it. That
 * refusal rests on this one test of "does it hold anything".
 */
class ContainerItemUtilTest {

    @BeforeAll
    static void setUpServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void tearDownServer() {
        MockBukkit.unmock();
    }

    private static ItemStack shulkerHolding(ItemStack... items) {
        ItemStack box = new ItemStack(Material.SHULKER_BOX);
        BlockStateMeta meta = (BlockStateMeta) box.getItemMeta();
        ShulkerBox state = (ShulkerBox) meta.getBlockState();
        state.getInventory().addItem(items);
        meta.setBlockState(state);
        box.setItemMeta(meta);
        return box;
    }

    @Test
    void emptyShulkerHoldsNothing() {
        assertFalse(ContainerItemUtil.isNestingItemWithContents(new ItemStack(Material.SHULKER_BOX)));
    }

    @Test
    void loadedShulkerHoldsItems() {
        ItemStack box = shulkerHolding(new ItemStack(Material.NETHERITE_INGOT, 3));
        assertTrue(ContainerItemUtil.isNestingItemWithContents(box));
        assertTrue(ContainerItemUtil.countTotalItems(box) >= 3);
    }

    @Test
    void loadedBundleHoldsItems() {
        ItemStack bundle = new ItemStack(Material.BUNDLE);
        BundleMeta meta = (BundleMeta) bundle.getItemMeta();
        meta.addItem(new ItemStack(Material.DIAMOND, 2));
        bundle.setItemMeta(meta);
        assertTrue(ContainerItemUtil.isNestingItemWithContents(bundle));
    }

    @Test
    void plainItemsHoldNothing() {
        assertFalse(ContainerItemUtil.isNestingItemWithContents(new ItemStack(Material.DIAMOND, 64)));
        assertFalse(ContainerItemUtil.isNestingItemWithContents(new ItemStack(Material.BUNDLE)));
    }
}
