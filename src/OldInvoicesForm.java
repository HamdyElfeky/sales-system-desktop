import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class OldInvoicesForm extends Application {

    private ListView<String> lstInvoices;
    private ListView<String> lstItems;
    private Label lblStatus;
    private TextField txtInvoiceSearch;

    @Override
    public void start(Stage stage) {
        AppIcon.apply(stage);
        lstInvoices = new ListView<>();
        lstItems = new ListView<>();
        lblStatus = new Label("جاهز");

        txtInvoiceSearch = new TextField();
        txtInvoiceSearch.setPromptText("ابحث برقم الفاتورة");
        txtInvoiceSearch.textProperty().addListener((obs, oldVal, newVal) -> loadInvoices());

        Button btnRefresh = new Button("🔄 تحديث");
        btnRefresh.setOnAction(e -> loadInvoices());

        Button btnBack = new Button("🔙 الرجوع");
        btnBack.setOnAction(e -> {
            try {
                stage.close();
                new SalesForm().start(new Stage());
            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() ->
                        new Alert(Alert.AlertType.ERROR, "حدث خطأ أثناء الرجوع:\n" + ex.getMessage()).showAndWait());
            }
        });

        Button btnReturn = new Button("↩️ إرجاع منتجات");
        btnReturn.setOnAction(e -> {
            String selected = lstInvoices.getSelectionModel().getSelectedItem();
            if (selected == null) {
                new Alert(Alert.AlertType.WARNING, "⚠️ اختر فاتورة أولاً!").showAndWait();
                return;
            }

            int invoiceId = parseInvoiceId(selected);
            if (invoiceId == -1) {
                new Alert(Alert.AlertType.ERROR, "❌ لم يتم العثور على رقم الفاتورة بشكل صحيح!").showAndWait();
                return;
            }

            try {
                new ReturnForm(invoiceId).start(new Stage());
            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() ->
                        new Alert(Alert.AlertType.ERROR, "حدث خطأ أثناء فتح شاشة الإرجاع:\n" + ex.getMessage()).showAndWait());
            }
        });

        lstInvoices.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                int id = parseInvoiceId(newVal);
                if (id != -1) {
                    loadInvoiceItems(id);
                }
            }
        });

        VBox detailsBox = new VBox(8, lstItems);
        VBox.setMargin(lstItems, new Insets(8, 0, 0, 0));

        HBox listsBox = new HBox(10, lstInvoices, detailsBox);
        HBox.setMargin(lstInvoices, new Insets(0));
        listsBox.setPrefHeight(420);

        HBox topBar = new HBox(10, new Label("رقم الفاتورة:"), txtInvoiceSearch, btnRefresh, btnReturn, btnBack, lblStatus);

        VBox root = new VBox(10, topBar, listsBox);
        root.setPadding(new Insets(10));

        Scene scene = new Scene(root, 980, 540);
        stage.setTitle("📜 فواتير قديمة");
        stage.setScene(scene);
        stage.show();

        loadInvoices();
    }

    private int parseInvoiceId(String invoiceText) {
        try {
            int idx = invoiceText.lastIndexOf("| ID:");
            if (idx >= 0) {
                return Integer.parseInt(invoiceText.substring(idx + 5).trim());
            }
        } catch (Exception e) {
            return -1;
        }
        return -1;
    }

    private void loadInvoices() {
        lblStatus.setText("جارٍ التحميل...");
        lstInvoices.getItems().clear();
        lstItems.getItems().clear();

        String invoiceFilter = txtInvoiceSearch == null ? "" : txtInvoiceSearch.getText().trim();
        boolean hasInvoiceFilter = !invoiceFilter.isEmpty();

        new Thread(() -> {
            List<String> results = new ArrayList<>();
            String sql = """
                    SELECT InvoiceID, InvoiceDate, InvoiceTotal, COALESCE(CustomerName, '') AS CustomerName, COALESCE(CustomerPhone, '') AS CustomerPhone
                    FROM Invoices
                    WHERE (? = '' OR CAST(InvoiceID AS TEXT) LIKE ?)
                    ORDER BY InvoiceID DESC
                    """;

            try (Connection con = DatabaseConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {

                ps.setString(1, invoiceFilter);
                ps.setString(2, hasInvoiceFilter ? "%" + invoiceFilter + "%" : "");

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int id = rs.getInt("InvoiceID");
                        String date = rs.getString("InvoiceDate");
                        double total = rs.getDouble("InvoiceTotal");
                        String display = "رقم: " + id
                                + " | " + date
                                + " | المجموع: " + total + " EGP | ID:" + id;
                        results.add(display);
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() ->
                        new Alert(Alert.AlertType.ERROR, "حدث خطأ أثناء جلب الفواتير:\n" + ex.getMessage()).showAndWait());
            }

            Platform.runLater(() -> {
                lstInvoices.getItems().setAll(results);
                lblStatus.setText("تم التحميل (" + results.size() + ")");
            });
        }, "old-invoices-loader").start();
    }

    private void loadInvoiceItems(int invoiceId) {
        lblStatus.setText("تحميل عناصر الفاتورة...");
        lstItems.getItems().clear();

        new Thread(() -> {
            List<String> items = new ArrayList<>();

            try (Connection con = DatabaseConnection.getConnection()) {
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT ProductName, Quantity, UnitPrice FROM InvoiceItems WHERE InvoiceID = ?")) {

                    ps.setInt(1, invoiceId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String name = rs.getString("ProductName");
                            int qty = rs.getInt("Quantity");
                            double price = rs.getDouble("UnitPrice");
                            items.add(name + " × " + qty + " = " + String.format("%.2f", qty * price) + " EGP");
                        }
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() ->
                        new Alert(Alert.AlertType.ERROR, "خطأ أثناء تحميل عناصر الفاتورة:\n" + ex.getMessage()).showAndWait());
            }

            Platform.runLater(() -> {
                lstItems.getItems().setAll(items);
                lblStatus.setText("تم تحميل العناصر (" + items.size() + ")");
            });
        }, "invoice-items-loader").start();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
