package pt.henrique.market.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Fluent builder for creating ItemStacks with custom properties.
 * Used throughout the GUI system to create menu items.
 */
public class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    /**
     * Creates a new ItemBuilder with the specified material
     */
    public ItemBuilder(Material material) {
        this(material, 1);
    }

    /**
     * Creates a new ItemBuilder with the specified material and amount
     */
    public ItemBuilder(Material material, int amount) {
        this.item = new ItemStack(material, amount);
        this.meta = item.getItemMeta();
    }

    /**
     * Creates a new ItemBuilder from an existing ItemStack
     */
    /**
     * Wraps an existing stack as a GUI icon.
     * <p>
     * The count is clamped to the item's max stack size. Builder output only ever
     * goes into plugin GUI inventories, never to a player, and since 26.1 an
     * over-full stack resolves to empty, which would render the icon as a blank
     * slot. Real quantities are shown in the lore.
     */
    public ItemBuilder(ItemStack item) {
        this.item = item.clone();
        int maxStack = Math.max(1, this.item.getMaxStackSize());
        if (this.item.getAmount() > maxStack) {
            this.item.setAmount(maxStack);
        }
        this.meta = this.item.getItemMeta();
    }

    /**
     * Sets the display name using legacy color codes
     */
    public ItemBuilder name(String name) {
        if (meta != null) {
            meta.displayName(TextUtil.colorize(name));
        }
        return this;
    }

    /**
     * Sets the display name using a Component
     */
    public ItemBuilder name(Component name) {
        if (meta != null) {
            meta.displayName(name);
        }
        return this;
    }

    /**
     * Sets the lore using a list of strings with legacy color codes
     */
    public ItemBuilder lore(List<String> lore) {
        if (meta != null && lore != null) {
            List<Component> components = lore.stream()
                    .map(TextUtil::colorize)
                    .collect(Collectors.toList());
            meta.lore(components);
        }
        return this;
    }

    /**
     * Sets the lore using varargs strings
     */
    public ItemBuilder lore(String... lore) {
        return lore(List.of(lore));
    }

    /**
     * Adds a line to the existing lore
     */
    public ItemBuilder addLore(String line) {
        if (meta != null) {
            List<Component> existingLore = meta.lore();
            List<Component> newLore = existingLore != null ? new ArrayList<>(existingLore) : new ArrayList<>();
            newLore.add(TextUtil.colorize(line));
            meta.lore(newLore);
        }
        return this;
    }

    /**
     * Adds multiple lines to the existing lore
     */
    public ItemBuilder addLore(List<String> lines) {
        if (meta != null && lines != null) {
            List<Component> existingLore = meta.lore();
            List<Component> newLore = existingLore != null ? new ArrayList<>(existingLore) : new ArrayList<>();
            for (String line : lines) {
                newLore.add(TextUtil.colorize(line));
            }
            meta.lore(newLore);
        }
        return this;
    }

    /**
     * Sets the item amount
     */
    public ItemBuilder amount(int amount) {
        item.setAmount(Math.min(Math.max(1, item.getMaxStackSize()), Math.max(1, amount)));
        return this;
    }

    /**
     * Adds an enchantment glow effect (uses LUCK with HIDE_ENCHANTS flag)
     */
    public ItemBuilder glow(boolean glow) {
        if (glow && meta != null) {
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    /**
     * Always adds glow effect
     */
    public ItemBuilder glow() {
        return glow(true);
    }

    /**
     * Adds an enchantment
     */
    public ItemBuilder enchant(Enchantment enchantment, int level) {
        if (meta != null) {
            meta.addEnchant(enchantment, level, true);
        }
        return this;
    }

    /**
     * Hides all item flags (enchants, attributes, etc.)
     */
    public ItemBuilder hideFlags() {
        if (meta != null) {
            meta.addItemFlags(ItemFlag.values());
        }
        return this;
    }

    /**
     * Adds specific item flags
     */
    public ItemBuilder addFlags(ItemFlag... flags) {
        if (meta != null) {
            meta.addItemFlags(flags);
        }
        return this;
    }

    /**
     * Sets the item as unbreakable
     */
    public ItemBuilder unbreakable(boolean unbreakable) {
        if (meta != null) {
            meta.setUnbreakable(unbreakable);
            if (unbreakable) {
                meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            }
        }
        return this;
    }

    /**
     * Sets custom model data
     */
    public ItemBuilder customModelData(int data) {
        if (meta != null) {
            meta.setCustomModelData(data);
        }
        return this;
    }

    /**
     * Builds and returns the final ItemStack
     */
    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Creates a copy of an ItemStack with modified lore
     */
    public static ItemStack withLore(ItemStack original, List<String> lore) {
        return new ItemBuilder(original).lore(lore).build();
    }

    /**
     * Creates a copy of an ItemStack with additional lore appended
     */
    public static ItemStack appendLore(ItemStack original, List<String> additionalLore) {
        return new ItemBuilder(original).addLore(additionalLore).build();
    }
}

