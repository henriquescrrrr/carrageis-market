# Changelog

All notable changes to Market will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.1.0] - 2026-08-21

Two builds from one source tree, both included in this release:
`market-2.1.0+mc1.21.11.jar` for Paper/Purpur 1.21.x and
`market-2.1.0+mc26.2.jar` for Paper/Purpur 26.2.

### Fixed

- **Selling told enchanted items apart from plain ones.** The sell flows grouped
  and removed items by material, so a Power V / Flame / Infinity / Unbreaking III
  bow and a plain bow were one entry at one price, and selling "a bow" could hand
  over the good one. Every sell flow — inventory grid, hand, sell-all,
  multi-select, chest and shulker — now groups by the item itself and removes only
  stacks identical to the one shown.
- **Enchanted books are worth all their enchantments.** Book prices came from
  whichever single `ENCHANTED_BOOK_*` row matched, so Efficiency V + Unbreaking III
  + Fortune III sold for the price of Efficiency V alone. A book is now worth the
  sum of its enchantments. Books with more than one enchantment previously matched
  no row at all. Relatedly, the enchantment bonus read `ItemMeta#getEnchants()`,
  which is empty for books — it now reads stored enchantments too.
- **`/sell` offers all three ways to sell.** It went straight to the inventory
  grid; the hand and container routes were reachable only from inside the shop
  menu. It now opens a chooser, with `/sell inventory|hand|container` to skip it.
  Added the `/vender` and `/venda` aliases.
- **Search asks in chat everywhere.** The category and search-result screens forced
  the sign editor regardless of `server-shop.search-mode`, which the main menu
  already honoured.
- **A failed payout refunds the item that was taken**, not a plain one of the same
  type. The old refund path rebuilt the item from its price row, destroying
  enchantments.
- **Language files pick up new strings on update.** Only `en_US` merged the jar's
  defaults, so strings added by an update were missing — or English — in every
  other locale.

### Added

- Sell grid pagination, and a per-material filter when several versions of an item
  are owned.
- Per-item price breakdown on the sell screen, including which enchantment
  contributes what on a book.
- `sell-inventory.sell-all.skip-special-gear` (default true): bulk sales hold back
  enchanted, renamed and ToolMods-fitted items, and report how many were skipped.
  `skip-damaged-gear` (default false) extends that to worn tools and armour.
- `server-shop.enchanted-book.sum-enchantments` (default true) to switch book
  summing off.

### Removed

- The "sell to server / community / auction" chooser screen, which nothing opened.

### Technical

- Maven profiles `mc1211` (default) and `mc262` build against paper-api 1.21.11 and
  26.2 from the same sources; both emit Java 21 bytecode. See BUILDING.md.
- `server-shop.items.match-mode` now only picks the price row. Which items a sale
  takes is decided by item identity, never by the match mode.

---

## [1.0.0] - 2026-01-15

### Added

#### Core Features
- **Fixed-Price Market**: Create and browse fixed-price listings
- **Auction System**: Full auction support with bidding, buyout, and anti-snipe protection
- **Claim Storage**: Safe item delivery for expired listings and won auctions
- **Earnings System**: Pending earnings with withdraw functionality

#### GUI System
- Fully GUI-based interface - no complex commands to learn
- Intuitive item selection from player inventory
- Quantity selection with step buttons for stackable items
- Price and duration settings with click-to-adjust controls
- Paginated browsing for listings and auctions
- Confirmation dialogs for purchases and bids

#### Economy Integration
- **Vault support** (primary): Works with any Vault-compatible economy plugin
- **EssentialsX fallback**: Direct integration when Vault is not available
- Thread-safe economy operations
- Detailed startup logging for economy detection

#### Administration
- Admin GUI for viewing all listings and auctions
- Remove any listing or cancel any auction
- Force-end auctions with proper refunds
- In-game configuration reload

#### Configuration
- SQLite database (default) with MySQL support
- Configurable taxes for market and auction sales
- Listing and auction limits per player
- Duration options for listings and auctions
- Item blacklist by material and keywords
- Sound effects customization
- Notification settings

#### Localization
- Full language file support
- Included languages: English (en_US), Portuguese (pt_PT)
- All messages and GUI text customizable

### Technical
- Built for Paper 1.21+
- Requires Java 21+
- Uses HikariCP for database connection pooling
- Async database operations with sync economy calls for safety
- Atomic transaction handling to prevent duplication bugs

---

## Future Releases

### Planned Features
- Search/filter functionality in browse GUIs
- Category system for listings
- Statistics and leaderboards
- Public API for developer integration
- Additional language translations

---

For more information, see the [README](README.md) or visit the [GitHub repository](https://github.com/henriquescrrrr/carrageis-market).

