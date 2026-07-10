package pt.henrique.market.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.inventory.ItemStack;
import pt.henrique.market.Market;
import pt.henrique.market.model.*;
import pt.henrique.market.util.ItemSerializer;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Manages database connections and operations.
 * Supports SQLite (default) and MySQL.
 * All operations are asynchronous to prevent blocking the main thread.
 */
public class DatabaseManager {

    private final Market plugin;
    private HikariDataSource dataSource;
    private boolean isMySQL;

    // Schema version for migrations
    private static final int SCHEMA_VERSION = 1;

    public DatabaseManager(Market plugin) {
        this.plugin = plugin;
    }

    /**
     * Initializes the database connection pool and creates tables.
     *
     * @return true if successful
     */
    public boolean initialize() {
        try {
            var config = plugin.getConfigManager();
            isMySQL = "mysql".equalsIgnoreCase(config.getDatabaseType());

            HikariConfig hikariConfig = new HikariConfig();

            if (isMySQL) {
                // MySQL configuration
                hikariConfig.setJdbcUrl("jdbc:mysql://" + config.getMysqlHost() + ":" +
                        config.getMysqlPort() + "/" + config.getMysqlDatabase() +
                        "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8");
                hikariConfig.setUsername(config.getMysqlUsername());
                hikariConfig.setPassword(config.getMysqlPassword());
                hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
            } else {
                // SQLite configuration
                File dataFolder = plugin.getDataFolder();
                if (!dataFolder.exists()) {
                    dataFolder.mkdirs();
                }
                File dbFile = new File(dataFolder, config.getSqliteFile());
                hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
                hikariConfig.setDriverClassName("org.sqlite.JDBC");
            }

            // Connection pool settings
            hikariConfig.setMaximumPoolSize(config.getPoolMaxSize());
            hikariConfig.setMinimumIdle(config.getPoolMinIdle());
            hikariConfig.setConnectionTimeout(config.getPoolConnectionTimeout());
            hikariConfig.setIdleTimeout(config.getPoolIdleTimeout());
            hikariConfig.setMaxLifetime(config.getPoolMaxLifetime());
            hikariConfig.setPoolName("Market-Pool");

            dataSource = new HikariDataSource(hikariConfig);

            // Create tables
            createTables();

            plugin.getLogger().info("Database connection established (" +
                    (isMySQL ? "MySQL" : "SQLite") + ")");
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize database", e);
            return false;
        }
    }

