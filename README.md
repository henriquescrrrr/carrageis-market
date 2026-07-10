# Market

[![Modrinth Downloads](https://img.shields.io/modrinth/dt/market?logo=modrinth)](https://modrinth.com/plugin/market)
[![GitHub License](https://img.shields.io/github/license/henrique/Market)](LICENSE)

A unified, GUI-only **marketplace plugin** for Minecraft Paper 1.21.11+.

One command (`/market`) opens a **hub GUI** with three main sections:
- 🏪 **Server Market** — Buy/sell any item from the server at fixed prices (large spread encourages player trading)
- 🛒 **Community Market** — Player-to-player fixed-price listings
- 🔨 **Auctions** — Player-to-player bidding with anti-snipe protection

Plus: Claim Storage, Earnings Withdrawal, My Listings/Auctions, Admin Panel — all GUI-driven.

## ✨ Features

### 🏪 Server Market
- **Every item in the game** available for buy/sell
- Category-based GUI (Blocks, Ores, Farming, Food, Mob Drops, Redstone, Decoration, Tools, Combat, Brewing, Misc)
- Search, pagination, quantity selector (-32/-16/-8/-1 / +1/+8/+16/+32 / MIN/MAX)
- **Large spread** between buy/sell prices (default 75%) to keep the Community Market valuable
- Sell Hand / Sell Inventory quick-sell
- **Durability-based pricing** — worn items sell for less (up to -50%), with Mending/Unbreaking adjustments
- **Enchantment bonus pricing** — enchanted items sell for more (+25% of each enchantment's store buy price)
- **Dedicated Armor Row** in Sell Inventory — equipped gear is visually separated and protected from accidental sale
- Per-item and per-category price overrides in `prices.yml`
- Transaction logging to SQLite

### 💰 Economy: MultiBank Integration
- Uses **MultiBank** as the sole economy provider (no Vault/Essentials)
- All monetary values handled in **cents** (long) for precision
- Fully **async** — economy operations never block the main thread
- Configurable **server shop bank account** for auditable transfers
- Automatic balance re-check at confirm time to prevent exploits

### 🛒 Community Market (Player Listings)
- Create listings with custom prices and durations
- Browse paginated listings with sorting
- Safe atomic purchases to prevent double-buying
- Configurable taxes on sales

### 🔨 Auction System
- Start auctions with minimum bid and optional buyout price
- **Anti-snipe protection** extends auction when bids arrive near the end
- Bid history and automatic outbid notifications
- Safe handling of auction endings and payouts

### 📦 Claim Storage
- Items from expired listings go to claim storage
- Won auction items are safely delivered
- Handles full inventories gracefully

### 💰 Earnings Management
- Pending earnings from sales accumulate
- Withdraw all earnings at once
- Complete transaction history

### 🔐 Admin Features (GUI-based)
- View all listings and auctions
- Remove any listing or cancel auctions
- Force-end auctions
- Reload configuration in-game

### 🎮 Hub GUI Flow
`/market` opens the Market Hub with:
1. **Server Market** — Browse the server shop categories
2. **Community Market** — Browse player listings
3. **Auctions** — Browse player auctions
4. **Create Listing / Create Auction** — Quick access
5. **Claim Items / Earnings** — With live counters
6. **My Listings / My Auctions** — Manage your stuff
7. **Help / Admin** — Info and admin tools

## 📋 Requirements

| Requirement | Version |
|-------------|---------|
| **Server** | Paper 1.21.11+ (or Purpur, Folia-compatible forks) |
| **Java** | Java 21+ |
| **Economy** | [MultiBank](https://github.com/henrique/MultiBank) |

### Economy Setup
Market uses **MultiBank** for all economy operations:

1. Install [MultiBank](https://github.com/henrique/MultiBank) on your server
2. Create a server shop bank account (default ID: `server-shop`)
3. Set the account ID in `config.yml`:
   ```yaml
   economy:
     server-account-id: "server-shop"
   ```
4. Start/restart the server

All server shop transactions (buy/sell) transfer money to/from this dedicated account, providing a full audit trail.

## 📥 Installation

1. Download the latest `market-x.x.x.jar` from [Modrinth](https://modrinth.com/plugin/market)
2. Place it in your server's `plugins/` folder
3. Ensure you have an economy system installed (see above)
4. Start or restart your server
5. Edit `plugins/Market/config.yml` and `plugins/Market/prices.yml` as needed
6. Use `/market` to open everything!

### Upgrading from CommunityMarket
If you previously used CommunityMarket, the plugin will **automatically migrate** your data folder (`plugins/CommunityMarket/` → `plugins/Market/`). Your database, configs, and language files are preserved.

### Migrating from Vault/Essentials
If you previously used Market with Vault or EssentialsX:
1. Install MultiBank and set up player accounts
2. Remove Vault dependency from your server (if no other plugins need it)
3. Update `config.yml` with the `economy.server-account-id` setting
4. Restart the server

## 🎮 Commands

| Command | Aliases | Description | Permission |
|---------|---------|-------------|------------|
| `/market` | `/m`, `/shop`, `/cmarket`, `/servershop` | Opens the Market Hub GUI | `market.use` |
| `/market reload` | — | Reloads all configuration and prices | `market.admin.reload` |

**That's it!** Everything else is done through GUIs.

## 🔒 Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `market.*` | All permissions | op |
| `market.use` | Access the market hub GUI | everyone |
| `market.server` | Access the server shop | everyone |
| `market.community` | Access the community market | everyone |
| `market.auctions` | Access auctions | everyone |
| `market.sell` | Create fixed-price listings | everyone |
| `market.auction` | Create auctions | everyone |
| `market.buy` | Purchase from the community market | everyone |
| `market.bid` | Bid on auctions | everyone |
| `market.claim` | Claim items from storage | everyone |
| `market.withdraw` | Withdraw earnings | everyone |
| `market.admin` | Access admin functions | op |
| `market.admin.viewall` | View all listings/auctions | op |
| `market.admin.remove` | Remove any listing/auction | op |
| `market.admin.reload` | Reload configuration & prices | op |

## ⚙️ Configuration

### config.yml Highlights

```yaml
# Language setting (available: en_US, pt_PT)
language: en_US

# Database: sqlite (default) or mysql
database:
  type: sqlite

# Economy settings (MultiBank)
economy:
  currency-format: "$#,##0.00"
  server-account-id: "server-shop"  # MultiBank account for server shop
  taxes:
    market-tax: 5.0
    auction-tax: 7.5

# Server Shop settings
server-shop:
  default-sell-multiplier: 0.25  # 75% spread
  sell-tax: 0.0
  full-inventory-action: drop
  max-transaction-quantity: 2304
  include-special-items: false

# Sell Inventory safety
sell-inventory:
  allow-equipped-selling: false  # Prevents accidental armor selling

# Quantity selector — pack (stack) buttons
gui:
  quantity:
    pack-selectors:
      enabled: true   # Show pack buttons for stackable items
      max-packs: 4    # Number of pack buttons per side (1–4)
```

### Server Shop Pricing (`prices.yml`)

The server shop uses a **large spread** model:

```
Buy Price  = what players PAY to buy from the server
Sell Price = what players RECEIVE when selling to the server
Spread     = (buy - sell) / buy × 100%
```

**Default:** `sell price = buy price × 0.25` → **75% spread**.

A Diamond costs $1,000 to buy, but only $250 when selling back. Players prefer selling to each other on the Community Market!

**Configuration hierarchy:**
1. Per-item override in `prices.yml` → `overrides:`
2. Category default → `default-prices:` + category `sell-multiplier:`
3. Global default → `config.yml` → `server-shop.default-sell-multiplier`

```yaml
# Example overrides in prices.yml:
overrides:
  DIAMOND:
    buy-price: 1000.0
  ELYTRA:
    buy-price: 50000.0
    sell-enabled: false
  BEDROCK:
    buy-enabled: false
    sell-enabled: false
```

### Enchantment Pricing

When selling enchanted items to the Server Market, each enchantment adds a bonus:

```
Bonus per enchantment = Store Buy Price of that enchantment × 25%
```

Configure enchantment buy prices in `prices.yml`:

```yaml
enchant-prices:
  SHARPNESS:
    perLevelBuyCents:
      1: 100000    # $1,000.00
      2: 200000    # $2,000.00
      3: 350000
      4: 600000
      5: 1000000   # $10,000.00
  MENDING:
    buyCents: 2500000  # $25,000.00 (flat, any level)
  UNBREAKING:
    perLevelBuyCents:
      1: 80000
      2: 160000
      3: 300000
```

**Example:** A Diamond Sword with Sharpness V and Unbreaking III:
- Base sell price: $250.00 (from config)
- Sharpness V bonus: $10,000 × 25% = +$2,500.00
- Unbreaking III bonus: $3,000 × 25% = +$750.00
- Total before wear: $3,500.00

### Durability-Based Sell Pricing

Items with durability (tools, armor, weapons) have their sell price reduced based on wear:

```
wearRatio = currentDamage / maxDamage
effectiveWear = wearRatio / (UnbreakingLevel + 1)   ← Unbreaking reduces penalty
reduction = effectiveWear × 0.50
finalMultiplier = max(0.50, 1.0 - reduction)         ← Min 50% of base value
```

**Special rules:**
- **Mending** items: durability penalty is **completely waived** (full value regardless of wear)
- **Unbreaking** items: effective wear is divided by `(level + 1)`, reflecting that visible damage represents less real usage
- The minimum payout is always **50% of the base sell value** (including enchant bonuses)

**Example:** A half-damaged Diamond Pickaxe (Eff V, Unbreaking III):
- wearRatio = 0.5
- effectiveWear = 0.5 / (3+1) = 0.125
- reduction = 0.125 × 0.50 = 0.0625 → 6.25% penalty
- Player receives 93.75% of full value

### Sell Inventory — Equipped Gear Safety

The Sell Inventory GUI has a dedicated top row for equipped gear:

| Slot | Content |
|------|---------|
| 0 | Helmet |
| 1 | Chestplate |
| 2 | Leggings |
| 3 | Boots |
| 4 | Offhand/Shield |
| 5–8 | Yellow label filler |

**Default behavior** (`allow-equipped-selling: false`):
- Clicking equipped items shows a warning and does nothing
- Players must unequip gear before selling

**Optional** (`allow-equipped-selling: true`):
- Equipped items can be sold but require a 2-step click confirmation
- Shift-clicking is always blocked to prevent accidents

### Pack Selectors (Stack Buttons)

The item detail quantity selector includes **pack buttons** that let players adjust quantity by full stacks at once:

| Button | Effect |
|--------|--------|
| -4 Stack / +4 Stack | ±256 items (for 64-stack items) |
| -3 Stack / +3 Stack | ±192 items |
| -2 Stack / +2 Stack | ±128 items |
| -1 Stack / +1 Stack | ±64 items |

**Stack size is per-item:**
- Most blocks/items: 1 stack = **64** items
- Ender pearls, snowballs, eggs: 1 stack = **16** items
- Non-stackable items (swords, armor, etc.): pack buttons are **hidden**

**Configuration:**
```yaml
gui:
  quantity:
    pack-selectors:
      enabled: true   # Set to false to hide pack buttons entirely
      max-packs: 4    # Number of pack buttons per side (1–4)
```

**Safety:**
- Pack buttons respect all existing bounds (max transaction quantity, max affordable, max inventory)
- Quantity is always clamped to valid range after any pack button press
- Affordability and availability are always re-checked at confirmation time

### Available Languages
- `en_US` - English (default)
- `pt_PT` - Portuguese

Language files are in `plugins/Market/lang/` and can be customized.

## 🔧 Troubleshooting

### "No compatible economy provider found!"

This error appears when Market cannot find an economy system.

**Solutions:**
1. **Install Vault + an economy plugin:**
   - Download [Vault]([https://www.spigotmc.org/resources/vault.34315/](https://modrinth.com/plugin/vault-2.0-economy-plugins))
   - Download [EssentialsX](https://essentialsx.net/) or another economy plugin
   - Restart your server

2. **Using EssentialsX without Vault:**
   - Make sure EssentialsX is installed and enabled
   - Check that `economy` is not disabled in EssentialsX's `config.yml`

### "MultiBank not found!"

This error appears when Market cannot find the MultiBank plugin.

**Solutions:**
1. Install [MultiBank](https://github.com/henrique/MultiBank) on your server
2. Ensure MultiBank is enabled and loads before Market
3. Check that MultiBank's API service is registered

### Items stuck in claim storage

If a player's inventory was full when they purchased an item or won an auction, the item goes to their claim storage. They can retrieve it from the main menu → "Claim Items".

### Auctions not ending

Auctions are checked periodically (every 5 seconds by default). If auctions seem stuck:
- Check server console for errors
- Verify the database is working properly
- Try `/market admin` → View Auctions to see auction status

### Database issues

By default, Market uses SQLite (no setup required). For larger servers, MySQL is recommended:

```yaml
database:
  type: mysql
  mysql:
    host: localhost
    port: 3306
    database: communitymarket
    username: your_user
    password: your_password
```

## 📊 API

Market does not currently provide a public API. If you need integration capabilities, please open an issue on GitHub.

## 🤝 Support

- **Issues & Bug Reports:** [GitHub Issues](https://github.com/henrique/Market/issues)
- **Feature Requests:** [GitHub Issues](https://github.com/henrique/Market/issues)

## 📜 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Credits

- Built for the Paper/Spigot community
- Uses [MultiBank](https://github.com/henrique/MultiBank) for economy integration
- Uses [HikariCP](https://github.com/brettwooldridge/HikariCP) for database connection pooling

