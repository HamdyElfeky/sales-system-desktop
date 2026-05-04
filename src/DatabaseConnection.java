import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class DatabaseConnection {
    private static final String DB_FILE = "sales.db";
    private static final String URL = "jdbc:sqlite:" + DB_FILE;
    private static final DateTimeFormatter DB_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String INVOICE_DATE_LOCAL_MIGRATION_KEY = "invoice-date-local-v1";

    static {
        // Initialize database and create tables if not exist
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // Enable foreign keys (SQLite requires this explicitly)
            stmt.execute("PRAGMA foreign_keys = ON;");

            // Create Products table
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS Products (
                    ProductID INTEGER PRIMARY KEY AUTOINCREMENT,
                    Barcode TEXT UNIQUE,
                    Category TEXT,
                    ProductName TEXT NOT NULL,
                    Quantity INTEGER,
                    UnitPrice REAL NOT NULL
                )
            """);

            // Create Invoices table
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS Invoices (
                    InvoiceID INTEGER PRIMARY KEY AUTOINCREMENT,
                    InvoiceDate TEXT NOT NULL,
                    InvoiceTotal REAL NOT NULL,
                    CustomerName TEXT,
                    CustomerPhone TEXT
                )
            """);

            ensureColumnExists(conn, "Invoices", "CustomerName", "TEXT");
            ensureColumnExists(conn, "Invoices", "CustomerPhone", "TEXT");

            // Create InvoiceItems table
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS InvoiceItems (
                    ItemID INTEGER PRIMARY KEY AUTOINCREMENT,
                    InvoiceID INTEGER,
                    ProductName TEXT,
                    Quantity INTEGER,
                    UnitPrice REAL,
                    FOREIGN KEY(InvoiceID) REFERENCES Invoices(InvoiceID)
                )
            """);

            // Create Returns table
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS Returns (
                    ReturnID INTEGER PRIMARY KEY AUTOINCREMENT,
                    DateReturned TEXT,
                    InvoiceID INTEGER,
                    ProductID INTEGER,
                    QuantityReturned INTEGER,
                    FOREIGN KEY(InvoiceID) REFERENCES Invoices(InvoiceID),
                    FOREIGN KEY(ProductID) REFERENCES Products(ProductID)
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS BarcodeLookupCache (
                    Barcode TEXT PRIMARY KEY,
                    ProductName TEXT NOT NULL,
                    Category TEXT,
                    LastUpdated TEXT DEFAULT CURRENT_TIMESTAMP
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS AppMeta (
                    MetaKey TEXT PRIMARY KEY,
                    MetaValue TEXT NOT NULL
                )
            """);

            migrateLegacyInvoiceDatesToLocalTime(conn);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    public static String currentDateTimeText() {
        return LocalDateTime.now().format(DB_DATE_TIME_FORMATTER);
    }

    private static void ensureColumnExists(Connection conn, String tableName, String columnName, String columnDefinition) throws SQLException {
        String pragmaSql = "PRAGMA table_info(" + tableName + ")";
        try (PreparedStatement ps = conn.prepareStatement(pragmaSql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                    return;
                }
            }
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition);
        }
    }

    private static void migrateLegacyInvoiceDatesToLocalTime(Connection conn) throws SQLException {
        if (isMigrationApplied(conn, INVOICE_DATE_LOCAL_MIGRATION_KEY)) {
            return;
        }

        try (PreparedStatement select = conn.prepareStatement("SELECT InvoiceID, InvoiceDate FROM Invoices");
             ResultSet rs = select.executeQuery();
             PreparedStatement update = conn.prepareStatement("UPDATE Invoices SET InvoiceDate = ? WHERE InvoiceID = ?")) {

            while (rs.next()) {
                String rawValue = rs.getString("InvoiceDate");
                if (rawValue == null || rawValue.isBlank() || rawValue.chars().allMatch(Character::isDigit)) {
                    continue;
                }

                try {
                    LocalDateTime utcDateTime = LocalDateTime.parse(rawValue.trim(), DB_DATE_TIME_FORMATTER);
                    String localValue = utcDateTime
                            .atZone(ZoneOffset.UTC)
                            .withZoneSameInstant(ZoneId.systemDefault())
                            .toLocalDateTime()
                            .format(DB_DATE_TIME_FORMATTER);

                    if (!localValue.equals(rawValue)) {
                        update.setString(1, localValue);
                        update.setInt(2, rs.getInt("InvoiceID"));
                        update.addBatch();
                    }
                } catch (DateTimeParseException ignored) {
                    // Leave non-standard legacy values untouched.
                }
            }

            update.executeBatch();
        }

        markMigrationApplied(conn, INVOICE_DATE_LOCAL_MIGRATION_KEY);
    }

    private static boolean isMigrationApplied(Connection conn, String migrationKey) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM AppMeta WHERE MetaKey = ?")) {
            ps.setString(1, migrationKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void markMigrationApplied(Connection conn, String migrationKey) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO AppMeta (MetaKey, MetaValue) VALUES (?, ?)")) {
            ps.setString(1, migrationKey);
            ps.setString(2, currentDateTimeText());
            ps.executeUpdate();
        }
    }
}
