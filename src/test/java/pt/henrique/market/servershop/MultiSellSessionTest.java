package pt.henrique.market.servershop;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Multi-select holds item templates, so selecting one item never drags in its lookalike. */
class MultiSellSessionTest {

    @BeforeAll
    static void setUpServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void tearDownServer() {
        MockBukkit.unmock();
    }

    private static Enchantment byKey(String key) {
        Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
        assertNotNull(enchantment, "enchantment missing from registry: " + key);
        return enchantment;
    }

    @Test
    void selectingThePlainPickaxeLeavesTheEnchantedOneAlone() {
        ItemStack plain = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemStack enchanted = new ItemStack(Material.DIAMOND_PICKAXE);
        enchanted.addUnsafeEnchantment(byKey("efficiency"), 5);

        MultiSellSession session = new MultiSellSession();
        assertTrue(session.toggle(plain));

        assertTrue(session.isSelected(plain));
        assertFalse(session.isSelected(enchanted));
        assertEquals(1, session.getSelectedCount());
    }

    @Test
    void toggleAddsThenRemoves() {
        ItemStack stack = new ItemStack(Material.COBBLESTONE, 64);
        MultiSellSession session = new MultiSellSession();

        assertTrue(session.toggle(stack));
        assertEquals(1, session.getSelectedCount());
        assertFalse(session.toggle(stack));
        assertTrue(session.isEmpty());
    }

    @Test
    void selectionsAreStoredAsSingleUnitsAndSurviveStackChanges() {
        ItemStack stack = new ItemStack(Material.COBBLESTONE, 64);
        MultiSellSession session = new MultiSellSession();
        session.toggle(stack);

        assertEquals(1, session.getSelected().get(0).getAmount(),
                "a selection is an identity, not a quantity");

        stack.setAmount(12);
        assertTrue(session.isSelected(stack), "stack size must not affect identity");
    }

    @Test
    void percentageRoundsHalfUpAndNeverSellsAStrayUnit() {
        assertEquals(250, MultiSellSession.applyPercent(250, 100));
        assertEquals(125, MultiSellSession.applyPercent(250, 50));
        assertEquals(2, MultiSellSession.applyPercent(3, 50));
        assertEquals(0, MultiSellSession.applyPercent(1, 10), "10% of one item is nothing, not one");
        assertEquals(0, MultiSellSession.applyPercent(0, 100));
    }

    @Test
    void percentIsClampedToTheUsableRange() {
        MultiSellSession session = new MultiSellSession();
        session.setPercent(0);
        assertEquals(1, session.getPercent());
        session.setPercent(500);
        assertEquals(100, session.getPercent());
    }

    @Test
    void resetClearsBothSelectionAndMode() {
        MultiSellSession session = new MultiSellSession();
        session.setActive(true);
        session.toggle(new ItemStack(Material.COBBLESTONE));

        session.reset();
        assertTrue(session.isEmpty());
        assertFalse(session.isActive());
    }
}
