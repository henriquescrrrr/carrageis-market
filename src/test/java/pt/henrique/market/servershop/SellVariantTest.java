package pt.henrique.market.servershop;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import pt.henrique.market.servershop.model.SellQuote;
import pt.henrique.market.servershop.model.SellVariant;
import pt.henrique.market.servershop.service.EnchantPriceUtil;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rule this release is built on: two items count as the same thing only when
 * the game itself would stack them.
 * <p>
 * A server mock is needed because ItemStack meta and enchantment lookups go
 * through the running server's item factory and registries.
 */
class SellVariantTest {

    @BeforeAll
    static void setUpServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void tearDownServer() {
        MockBukkit.unmock();
    }

    private static SellVariant variant(ItemStack sample, double unitPrice) {
        return new SellVariant(new SellQuote("BOW", "Bow", unitPrice, false), sample, unitPrice);
    }

    private static ItemStack enchanted(Material material, Enchantment enchantment, int level) {
        ItemStack stack = new ItemStack(material);
        stack.addUnsafeEnchantment(enchantment, level);
        return stack;
    }

    private static Enchantment byKey(String key) {
        Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
        assertNotNull(enchantment, "enchantment missing from registry: " + key);
        return enchantment;
    }

    // ── The bow case ──────────────────────────────────────────────

    @Test
    void anOverpoweredBowIsNotThePlainBowNextToIt() {
        ItemStack good = new ItemStack(Material.BOW);
        good.addUnsafeEnchantment(byKey("power"), 5);
        good.addUnsafeEnchantment(byKey("flame"), 2);
        good.addUnsafeEnchantment(byKey("unbreaking"), 3);
        good.addUnsafeEnchantment(byKey("infinity"), 1);

        ItemStack junk = enchanted(Material.BOW, byKey("unbreaking"), 1);

        SellVariant junkVariant = variant(junk, 10.0);
        assertTrue(junkVariant.matches(junk.clone()), "a variant must match its own item");
        assertFalse(junkVariant.matches(good), "the good bow must not be sellable as the junk one");

        assertNull(SellVariantScanner.find(List.of(junkVariant), good),
                "lookup must not fall back to the same material");
    }

    @Test
    void identicalStacksMergeIntoOneEntry() {
        ItemStack one = enchanted(Material.BOW, byKey("unbreaking"), 1);
        ItemStack two = enchanted(Material.BOW, byKey("unbreaking"), 1);

        SellVariant v = variant(one, 10.0);
        v.add(0, one.getAmount());
        assertSame(v, SellVariantScanner.find(List.of(v), two), "same item, same entry");

        v.add(5, two.getAmount());
        assertEquals(2, v.amount());
        assertEquals(List.of(0, 5), v.slots());
        assertEquals(20.0, v.totalValue(), 0.001);
    }

    @Test
    void differentEnchantmentLevelsAreDifferentItems() {
        SellVariant sharpnessOne = variant(enchanted(Material.DIAMOND_SWORD, byKey("sharpness"), 1), 100.0);
        assertFalse(sharpnessOne.matches(enchanted(Material.DIAMOND_SWORD, byKey("sharpness"), 5)));
    }

    @Test
    void wearMakesItADifferentItem() {
        ItemStack pristine = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemStack worn = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = worn.getItemMeta();
        ((Damageable) meta).setDamage(500);
        worn.setItemMeta(meta);

        assertFalse(variant(pristine, 50.0).matches(worn),
                "a half-broken pickaxe must not be sold as a fresh one");
    }

    @Test
    void refundsRebuildTheItemThatWasTaken() {
        ItemStack good = new ItemStack(Material.BOW);
        good.addUnsafeEnchantment(byKey("power"), 5);
        good.addUnsafeEnchantment(byKey("infinity"), 1);

        ItemStack refunded = variant(good, 500.0).createStack(1);
        assertEquals(5, refunded.getEnchantmentLevel(byKey("power")));
        assertEquals(1, refunded.getEnchantmentLevel(byKey("infinity")));
    }

