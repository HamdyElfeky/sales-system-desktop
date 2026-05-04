import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;

public class ReturnForm extends Application {
    private final int invoiceId;
    private VBox itemsContainer;
    private final Map<String, TextField> returnFields = new LinkedHashMap<>();
    private final Map<String, ReturnableItem> returnableItems = new LinkedHashMap<>();

    public ReturnForm(int invoiceId) {
        this.invoiceId = invoiceId;
    }

    @Override
    public void start(Stage stage) {
        AppIcon.apply(stage);

        Label lblTitle = new Label("إرجاع منتجات من الفاتورة رقم: " + invoiceId);
        itemsContainer = new VBox(10);
        itemsContainer.setPadding(new Insets(10));

        Button btnProcess = new Button("تنفيذ الإرجاع");
        btnProcess.setOnAction(e -> processReturn());

        Button btnBack = new Button("رجوع");
        btnBack.setOnAction(e -> stage.close());

        loadInvoiceItems();

        ScrollPane scrollPane = new ScrollPane(itemsContainer);
        scrollPane.setFitToWidth(true);

        VBox root = new VBox(15, lblTitle, scrollPane, new HBox(10, btnProcess, btnBack));
        root.setPadding(new Insets(15));

        Scene scene = new Scene(root, 700, 520);
        stage.setTitle("شاشة الإرجاع");
        stage.setScene(scene);
        stage.show();
    }

    private void loadInvoiceItems() {
        itemsContainer.getChildren().clear();
        returnFields.clear();
        returnableItems.clear();

        String sql = """
                SELECT II.ProductName,
                       SUM(CASE WHEN II.Quantity > 0 THEN II.Quantity ELSE 0 END) AS SoldQty,
                       COALESCE((
                           SELECT SUM(R.QuantityReturned)
                           FROM Returns R
                           JOIN Products P ON P.ProductID = R.ProductID
                           WHERE R.InvoiceID = II.InvoiceID
                             AND P.ProductName = II.ProductName
                       ), 0) AS ReturnedQty,
                       COALESCE((
                           SELECT P.ProductID
                           FROM Products P
                           WHERE P.ProductName = II.ProductName
                           LIMIT 1
                       ), 0) AS ProductID,
                       COALESCE(MIN(CASE WHEN II.Quantity > 0 THEN II.UnitPrice END), 0) AS UnitPrice
                FROM InvoiceItems II
                WHERE II.InvoiceID = ?
                GROUP BY II.InvoiceID, II.ProductName
                HAVING SoldQty > 0
                ORDER BY MIN(II.ItemID)
                """;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, invoiceId);

