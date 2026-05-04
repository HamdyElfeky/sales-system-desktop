import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class Main {
    public static void main(String[] args) {
        SalesForm.main(args);
    }
}

class TestDBConnection {
    public static void main(String[] args) {
        try (Connection con = DatabaseConnection.getConnection()) {
            System.out.println("Connected successfully to SalesSystem!");

            String query = "SELECT ProductID, ProductName, UnitPrice, Quantity FROM Products";
            Statement stmt = con.createStatement();
            ResultSet rs = stmt.executeQuery(query);

            System.out.println("\nProducts List:");
            System.out.println("------------------------------");
            while (rs.next()) {
                System.out.println(
                        rs.getInt("ProductID") + " | " +
                                rs.getString("ProductName") + " | " +
                                rs.getDouble("UnitPrice") + " EGP | Qty: " +
                                rs.getInt("Quantity")
                );
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
