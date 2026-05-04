import java.sql.Connection;
import java.sql.PreparedStatement;

public class InsertProduct {
    public static void main(String[] args) {
        try (Connection con = DatabaseConnection.getConnection()) {
            String sql = "INSERT INTO Products (ProductName, UnitPrice, Quantity, Category, Barcode) VALUES (?, ?, ?, ?, ?)";
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setString(1, "Pepsi 250ml");
            ps.setDouble(2, 12.5);
            ps.setInt(3, 100);
            ps.setString(4, "Drinks");
            ps.setString(5, "QR12345");
            ps.executeUpdate();

            System.out.println("✅ Product inserted successfully!");
        } catch (Exception e) {
            System.out.println("❌ Error: " + e.getMessage());
        }
    }
}