            try (ResultSet rs = ps.executeQuery()) {
                boolean hasReturnableItems = false;

                while (rs.next()) {
                    String productName = rs.getString("ProductName");
                    int soldQty = rs.getInt("SoldQty");
                    int returnedQty = rs.getInt("ReturnedQty");
                    int availableQty = Math.max(0, soldQty - returnedQty);
                    int productId = rs.getInt("ProductID");
                    double unitPrice = rs.getDouble("UnitPrice");

                    ReturnableItem item = new ReturnableItem(productId, productName, soldQty, returnedQty, availableQty, unitPrice);
                    returnableItems.put(productName, item);

                    Label lbl = new Label(productName
                            + " (المباع: " + soldQty
                            + " | المرتجع: " + returnedQty
                            + " | المتاح للإرجاع: " + availableQty + ")");

                    if (availableQty > 0) {
                        TextField txtReturn = new TextField();
                        txtReturn.setPromptText("كمية الإرجاع");
                        txtReturn.setMaxWidth(120);

                        itemsContainer.getChildren().add(new HBox(10, lbl, txtReturn));
                        returnFields.put(productName, txtReturn);
                        hasReturnableItems = true;
                    } else {
                        Label lblDone = new Label("تم إرجاع الكمية بالكامل");
                        lblDone.setStyle("-fx-text-fill: #a94442;");
                        itemsContainer.getChildren().add(new HBox(10, lbl, lblDone));
                    }
                }

                if (!hasReturnableItems) {
                    itemsContainer.getChildren().add(new Label("لا توجد كميات متاحة للإرجاع في هذه الفاتورة."));
                }
            }

        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "خطأ أثناء تحميل العناصر: " + ex.getMessage()).showAndWait();
        }
    }

    private void processReturn() {
        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            double totalReturnedValue = 0;
            boolean hasAnyReturn = false;

            for (Map.Entry<String, TextField> entry : returnFields.entrySet()) {
                String productName = entry.getKey();
                String rawValue = entry.getValue().getText().trim();
                if (rawValue.isEmpty()) {
                    continue;
                }

                int qtyReturned;
                try {
                    qtyReturned = Integer.parseInt(rawValue);
                } catch (NumberFormatException ex) {
                    con.rollback();
                    new Alert(Alert.AlertType.ERROR, "الكمية المرتجعة للمنتج " + productName + " يجب أن تكون رقمًا صحيحًا.").showAndWait();
                    return;
                }

                if (qtyReturned <= 0) {
                    continue;
                }

                ReturnableItem item = returnableItems.get(productName);
                if (item == null) {
                    con.rollback();
                    new Alert(Alert.AlertType.ERROR, "تعذر العثور على بيانات المنتج: " + productName).showAndWait();
                    return;
                }

                if (item.availableQty <= 0) {
                    con.rollback();
                    new Alert(Alert.AlertType.ERROR, "هذا المنتج تم إرجاع كميته بالكامل بالفعل: " + productName).showAndWait();
                    return;
                }

                if (qtyReturned > item.availableQty) {
                    con.rollback();
                    new Alert(Alert.AlertType.ERROR,
                            "لا يمكن إرجاع " + qtyReturned + " من المنتج " + productName
                                    + ". المتاح فقط: " + item.availableQty).showAndWait();
                    return;
                }

                if (item.productId <= 0) {
                    con.rollback();
                    new Alert(Alert.AlertType.ERROR, "تعذر تحديد المنتج في المخزون: " + productName).showAndWait();
                    return;
                }

                hasAnyReturn = true;
                double returnValue = item.unitPrice * qtyReturned;
                totalReturnedValue += returnValue;

                try (PreparedStatement psInsert = con.prepareStatement(
                        "INSERT INTO Returns (InvoiceID, ProductID, QuantityReturned, DateReturned) VALUES (?, ?, ?, ?)");
                     PreparedStatement psUpdate = con.prepareStatement(
                             "UPDATE Products SET Quantity = Quantity + ? WHERE ProductID = ?");
                     PreparedStatement psReturnItem = con.prepareStatement(
                             "INSERT INTO InvoiceItems (InvoiceID, ProductName, Quantity, UnitPrice) VALUES (?, ?, ?, ?)")) {

                    psInsert.setInt(1, invoiceId);
                    psInsert.setInt(2, item.productId);
                    psInsert.setInt(3, qtyReturned);
                    psInsert.setString(4, DatabaseConnection.currentDateTimeText());
                    psInsert.executeUpdate();

                    psUpdate.setInt(1, qtyReturned);
                    psUpdate.setInt(2, item.productId);
                    psUpdate.executeUpdate();

                    psReturnItem.setInt(1, invoiceId);
                    psReturnItem.setString(2, productName + " (مرتجع)");
                    psReturnItem.setInt(3, -qtyReturned);
                    psReturnItem.setDouble(4, item.unitPrice);
                    psReturnItem.executeUpdate();
                }
            }

            if (!hasAnyReturn) {
                con.rollback();
                new Alert(Alert.AlertType.WARNING, "أدخل كمية مرتجعة صحيحة على الأقل لمنتج واحد.").showAndWait();
                return;
            }

            try (PreparedStatement psUpdateInvoice = con.prepareStatement(
                    "UPDATE Invoices SET InvoiceTotal = InvoiceTotal - ? WHERE InvoiceID = ?")) {
                psUpdateInvoice.setDouble(1, totalReturnedValue);
                psUpdateInvoice.setInt(2, invoiceId);
                psUpdateInvoice.executeUpdate();
            }

            con.commit();
            new Alert(Alert.AlertType.INFORMATION,
                    "تم تسجيل الإرجاع وتحديث الفاتورة والمخزون بنجاح.\nتم خصم "
                            + totalReturnedValue + " EGP من الفاتورة.").showAndWait();

            ((Stage) itemsContainer.getScene().getWindow()).close();

        } catch (Exception ex) {
            ex.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "خطأ أثناء الإرجاع: " + ex.getMessage()).showAndWait();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    private static class ReturnableItem {
        private final int productId;
        private final String productName;
        private final int soldQty;
        private final int returnedQty;
        private final int availableQty;
        private final double unitPrice;

        private ReturnableItem(int productId, String productName, int soldQty, int returnedQty, int availableQty, double unitPrice) {
            this.productId = productId;
            this.productName = productName;
            this.soldQty = soldQty;
            this.returnedQty = returnedQty;
            this.availableQty = availableQty;
            this.unitPrice = unitPrice;
        }
    }
}