    /**
     * Shuts down the database connection pool.
     */
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Database connection closed.");
        }
    }

    /**
     * Gets a connection from the pool.
     */
    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Creates all database tables.
     */
    private void createTables() throws SQLException {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {

            // Listings table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS listings (
                    id INTEGER PRIMARY KEY %s,
                    seller_uuid VARCHAR(36) NOT NULL,
                    seller_name VARCHAR(16) NOT NULL,
                    item_data TEXT NOT NULL,
                    amount INTEGER NOT NULL,
                    price DOUBLE NOT NULL,
                    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                    created_at BIGINT NOT NULL,
                    expires_at BIGINT,
                    buyer_uuid VARCHAR(36),
                    buyer_name VARCHAR(16),
                    sold_at BIGINT
                )
                """.formatted(isMySQL ? "AUTO_INCREMENT" : "AUTOINCREMENT"));

            // Auctions table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS auctions (
                    id INTEGER PRIMARY KEY %s,
                    seller_uuid VARCHAR(36) NOT NULL,
                    seller_name VARCHAR(16) NOT NULL,
                    item_data TEXT NOT NULL,
                    start_price DOUBLE NOT NULL,
                    current_bid DOUBLE NOT NULL DEFAULT 0,
                    highest_bidder_uuid VARCHAR(36),
                    highest_bidder_name VARCHAR(16),
                    bid_count INTEGER NOT NULL DEFAULT 0,
                    buyout_price DOUBLE,
                    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                    created_at BIGINT NOT NULL,
                    ends_at BIGINT NOT NULL,
                    extension_count INTEGER NOT NULL DEFAULT 0
                )
                """.formatted(isMySQL ? "AUTO_INCREMENT" : "AUTOINCREMENT"));

            // Bids table (bid history)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS bids (
                    id INTEGER PRIMARY KEY %s,
                    auction_id INTEGER NOT NULL,
                    bidder_uuid VARCHAR(36) NOT NULL,
                    bidder_name VARCHAR(16) NOT NULL,
                    amount DOUBLE NOT NULL,
                    created_at BIGINT NOT NULL,
                    FOREIGN KEY (auction_id) REFERENCES auctions(id)
                )
                """.formatted(isMySQL ? "AUTO_INCREMENT" : "AUTOINCREMENT"));

            // Claim storage table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS claim_storage (
                    id INTEGER PRIMARY KEY %s,
                    player_uuid VARCHAR(36) NOT NULL,
                    item_data TEXT NOT NULL,
                    reason VARCHAR(50) NOT NULL,
                    source_info VARCHAR(100),
                    created_at BIGINT NOT NULL
                )
                """.formatted(isMySQL ? "AUTO_INCREMENT" : "AUTOINCREMENT"));

            // Pending earnings table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS pending_earnings (
                    id INTEGER PRIMARY KEY %s,
                    player_uuid VARCHAR(36) NOT NULL,
                    amount DOUBLE NOT NULL,
                    source VARCHAR(100),
                    created_at BIGINT NOT NULL,
                    withdrawn BOOLEAN NOT NULL DEFAULT FALSE
                )
                """.formatted(isMySQL ? "AUTO_INCREMENT" : "AUTOINCREMENT"));

            // Player data table (for cooldowns, etc.)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_data (
                    player_uuid VARCHAR(36) PRIMARY KEY,
                    last_listing_time BIGINT,
                    preferred_language VARCHAR(10)
                )
                """);

            // Server Shop transactions table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS shop_transactions (
                    id INTEGER PRIMARY KEY %s,
                    player_uuid VARCHAR(36) NOT NULL,
                    player_name VARCHAR(16) NOT NULL,
                    material VARCHAR(64) NOT NULL,
                    amount INTEGER NOT NULL,
                    price_per_unit DOUBLE NOT NULL,
                    total_price DOUBLE NOT NULL,
                    tax DOUBLE NOT NULL DEFAULT 0,
                    type VARCHAR(4) NOT NULL,
                    timestamp BIGINT NOT NULL
                )
                """.formatted(isMySQL ? "AUTO_INCREMENT" : "AUTOINCREMENT"));

            // Sale notifications table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS sale_notifications (
                    id INTEGER PRIMARY KEY %s,
                    seller_uuid VARCHAR(36) NOT NULL,
                    item_name TEXT NOT NULL,
                    formatted_price TEXT NOT NULL,
                    buyer_name VARCHAR(16) NOT NULL,
                    created_at BIGINT NOT NULL
                )
                """.formatted(isMySQL ? "AUTO_INCREMENT" : "AUTOINCREMENT"));

            // Create indexes for performance
            try {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_listings_seller ON listings(seller_uuid)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_listings_status ON listings(status)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_auctions_seller ON auctions(seller_uuid)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_auctions_status ON auctions(status)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_claim_player ON claim_storage(player_uuid)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_earnings_player ON pending_earnings(player_uuid)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_sale_notif_player ON sale_notifications(seller_uuid)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_shop_tx_player ON shop_transactions(player_uuid)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_shop_tx_material ON shop_transactions(material)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_shop_tx_timestamp ON shop_transactions(timestamp)");
            } catch (SQLException e) {
                // Indexes might already exist, ignore
            }
        }
    }

    // ==================== LISTING OPERATIONS ====================

    /**
     * Creates a new listing and returns its ID.
     */
    public CompletableFuture<Integer> createListing(Listing listing) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                INSERT INTO listings (seller_uuid, seller_name, item_data, amount, price, status, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                stmt.setString(1, listing.getSellerUuid().toString());
                stmt.setString(2, listing.getSellerName());
                stmt.setString(3, ItemSerializer.serialize(listing.getItem()));
                stmt.setInt(4, listing.getAmount());
                stmt.setDouble(5, listing.getPrice());
                stmt.setString(6, listing.getStatus().name());
                stmt.setLong(7, listing.getCreatedAt().toEpochMilli());
                stmt.setLong(8, listing.getExpiresAt() != null ? listing.getExpiresAt().toEpochMilli() : 0);

                stmt.executeUpdate();

                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to create listing", e);
            }
            return -1;
        });
    }

    /**
     * Gets all active listings.
     */
    public CompletableFuture<List<Listing>> getActiveListings() {
        return CompletableFuture.supplyAsync(() -> {
            List<Listing> listings = new ArrayList<>();
            String sql = "SELECT * FROM listings WHERE status = 'ACTIVE' ORDER BY created_at DESC";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    Listing listing = mapListing(rs);
                    if (listing != null) {
                        listings.add(listing);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to get active listings", e);
            }
            return listings;
        });
    }

    /**
     * Gets a listing by ID.
     */
    public CompletableFuture<Optional<Listing>> getListing(int id) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM listings WHERE id = ?";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, id);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.ofNullable(mapListing(rs));
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to get listing", e);
            }
            return Optional.empty();
        });
    }

    /**
     * Gets all listings for a player.
     */
    public CompletableFuture<List<Listing>> getPlayerListings(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<Listing> listings = new ArrayList<>();
            String sql = "SELECT * FROM listings WHERE seller_uuid = ? AND status = 'ACTIVE' ORDER BY created_at DESC";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Listing listing = mapListing(rs);
                        if (listing != null) {
                            listings.add(listing);
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to get player listings", e);
            }
            return listings;
        });
    }

    /**
     * Counts active listings for a player.
     */
    public CompletableFuture<Integer> countPlayerListings(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM listings WHERE seller_uuid = ? AND status = 'ACTIVE'";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to count player listings", e);
            }
            return 0;
        });
    }

    /**
     * Atomically purchases a listing.
     * Returns true if successful (listing was still available).
     */
    public CompletableFuture<Boolean> purchaseListing(int listingId, UUID buyerUuid, String buyerName) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE listings SET status = 'SOLD', buyer_uuid = ?, buyer_name = ?, sold_at = ? " +
                         "WHERE id = ? AND status = 'ACTIVE'";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, buyerUuid.toString());
                stmt.setString(2, buyerName);
                stmt.setLong(3, Instant.now().toEpochMilli());
                stmt.setInt(4, listingId);

                int updated = stmt.executeUpdate();
                return updated > 0;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to purchase listing", e);
            }
            return false;
        });
    }

    /**
     * Updates a listing's status.
     */
    public CompletableFuture<Boolean> updateListingStatus(int listingId, Listing.ListingStatus status) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE listings SET status = ? WHERE id = ?";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, status.name());
                stmt.setInt(2, listingId);

                return stmt.executeUpdate() > 0;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to update listing status", e);
            }
            return false;
        });
    }

    /**
     * Atomically transitions a listing away from ACTIVE to the given status.
     * Returns true ONLY if this call performed the transition (i.e. the listing
     * was still ACTIVE). This is the anti-dupe gate for cancel/expire flows:
     * concurrent cancels, or a cancel racing a purchase, will only succeed once,
     * so the item is moved to claim storage at most once.
     */
    public CompletableFuture<Boolean> transitionListingFromActive(int listingId, Listing.ListingStatus newStatus) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE listings SET status = ? WHERE id = ? AND status = 'ACTIVE'";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, newStatus.name());
                stmt.setInt(2, listingId);

                return stmt.executeUpdate() > 0;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to transition listing status", e);
            }
            return false;
        });
    }

    /**
     * Gets expired active listings.
     */
    public CompletableFuture<List<Listing>> getExpiredListings() {
        return CompletableFuture.supplyAsync(() -> {
            List<Listing> listings = new ArrayList<>();
            String sql = "SELECT * FROM listings WHERE status = 'ACTIVE' AND expires_at > 0 AND expires_at < ?";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setLong(1, Instant.now().toEpochMilli());

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Listing listing = mapListing(rs);
                        if (listing != null) {
                            listings.add(listing);
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to get expired listings", e);
            }
            return listings;
        });
    }

    private Listing mapListing(ResultSet rs) throws SQLException {
        try {
            Listing listing = new Listing();
            listing.setId(rs.getInt("id"));
            listing.setSellerUuid(UUID.fromString(rs.getString("seller_uuid")));
            listing.setSellerName(rs.getString("seller_name"));
            listing.setItem(ItemSerializer.deserialize(rs.getString("item_data")));
            listing.setAmount(rs.getInt("amount"));
            listing.setPrice(rs.getDouble("price"));
            listing.setStatus(Listing.ListingStatus.valueOf(rs.getString("status")));
            listing.setCreatedAt(Instant.ofEpochMilli(rs.getLong("created_at")));
            long expiresAt = rs.getLong("expires_at");
            if (expiresAt > 0) {
                listing.setExpiresAt(Instant.ofEpochMilli(expiresAt));
            }
            return listing;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to map listing", e);
            return null;
        }
    }

    // ==================== AUCTION OPERATIONS ====================

    /**
     * Creates a new auction and returns its ID.
     */
    public CompletableFuture<Integer> createAuction(Auction auction) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                INSERT INTO auctions (seller_uuid, seller_name, item_data, start_price, current_bid, 
                    buyout_price, status, created_at, ends_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                stmt.setString(1, auction.getSellerUuid().toString());
                stmt.setString(2, auction.getSellerName());
                stmt.setString(3, ItemSerializer.serialize(auction.getItem()));
                stmt.setDouble(4, auction.getStartPrice());
                stmt.setDouble(5, 0);
                if (auction.getBuyoutPrice() != null) {
                    stmt.setDouble(6, auction.getBuyoutPrice());
                } else {
                    stmt.setNull(6, Types.DOUBLE);
                }
                stmt.setString(7, auction.getStatus().name());
                stmt.setLong(8, auction.getCreatedAt().toEpochMilli());
                stmt.setLong(9, auction.getEndsAt().toEpochMilli());

                stmt.executeUpdate();

                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to create auction", e);
            }
            return -1;
        });
    }

    /**
     * Gets all active auctions.
     */
    public CompletableFuture<List<Auction>> getActiveAuctions() {
        return CompletableFuture.supplyAsync(() -> {
            List<Auction> auctions = new ArrayList<>();
            String sql = "SELECT * FROM auctions WHERE status = 'ACTIVE' ORDER BY ends_at ASC";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    Auction auction = mapAuction(rs);
                    if (auction != null) {
                        auctions.add(auction);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to get active auctions", e);
            }
            return auctions;
        });
    }

    /**
     * Gets an auction by ID.
     */
    public CompletableFuture<Optional<Auction>> getAuction(int id) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM auctions WHERE id = ?";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, id);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.ofNullable(mapAuction(rs));
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to get auction", e);
            }
            return Optional.empty();
        });
    }

    /**
     * Gets all auctions for a player.
     */
    public CompletableFuture<List<Auction>> getPlayerAuctions(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<Auction> auctions = new ArrayList<>();
            String sql = "SELECT * FROM auctions WHERE seller_uuid = ? AND status = 'ACTIVE' ORDER BY ends_at ASC";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Auction auction = mapAuction(rs);
                        if (auction != null) {
                            auctions.add(auction);
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to get player auctions", e);
            }
            return auctions;
        });
    }

    /**
     * Counts active auctions for a player.
     */
    public CompletableFuture<Integer> countPlayerAuctions(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM auctions WHERE seller_uuid = ? AND status = 'ACTIVE'";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to count player auctions", e);
            }
            return 0;
        });
    }

    /**
     * Places a bid on an auction.
     */
    public CompletableFuture<Boolean> placeBid(int auctionId, UUID bidderUuid, String bidderName, double amount) {
        return CompletableFuture.supplyAsync(() -> {
            String updateSql = """
                UPDATE auctions SET current_bid = ?, highest_bidder_uuid = ?, highest_bidder_name = ?, 
                    bid_count = bid_count + 1
                WHERE id = ? AND status = 'ACTIVE' AND current_bid < ?
                """;

            String insertBidSql = """
                INSERT INTO bids (auction_id, bidder_uuid, bidder_name, amount, created_at)
                VALUES (?, ?, ?, ?, ?)
                """;

            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);

                try (PreparedStatement updateStmt = conn.prepareStatement(updateSql);
                     PreparedStatement insertStmt = conn.prepareStatement(insertBidSql)) {

                    updateStmt.setDouble(1, amount);
                    updateStmt.setString(2, bidderUuid.toString());
                    updateStmt.setString(3, bidderName);
                    updateStmt.setInt(4, auctionId);
                    updateStmt.setDouble(5, amount);

                    int updated = updateStmt.executeUpdate();

                    if (updated > 0) {
                        // Insert bid history
                        insertStmt.setInt(1, auctionId);
                        insertStmt.setString(2, bidderUuid.toString());
                        insertStmt.setString(3, bidderName);
                        insertStmt.setDouble(4, amount);
                        insertStmt.setLong(5, Instant.now().toEpochMilli());
                        insertStmt.executeUpdate();

                        conn.commit();
                        return true;
                    } else {
                        conn.rollback();
                        return false;
                    }
                } catch (Exception e) {
                    conn.rollback();
                    throw e;
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to place bid", e);
            }
            return false;
        });
    }

    /**
     * Updates an auction's status.
     */
    public CompletableFuture<Boolean> updateAuctionStatus(int auctionId, Auction.AuctionStatus status) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE auctions SET status = ? WHERE id = ?";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, status.name());
                stmt.setInt(2, auctionId);

                return stmt.executeUpdate() > 0;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to update auction status", e);
            }
            return false;
        });
    }

    /**
     * Atomically transitions an auction away from ACTIVE to the given status.
     * Returns true ONLY if this call performed the transition (i.e. the auction
     * was still ACTIVE). This is the anti-dupe gate for auction end/cancel:
     * a buyout racing the scheduled end task, or two overlapping task runs, will
     * only settle the auction once — preventing double item delivery and double
     * seller payout.
     */
    public CompletableFuture<Boolean> transitionAuctionFromActive(int auctionId, Auction.AuctionStatus newStatus) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE auctions SET status = ? WHERE id = ? AND status = 'ACTIVE'";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, newStatus.name());
                stmt.setInt(2, auctionId);

                return stmt.executeUpdate() > 0;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to transition auction status", e);
            }
            return false;
        });
    }

    /**
     * Gets auctions that have ended but are still active.
     */
    public CompletableFuture<List<Auction>> getEndedAuctions() {
        return CompletableFuture.supplyAsync(() -> {
            List<Auction> auctions = new ArrayList<>();
            String sql = "SELECT * FROM auctions WHERE status = 'ACTIVE' AND ends_at < ?";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setLong(1, Instant.now().toEpochMilli());

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Auction auction = mapAuction(rs);
                        if (auction != null) {
                            auctions.add(auction);
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to get ended auctions", e);
            }
            return auctions;
        });
    }

    private Auction mapAuction(ResultSet rs) throws SQLException {
        try {
            Auction auction = new Auction();
            auction.setId(rs.getInt("id"));
            auction.setSellerUuid(UUID.fromString(rs.getString("seller_uuid")));
            auction.setSellerName(rs.getString("seller_name"));
            auction.setItem(ItemSerializer.deserialize(rs.getString("item_data")));
            auction.setStartPrice(rs.getDouble("start_price"));
            auction.setCurrentBid(rs.getDouble("current_bid"));

            String highestBidder = rs.getString("highest_bidder_uuid");
            if (highestBidder != null) {
                auction.setHighestBidderUuid(UUID.fromString(highestBidder));
                auction.setHighestBidderName(rs.getString("highest_bidder_name"));
            }

            auction.setBidCount(rs.getInt("bid_count"));

            double buyout = rs.getDouble("buyout_price");
            if (!rs.wasNull()) {
                auction.setBuyoutPrice(buyout);
            }

            auction.setStatus(Auction.AuctionStatus.valueOf(rs.getString("status")));
            auction.setCreatedAt(Instant.ofEpochMilli(rs.getLong("created_at")));
            auction.setEndsAt(Instant.ofEpochMilli(rs.getLong("ends_at")));
            auction.setExtensionCount(rs.getInt("extension_count"));

            return auction;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to map auction", e);
            return null;
        }
    }

    // ==================== CLAIM STORAGE OPERATIONS ====================

    /**
     * Adds an item to claim storage.
     */
    public CompletableFuture<Integer> addClaimItem(ClaimItem claimItem) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                INSERT INTO claim_storage (player_uuid, item_data, reason, source_info, created_at)
                VALUES (?, ?, ?, ?, ?)
                """;

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                stmt.setString(1, claimItem.getPlayerUuid().toString());
                stmt.setString(2, ItemSerializer.serialize(claimItem.getItem()));
                stmt.setString(3, claimItem.getReason().name());
                stmt.setString(4, claimItem.getSourceInfo());
                stmt.setLong(5, claimItem.getCreatedAt().toEpochMilli());

                stmt.executeUpdate();

                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to add claim item", e);
            }
            return -1;
        });
    }

    /**
     * Gets all claim items for a player.
     */
    public CompletableFuture<List<ClaimItem>> getPlayerClaimItems(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<ClaimItem> items = new ArrayList<>();
            String sql = "SELECT * FROM claim_storage WHERE player_uuid = ? ORDER BY created_at DESC";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        ClaimItem item = mapClaimItem(rs);
                        if (item != null) {
                            items.add(item);
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to get claim items", e);
            }
            return items;
        });
    }

    /**
     * Counts claim items for a player.
     */
    public CompletableFuture<Integer> countPlayerClaimItems(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM claim_storage WHERE player_uuid = ?";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to count claim items", e);
            }
            return 0;
        });
    }

    /**
     * Removes a claim item.
     */
    public CompletableFuture<Boolean> removeClaimItem(int id) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM claim_storage WHERE id = ?";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, id);
                return stmt.executeUpdate() > 0;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to remove claim item", e);
            }
            return false;
        });
    }

    private ClaimItem mapClaimItem(ResultSet rs) throws SQLException {
        try {
            ClaimItem item = new ClaimItem();
            item.setId(rs.getInt("id"));
            item.setPlayerUuid(UUID.fromString(rs.getString("player_uuid")));
            item.setItem(ItemSerializer.deserialize(rs.getString("item_data")));
            item.setReason(ClaimItem.ClaimReason.valueOf(rs.getString("reason")));
            item.setSourceInfo(rs.getString("source_info"));
            item.setCreatedAt(Instant.ofEpochMilli(rs.getLong("created_at")));
            return item;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to map claim item", e);
            return null;
        }
    }

    // ==================== EARNINGS OPERATIONS ====================

    /**
     * Adds pending earnings.
     */
    public CompletableFuture<Integer> addPendingEarnings(PendingEarnings earnings) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                INSERT INTO pending_earnings (player_uuid, amount, source, created_at, withdrawn)
                VALUES (?, ?, ?, ?, ?)
                """;

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                stmt.setString(1, earnings.getPlayerUuid().toString());
                stmt.setDouble(2, earnings.getAmount());
                stmt.setString(3, earnings.getSource());
                stmt.setLong(4, earnings.getCreatedAt().toEpochMilli());
                stmt.setBoolean(5, false);

                stmt.executeUpdate();

                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to add pending earnings", e);
            }
            return -1;
        });
    }

    /**
     * Gets total pending earnings for a player.
     */
    public CompletableFuture<Double> getPlayerPendingEarnings(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT SUM(amount) FROM pending_earnings WHERE player_uuid = ? AND withdrawn = FALSE";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getDouble(1);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to get pending earnings", e);
            }
            return 0.0;
        });
    }

    /**
     * Marks all earnings as withdrawn for a player.
     */
    public CompletableFuture<Boolean> withdrawAllEarnings(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE pending_earnings SET withdrawn = TRUE WHERE player_uuid = ? AND withdrawn = FALSE";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());
                return stmt.executeUpdate() > 0;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to withdraw earnings", e);
            }
            return false;
        });
    }

    // ==================== SALE NOTIFICATION OPERATIONS ====================

    /**
     * Adds a sale notification for an offline seller.
     */
    public CompletableFuture<Integer> addSaleNotification(SaleNotification notification) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                INSERT INTO sale_notifications (seller_uuid, item_name, formatted_price, buyer_name, created_at)
                VALUES (?, ?, ?, ?, ?)
                """;

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                stmt.setString(1, notification.getSellerUuid().toString());
                stmt.setString(2, notification.getItemName());
                stmt.setString(3, notification.getFormattedPrice());
                stmt.setString(4, notification.getBuyerName());
                stmt.setLong(5, notification.getCreatedAt().toEpochMilli());

                stmt.executeUpdate();

                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to add sale notification", e);
            }
            return -1;
        });
    }

    /**
     * Gets all pending sale notifications for a player.
     */
    public CompletableFuture<List<SaleNotification>> getSaleNotifications(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM sale_notifications WHERE seller_uuid = ? ORDER BY created_at ASC";
            List<SaleNotification> notifications = new ArrayList<>();

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        SaleNotification n = new SaleNotification();
                        n.setId(rs.getInt("id"));
                        n.setSellerUuid(UUID.fromString(rs.getString("seller_uuid")));
                        n.setItemName(rs.getString("item_name"));
                        n.setFormattedPrice(rs.getString("formatted_price"));
                        n.setBuyerName(rs.getString("buyer_name"));
                        n.setCreatedAt(Instant.ofEpochMilli(rs.getLong("created_at")));
                        notifications.add(n);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to get sale notifications", e);
            }
            return notifications;
        });
    }

    /**
     * Clears all sale notifications for a player.
     */
    public CompletableFuture<Boolean> clearSaleNotifications(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM sale_notifications WHERE seller_uuid = ?";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());
                return stmt.executeUpdate() >= 0;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to clear sale notifications", e);
            }
            return false;
        });
    }

    // ==================== PLAYER DATA OPERATIONS ====================

    /**
     * Gets the last listing time for a player.
     */
    public CompletableFuture<Optional<Instant>> getLastListingTime(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT last_listing_time FROM player_data WHERE player_uuid = ?";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        long time = rs.getLong("last_listing_time");
                        if (!rs.wasNull() && time > 0) {
                            return Optional.of(Instant.ofEpochMilli(time));
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to get last listing time", e);
            }
            return Optional.empty();
        });
    }

    /**
     * Updates the last listing time for a player.
     */
    public void updateLastListingTime(UUID playerUuid) {
        CompletableFuture.runAsync(() -> {
            String sql = isMySQL
                    ? "INSERT INTO player_data (player_uuid, last_listing_time) VALUES (?, ?) ON DUPLICATE KEY UPDATE last_listing_time = ?"
                    : "INSERT OR REPLACE INTO player_data (player_uuid, last_listing_time) VALUES (?, ?)";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                long now = Instant.now().toEpochMilli();
                stmt.setString(1, playerUuid.toString());
                stmt.setLong(2, now);
                if (isMySQL) {
                    stmt.setLong(3, now);
                }

                stmt.executeUpdate();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to update last listing time", e);
            }
        });
    }

    // ==================== SERVER SHOP OPERATIONS ====================

    /**
     * Logs a server shop transaction asynchronously.
     */
    public void logShopTransaction(pt.henrique.market.servershop.model.ShopTransaction record) {
        CompletableFuture.runAsync(() -> {
            String sql = """
                INSERT INTO shop_transactions (player_uuid, player_name, material, amount,
                    price_per_unit, total_price, tax, type, timestamp)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, record.playerUuid().toString());
                stmt.setString(2, record.playerName());
                stmt.setString(3, record.itemId());
                stmt.setInt(4, record.amount());
                stmt.setDouble(5, record.pricePerUnit());
                stmt.setDouble(6, record.totalPrice());
                stmt.setDouble(7, record.tax());
                stmt.setString(8, record.type().name());
                stmt.setLong(9, record.timestamp());

                stmt.executeUpdate();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to log shop transaction", e);
            }
        });
    }

    /**
     * Sums the net amount (total_price - tax, in cents) a player earned from
     * server-shop SELL transactions since the given epoch millis. Synchronous —
     * used once per player per day to seed the in-memory daily sell cap counter.
     */
    public long getShopSellNetCentsSince(java.util.UUID playerUuid, long sinceMillis) {
        String sql = """
            SELECT COALESCE(SUM(total_price - tax), 0) FROM shop_transactions
            WHERE player_uuid = ? AND type = 'SELL' AND timestamp >= ?
            """;
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setLong(2, sinceMillis);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) return Math.round(rs.getDouble(1) * 100.0);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to read daily sell total", e);
        }
        return 0;
    }
}

