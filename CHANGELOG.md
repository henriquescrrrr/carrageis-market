# Changelog

All notable changes to Market will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.2.0] - 2026-10-01

Requires Minecraft 26.3 or 26.2 and Java 25. Two jars from the same source: `market-2.2.0+mc26.3.jar` and `market-2.2.0+mc26.2.jar`. On 1.21.x use 2.1.0.

### Fixed

- **2.1.0 for 26.2 had lost the 2.0.0 fixes.** It was built from the 1.21.x line, which never had them. This release joins the two lines: everything 2.1.0 added (selling the exact item picked, enchanted books worth all their enchantments, the `/sell` chooser) together with the 2.0.0 container rules.
- **A loaded container was sold as an empty one again.** Under 2.1.0 a chest sale listed filled shulker boxes, and sell-all did too when `sell-inventory.shulker-sell.enabled` was off; both paid the empty-box price and deleted what was inside. No sell screen offers a stack that holds other stacks any more: shulker boxes, bundles, loaded sulfur cube buckets and loaded crossbows. Filled shulker boxes still go to the three-way shulker chooser.
- Selling a loaded container from the hand says why it was refused instead of "not sellable", and a chest sale tells the player how many loaded containers it left alone.
- **Thirteen default prices paid more for an item than its ingredients cost in the shop.** Buying the ingredients, crafting and selling back made money every time. The worst: a diamond shovel sold for 1540 against 901 for one diamond and two sticks, a name tag for 63.75 against 3.50 of paper and copper nugget, a lodestone for 191.25 against 83. Each of these now sells for 65% of the cheapest shop cost of its ingredients, the rule the potion prices already follow. Lowered: diamond shovel 1540 → 585.65, lodestone 191.25 → 53.95, saddle 93.50 → 42.25, brewing stand 63.75 → 40.95, name tag 63.75 → 2.27, flower and bordure indented banner patterns 25.50 → 2.93, field masoned banner pattern 25.50 → 7.48, grindstone 5.50 → 3.41, chiseled polished blackstone 4.25 → 2.60, stone spear 3.00 → 1.30, chiseled deepslate 2.00 → 0.98, nether brick 1.62 → 0.49. This changes the shipped `prices.yml` only: a server that already has its own keeps its numbers and should apply these by hand.
- `FROSTED_ICE` is no longer in the item list. It is not an item, and every start logged a warning about it.

### Added

- **Prices and categories for the 105 tradable items of Minecraft 26.3.** Every price comes from an item already in the table, so the tier spreads hold:
  - poplar wood set = cherry wood set, item for item; poplar leaves and sapling = cherry leaves and sapling;
  - wool and concrete slabs = half the block of the same colour; stairs = the block, because the stonecutter makes one stair from one concrete block;
  - cushions = three wool slabs of their colour, which is the recipe;
  - straw bed = three quarters of a hay bale (three bales make four beds);
  - shelf mushroom = brown mushroom; red shrub = bush.
- Every recipe in the 26.3 data pack was checked for a buy-craft-sell loop against the whole table: 1661 recipes, none left.
- **The 16 explorer maps are not for sale.** 26.3 turned them into items of their own (`OCEAN_MONUMENT_MAP`, `WOODLAND_MANSION_MAP` and so on). Cartographers sell them without limit, and a map bought from the shop would carry no map data. They are in Disabled / OP Items, with buying and selling switched off in `prices.yml` as well, so an existing server that never sees the new `items.yml` still cannot trade them.

### Changed

- Built against Paper 26.3 on Java 25 and tested on Paper 26.3 build 140, which Paper still marks as beta: there was no stable 26.3 build when this was released. The 26.2 jar is built against Paper 26.2 from the same source.
- On an existing server, prices for the new items come from the jar's defaults until `prices.yml` names them. `items.yml` on disk is not merged, so new items land in a category picked from their name until they are added to it.
- Tests run against MockBukkit for 26.2: 22 pass, 4 of them new, covering what counts as a stack holding other stacks.

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

## [2.0.0] - 2026-08-06

Requires Minecraft 26.2 and Java 25. This release does not run on 1.21.x; upgrade the server before installing it.

### Fixed

- Selling a chest that held a filled shulker box destroyed its contents. The Sell Chest scan tested only for a sell price and the BuildersDream tag, so a full box was priced as an empty one, and the removal step matched by item id, which in the default match mode makes a full box a valid target for a quote written against an empty one. The player was paid the price of an empty shulker and lost everything inside it. Containers holding items are skipped now, and the player is told how many were left alone.
- The same omission was in four more paths. Multi-sell and percentage selling had no container test at all, so selling one empty shulker box could remove a filled one from an earlier slot. Sell-all and the sell-inventory screen protected shulker boxes only when `sell-inventory.shulker-sell.enabled` was on, and deleted them when it was off. All of them refuse a container holding items now, in both match modes.
- Selling from the hand refuses a loaded bundle or a loaded sulfur cube bucket with a message instead of buying it at the empty item's price.
- Listing more than one stack could hand the buyer nothing. The quantity selector let a player list everything they held, so five stacks of dirt became a single count of 320, and from 26.1 a stack larger than its own maximum is read as empty. The purchase, the refund on a failed listing, the return on cancel or expiry, and claim storage were each a place where the player received an empty slot. Items are split into legal stacks before they are given or taken now, and every leftover is delivered instead of only the first.
- The same count rendered as a blank slot in Browse Market, My Listings and the quantity selector. Icon counts are clamped, and against the item's real maximum: items that stack to 16 or to 1 were treated as stacking to 64.
- On the player market and on auctions, a blacklisted material or a blacklisted name or lore keyword could be sold inside a shulker box, a bundle or a loaded sulfur cube bucket. Every rule now applies to the container and to everything inside it, three levels deep, and listing and auction creation both run the same check before anything is written.
- The server shop read the maximum stack size from the material rather than from the item when handing goods over, which is wrong for a shop item whose components lower it.

### Changed

- Built against Paper 26.2 on Java 25.
- The server shop refuses a container that holds items rather than pricing it. Valuing the contents would mean applying every pricing, tax and cap rule recursively, and it would make a container the cheapest way to launder anything those rules do not cover; handing the contents back means editing a player's container in the middle of a transaction that already has a rollback to manage. The one exception stays: a filled shulker box still routes to the three-way chooser (box only, box and contents, contents only), which prices each stack on its own and returns everything the shop does not buy.
- The player-to-player market and auctions still accept filled containers. Prices there are set by the seller and the item transfers intact, so the check is the blacklist, applied to the contents.
- Listings already stored with an oversized count keep it and are split when they are handed out, so no database migration is needed.

### Added

- Prices and shop categories for the 31 new 26.2 materials: the sulfur and cinnabar families with their slabs, stairs and walls, the sulfur spike, potent sulfur, the golden dandelion and the new music disc. Every sell price sits below the cheapest way to obtain that item through the shop itself, and a cut variant never sells for more than the block it is cut from, so the stonecutter cannot be turned into a money printer. The wandering trader and crafting routes into the two families were checked against the same rule.
- `SULFUR_CUBE_BUCKET` and `SULFUR_CUBE_SPAWN_EGG` ship with buying and selling disabled. The bucket can swallow an item, and the code refuses a loaded one on every shop path whatever the config says.
- Message keys `sell-chest-skipped-containers` and `sell-hand-container-loaded` in `en_US`, `pt_PT` and `fr_FR`.

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