    @Test
    void theIdentityStackCannotBeMutatedFromOutside() {
        ItemStack sample = enchanted(Material.BOW, byKey("unbreaking"), 1);
        SellVariant v = variant(sample, 10.0);

        v.template().addUnsafeEnchantment(byKey("power"), 5);
        sample.addUnsafeEnchantment(byKey("power"), 5);

        assertFalse(v.matches(sample), "the variant must not follow edits to the stack it was built from");
        assertEquals(0, v.template().getEnchantmentLevel(byKey("power")));
    }

    // ── Enchanted books ───────────────────────────────────────────

    @Test
    void bookEnchantmentsAreReadFromStorage() {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        meta.addStoredEnchant(byKey("efficiency"), 5, true);
        meta.addStoredEnchant(byKey("unbreaking"), 3, true);
        meta.addStoredEnchant(byKey("fortune"), 3, true);
        book.setItemMeta(meta);

        // The bug this replaced: getEnchants() is empty for books, so every book
        // priced at zero and multi-enchantment books matched no price row at all.
        assertTrue(book.getItemMeta().getEnchants().isEmpty(), "premise: books keep enchants in storage");

        Map<Enchantment, Integer> effective = EnchantPriceUtil.effectiveEnchants(book);
        assertEquals(3, effective.size());
        assertEquals(5, effective.get(byKey("efficiency")));
        assertEquals(3, effective.get(byKey("unbreaking")));
        assertEquals(3, effective.get(byKey("fortune")));
        assertTrue(EnchantPriceUtil.isEnchantedBook(book));
    }

    @Test
    void booksWithDifferentEnchantmentsAreDifferentItems() {
        ItemStack efficiency = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta effMeta = (EnchantmentStorageMeta) efficiency.getItemMeta();
        effMeta.addStoredEnchant(byKey("efficiency"), 5, true);
        efficiency.setItemMeta(effMeta);

        ItemStack mending = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta mendMeta = (EnchantmentStorageMeta) mending.getItemMeta();
        mendMeta.addStoredEnchant(byKey("mending"), 1, true);
        mending.setItemMeta(mendMeta);

        assertFalse(variant(efficiency, 1000.0).matches(mending));
    }

    @Test
    void aPlainItemHasNoEnchantmentsToRead() {
        assertTrue(EnchantPriceUtil.effectiveEnchants(new ItemStack(Material.STONE)).isEmpty());
        assertTrue(EnchantPriceUtil.effectiveEnchants(null).isEmpty());
        assertFalse(EnchantPriceUtil.isEnchantedBook(new ItemStack(Material.BOOK)));
        assertEquals("", EnchantPriceUtil.describeEnchants(new ItemStack(Material.STONE)));
    }

    @Test
    void enchantmentsAreDescribedWithRomanLevels() {
        String described = EnchantPriceUtil.describeEnchants(
                enchanted(Material.DIAMOND_PICKAXE, byKey("efficiency"), 5));
        assertEquals("Efficiency V", described);
    }

    // ── Bulk-sale protection ──────────────────────────────────────

    @Test
    void bulkSalesHoldBackGearWorthKeeping() {
        assertTrue(SellVariantScanner.isProtectedGear(
                enchanted(Material.BOW, byKey("power"), 5)), "enchanted");

        ItemStack named = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = named.getItemMeta();
        meta.setDisplayName("Excalibur");
        named.setItemMeta(meta);
        assertTrue(SellVariantScanner.isProtectedGear(named), "renamed");

        assertFalse(SellVariantScanner.isProtectedGear(new ItemStack(Material.COBBLESTONE)),
                "plain resources must still sell in bulk");
    }

    @Test
    void wornGearIsRecognisedAsDamaged() {
        ItemStack worn = new ItemStack(Material.IRON_PICKAXE);
        ItemMeta meta = worn.getItemMeta();
        ((Damageable) meta).setDamage(10);
        worn.setItemMeta(meta);

        assertTrue(SellVariantScanner.isDamaged(worn));
        assertFalse(SellVariantScanner.isDamaged(new ItemStack(Material.IRON_PICKAXE)));
        assertFalse(SellVariantScanner.isDamaged(new ItemStack(Material.COBBLESTONE)),
                "blocks have no durability to lose");
    }
}
