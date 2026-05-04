import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class ReturnHistoryForm extends Application {

    private TableView<ReturnRecord> table;
    private TextField txtInvoiceSearch;

    @Override
    public void start(Stage stage) {
        AppIcon.apply(stage);
        Label lblTitle = new Label("📋 سجل المرتجعات");

        txtInvoiceSearch = new TextField();
        txtInvoiceSearch.setPromptText("ابحث برقم الفاتورة");
        txtInvoiceSearch.textProperty().addListener((obs, oldVal, newVal) -> loadReturns());

        table = new TableView<>();

        TableColumn<ReturnRecord, Integer> colId = new TableColumn<>("ID");
        colId.setCellValueFactory(c -> c.getValue().returnIdProperty().asObject());

        TableColumn<ReturnRecord, Integer> colInvoice = new TableColumn<>("رقم الفاتورة");
        colInvoice.setCellValueFactory(c -> c.getValue().invoiceIdProperty().asObject());

        TableColumn<ReturnRecord, String> colCustomerName = new TableColumn<>("اسم العميل");
        colCustomerName.setCellValueFactory(c -> c.getValue().customerNameProperty());

        TableColumn<ReturnRecord, String> colCustomerPhone = new TableColumn<>("رقم العميل");
        colCustomerPhone.setCellValueFactory(c -> c.getValue().customerPhoneProperty());

        TableColumn<ReturnRecord, String> colProduct = new TableColumn<>("المنتج");
        colProduct.setCellValueFactory(c -> c.getValue().productNameProperty());

        TableColumn<ReturnRecord, Integer> colQty = new TableColumn<>("الكمية المرتجعة");
        colQty.setCellValueFactory(c -> c.getValue().qtyReturnedProperty().asObject());

        TableColumn<ReturnRecord, String> colDate = new TableColumn<>("تاريخ ووقت الإرجاع");
        colDate.setCellValueFactory(c -> c.getValue().dateReturnedProperty());

        table.getColumns().addAll(colId, colInvoice, colCustomerName, colCustomerPhone, colProduct, colQty, colDate);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        Button btnReload = new Button("🔄 تحديث");
        btnReload.setOnAction(e -> loadReturns());

        HBox searchBar = new HBox(10, new Label("رقم الفاتورة:"), txtInvoiceSearch, btnReload);
        VBox root = new VBox(15, lblTitle, searchBar, table);
        root.setPadding(new Insets(15));

        loadReturns();

        Scene scene = new Scene(root, 1100, 520);
        stage.setTitle("📋 سجل المرتجعات");
        stage.setScene(scene);
        stage.show();
    }

    private void loadReturns() {
        table.getItems().clear();

        String invoiceFilter = txtInvoiceSearch == null ? "" : txtInvoiceSearch.getText().trim();
        boolean hasInvoiceFilter = !invoiceFilter.isEmpty();

        String sql = """
            SELECT 
                R.ReturnID, 
                R.InvoiceID,
                COALESCE(I.CustomerName, '') AS CustomerName,
                COALESCE(I.CustomerPhone, '') AS CustomerPhone,
                P.ProductName, 
                R.QuantityReturned,
                CASE
                    WHEN R.DateReturned <> '' AND R.DateReturned NOT GLOB '*[^0-9]*' THEN datetime(CAST(R.DateReturned AS INTEGER) / 1000, 'unixepoch', 'localtime')
                    ELSE R.DateReturned
                END AS DateReturned
            FROM Returns R
            JOIN Products P ON R.ProductID = P.ProductID
            LEFT JOIN Invoices I ON I.InvoiceID = R.InvoiceID
            WHERE (? = '' OR CAST(R.InvoiceID AS TEXT) LIKE ?)
            ORDER BY
                CASE
                    WHEN R.DateReturned <> '' AND R.DateReturned NOT GLOB '*[^0-9]*' THEN CAST(R.DateReturned AS INTEGER)
                    ELSE strftime('%s', R.DateReturned) * 1000
                END DESC
            """;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, invoiceFilter);
            ps.setString(2, hasInvoiceFilter ? "%" + invoiceFilter + "%" : "");

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    table.getItems().add(new ReturnRecord(
                            rs.getInt("ReturnID"),
                            rs.getInt("InvoiceID"),
                            rs.getString("ProductName"),
                            rs.getInt("QuantityReturned"),
                            rs.getString("DateReturned"),
                            rs.getString("CustomerName"),
                            rs.getString("CustomerPhone")
                    ));
                }
            }

        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "⚠️ خطأ أثناء تحميل البيانات: " + ex.getMessage()).showAndWait();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
