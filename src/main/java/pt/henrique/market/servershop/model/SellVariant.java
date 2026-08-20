package pt.henrique.market.servershop.model;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One distinct sellable item as it exists in a container, identified by what the
 * item actually is rather than by its material.
 * <p>
 * A Power V / Flame / Infinity / Unbreaking III bow and a plain Unbreaking I bow
 * are both {@code BOW}, but they are two variants: they show as two entries, are
 * priced separately, and selling one never takes the other. Two stacks join the
 * same variant only when {@link ItemStack#isSimilar(ItemStack)} says they are the
 * same item — the same rule the game itself uses to stack them.
 */
public final class SellVariant {

    private final SellQuote quote;
    /** Amount-1 clone that defines this variant's identity. Never handed out unguarded. */
    private final ItemStack template;
    /** Sell price for one unit, with enchantments, tool mods and wear applied. */
    private final double unitPrice;
    private final List<Integer> slots = new ArrayList<>();
    private int amount;

    public SellVariant(SellQuote quote, ItemStack sample, double unitPrice) {
        this.quote = quote;
        this.template = sample.clone();
        this.template.setAmount(1);
        this.unitPrice = unitPrice;
    }

    /** Records another stack of this same variant found at {@code slot}. */
    public void add(int slot, int stackAmount) {
        slots.add(slot);
        amount += stackAmount;
    }

    /** True when {@code stack} is the same item as this variant, ignoring stack size. */
    public boolean matches(ItemStack stack) {
        return stack != null && template.isSimilar(stack);
    }

    /** A fresh stack of this variant with the given amount — used for refunds. */
    public ItemStack createStack(int stackAmount) {
        ItemStack copy = template.clone();
        copy.setAmount(Math.max(1, stackAmount));
        return copy;
    }

    public SellQuote quote() { return quote; }
    public String itemId() { return quote.itemId(); }
    public String displayName() { return quote.displayName(); }
    /** Defensive copy of the identity stack (amount 1). */
    public ItemStack template() { return template.clone(); }
    public double unitPrice() { return unitPrice; }
    public int amount() { return amount; }
    public List<Integer> slots() { return Collections.unmodifiableList(slots); }

    /** Total value of every unit of this variant, before tax. */
    public double totalValue() {
        return Math.round(unitPrice * amount * 100.0) / 100.0;
    }
}
