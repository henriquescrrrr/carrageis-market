package pt.henrique.market.servershop;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Per-player state for the multi-select "sell to server" flow.
 * <p>
 * The player toggles {@link #active selection mode} on, clicks several sellable
 * inventory items to {@link #toggle(String) select} them, picks a
 * {@link #getPercent() percentage}, and sells a percentage of the total amount
 * of each selected item type in one batch.
 * <p>
 * Stored per-player in {@code ShopGuiManager} so the selection survives the
 * fresh-instance-per-open pattern used by the shop GUIs. It is cleared only at
 * explicit points (completed sale, leaving the sell flow, plugin disable) — see
 * {@code ShopGuiManager} and {@code GuiListener} for the lifecycle rationale.
 */
public class MultiSellSession {

    /** Item IDs the player has selected. Insertion order → stable preview grid. */
    private final Set<String> selectedItemIds = new LinkedHashSet<>();

    /** Whether selection mode is currently toggled on in the Sell Items GUI. */
    private boolean active = false;

    /** Currently chosen percentage (1–100). */
    private int percent = 100;

    /** Toggles selection mode on/off. */
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    /** @return whether the given item ID is currently selected. */
    public boolean isSelected(String itemId) {
        return selectedItemIds.contains(itemId);
    }

    /**
     * Toggles selection of an item ID.
     *
     * @return {@code true} if the item is now selected, {@code false} if removed.
     */
    public boolean toggle(String itemId) {
        if (selectedItemIds.contains(itemId)) {
            selectedItemIds.remove(itemId);
            return false;
        }
        selectedItemIds.add(itemId);
        return true;
    }

    /** @return an unmodifiable, insertion-ordered view of selected item IDs. */
    public Set<String> getSelected() {
        return Collections.unmodifiableSet(selectedItemIds);
    }

    public int getSelectedCount() { return selectedItemIds.size(); }

    public boolean isEmpty() { return selectedItemIds.isEmpty(); }

    /** Clears the selected items but keeps {@link #active} and {@link #percent}. */
    public void clearSelection() { selectedItemIds.clear(); }

    /** Fully resets the session (clears selection and turns selection mode off). */
    public void reset() {
        selectedItemIds.clear();
        active = false;
    }

    public int getPercent() { return percent; }

    /** Sets the percentage, clamped to the valid 1–100 range. */
    public void setPercent(int percent) {
        this.percent = Math.max(1, Math.min(100, percent));
    }

    /**
     * Computes how many units to sell for a given total at a given percentage.
     * <p>
     * Rounding is half-up: 250 @ 50% → 125, 3 @ 50% → 2. A result below one unit
     * rounds down to {@code 0}, so the item is skipped rather than selling a whole
     * unit for a tiny percentage. {@code 100%} always returns the full total.
     *
     * @param total   total units of the item the player holds (≥ 0)
     * @param percent percentage to sell (1–100)
     * @return number of units to sell (0 if nothing should be sold)
     */
    public static int applyPercent(int total, int percent) {
        if (total <= 0 || percent <= 0) return 0;
        if (percent >= 100) return total;
        int qty = (int) (((long) total * percent + 50) / 100); // round half-up
        return qty < 1 ? 0 : qty;
    }
}
