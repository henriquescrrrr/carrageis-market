# Changelog

All notable changes to CommunityMarket will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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

For more information, see the [README](README.md) or visit our [GitHub repository](https://github.com/henrique/CommunityMarket).

