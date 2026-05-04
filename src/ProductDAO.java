import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProductDAO {

    public List<Product> getAllProducts() {
        List<Product> list = new ArrayList<>();
        String sql = "SELECT * FROM Products";

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Integer quantity = rs.getObject("Quantity") == null ? null : rs.getInt("Quantity");
                Product p = new Product(
                        rs.getInt("ProductID"),
                        rs.getString("ProductName"),
                        rs.getDouble("UnitPrice"),
                        quantity,
                        rs.getString("Category"),
                        rs.getString("Barcode")
                );
                list.add(p);
            }

        } catch (Exception e) {
            System.out.println("❌ Error fetching products: " + e.getMessage());
        }
        return list;
    }
}
