package pt.henrique.market.servershop.model;

import org.bukkit.Material;

import java.util.*;

/**
 * Represents a server shop category containing a set of item IDs.
 * Categories are loaded from items.yml and define groupings for the GUI display.
 * Items not assigned to any category fall into MISCELLANEOUS.
 */
public class ShopCategory {

    private final String id;
    private String displayName;
    private Material icon;
    private String description;
    private double sellMultiplier;
    private boolean buyEnabled;
    private boolean sellEnabled;
    private final List<String> itemIds;

    public ShopCategory(String id) {
        this.id = id;
        this.displayName = id;
        this.icon = Material.CHEST;
        this.description = "";
        this.sellMultiplier = 0.25;
        this.buyEnabled = true;
        this.sellEnabled = true;
        this.itemIds = new ArrayList<>();
    }

    public String getId() { return id; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public Material getIcon() { return icon; }
    public void setIcon(Material icon) { this.icon = icon; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getSellMultiplier() { return sellMultiplier; }
    public void setSellMultiplier(double sellMultiplier) { this.sellMultiplier = sellMultiplier; }

    public boolean isBuyEnabled() { return buyEnabled; }
    public void setBuyEnabled(boolean buyEnabled) { this.buyEnabled = buyEnabled; }

    public boolean isSellEnabled() { return sellEnabled; }
    public void setSellEnabled(boolean sellEnabled) { this.sellEnabled = sellEnabled; }

    public List<String> getItemIds() { return Collections.unmodifiableList(itemIds); }
    public void addItem(String itemId) { itemIds.add(itemId); }

    public int getItemCount() { return itemIds.size(); }
}
