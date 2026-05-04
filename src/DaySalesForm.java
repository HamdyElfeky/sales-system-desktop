import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class DaySalesForm extends Application {
    private final ObservableList<DayTransactionRow> transactions = FXCollections.observableArrayList();

    private TableView<DayTransactionRow> table;
    private DatePicker datePicker;
    private Label lblSalesTotal;
    private Label lblReturnsTotal;
    private Label lblTransactionCount;
    private Label lblExpectedCash;
    private Label lblStatus;

    @Override
    public void start(Stage stage) {
        AppIcon.apply(stage);
        Label lblTitle = new Label("ملخص نهاية الوردية");
        lblTitle.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        datePicker = new DatePicker(LocalDate.now());
        datePicker.setOnAction(e -> loadTransactions());

        Button btnRefresh = new Button("تحديث");
        btnRefresh.setOnAction(e -> loadTransactions());

        HBox header = new HBox(10, new Label("التاريخ:"), datePicker, btnRefresh);
        header.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);

        table = buildTable();

        VBox summaryBox = buildSummaryBox();

        lblStatus = new Label("جاهز");

        VBox centerBox = new VBox(12, table, summaryBox);
        VBox.setVgrow(table, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setTop(new VBox(10, lblTitle, header));
        root.setCenter(centerBox);
        root.setBottom(lblStatus);
        root.setPadding(new Insets(16));
        root.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
        root.setStyle("-fx-background-color: #f6f7fb;");

        Scene scene = new Scene(root, 900, 620);
        stage.setTitle("Day Sales");
        stage.setScene(scene);
        stage.show();

        loadTransactions();
    }

    private TableView<DayTransactionRow> buildTable() {
        TableView<DayTransactionRow> tableView = new TableView<>(transactions);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tableView.setPlaceholder(new Label("لا توجد حركات في هذا اليوم"));
        tableView.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
        tableView.setStyle("-fx-background-color: white; -fx-border-color: #d8dce6; -fx-border-radius: 8; -fx-background-radius: 8;");

        TableColumn<DayTransactionRow, Integer> colInvoiceNumber = new TableColumn<>("رقم الفاتورة");
        colInvoiceNumber.setCellValueFactory(new PropertyValueFactory<>("invoiceNumber"));

        TableColumn<DayTransactionRow, String> colType = new TableColumn<>("نوع الحركة");
        colType.setCellValueFactory(new PropertyValueFactory<>("invoiceType"));

        TableColumn<DayTransactionRow, Double> colAmount = new TableColumn<>("المبلغ");
        colAmount.setCellValueFactory(new PropertyValueFactory<>("amount"));
        colAmount.setCellFactory(column -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%.2f EGP", item));
                }
            }
        });

        tableView.getColumns().addAll(colInvoiceNumber, colType, colAmount);

        tableView.setRowFactory(tv -> {
            TableRow<DayTransactionRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty()) {
                    showTransactionDetails(row.getItem());
                }
            });
            return row;
        });

        return tableView;
    }

    private VBox buildSummaryBox() {
        lblSalesTotal = createSummaryValueLabel();
        lblReturnsTotal = createSummaryValueLabel();
        lblTransactionCount = createSummaryValueLabel();
        lblExpectedCash = createSummaryValueLabel();

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(12);
        grid.setPadding(new Insets(14));
        grid.setStyle("-fx-background-color: white; -fx-border-color: #d8dce6; -fx-border-radius: 8; -fx-background-radius: 8;");

        grid.add(createSummaryTitleLabel("إجمالي المبيعات"), 0, 0);
        grid.add(lblSalesTotal, 1, 0);

        grid.add(createSummaryTitleLabel("إجمالي المرتجعات"), 0, 1);
        grid.add(lblReturnsTotal, 1, 1);

        grid.add(createSummaryTitleLabel("عدد الحركات"), 2, 0);
        grid.add(lblTransactionCount, 3, 0);

        grid.add(createSummaryTitleLabel("الرصيد النقدي المتوقع"), 2, 1);
        grid.add(lblExpectedCash, 3, 1);

        VBox box = new VBox(8, new Label("الملخص"), grid);
        box.getChildren().get(0).setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        return box;
    }

    private Label createSummaryTitleLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: bold;");
        return label;
    }

    private Label createSummaryValueLabel() {
        Label label = new Label("-");
        label.setStyle("-fx-font-size: 14px;");
        return label;
    }

    private void loadTransactions() {
        LocalDate selectedDate = datePicker.getValue();
        if (selectedDate == null) {
            selectedDate = LocalDate.now();
            datePicker.setValue(selectedDate);
        }

        lblStatus.setText("جارٍ تحميل حركات اليوم...");
        LocalDate finalSelectedDate = selectedDate;

        Thread loader = new Thread(() -> {
            try (Connection con = DatabaseConnection.getConnection()) {
                List<DayTransactionRow> loadedRows = new ArrayList<>();
                loadedRows.addAll(loadSalesTransactions(con, finalSelectedDate));
                loadedRows.addAll(loadReturnTransactions(con, finalSelectedDate));
                loadedRows.sort((a, b) -> b.getTransactionDate().compareTo(a.getTransactionDate()));

                SummaryStats summary = calculateSummary(loadedRows);

                Platform.runLater(() -> {
                    transactions.setAll(loadedRows);
                    updateSummary(summary);
                    lblStatus.setText("تم تحميل " + loadedRows.size() + " حركة");
                });

            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    lblStatus.setText("فشل تحميل البيانات");
                    new Alert(Alert.AlertType.ERROR, "حدث خطأ أثناء تحميل بيانات اليوم:\n" + ex.getMessage()).showAndWait();
                });
            }
        }, "day-sales-loader");

        loader.setDaemon(true);
        loader.start();
    }

    private List<DayTransactionRow> loadSalesTransactions(Connection con, LocalDate date) throws Exception {
        List<DayTransactionRow> rows = new ArrayList<>();
        String sql = """
                SELECT I.InvoiceID,
                       I.InvoiceDate,
                       COALESCE(SUM(CASE WHEN II.Quantity > 0 THEN II.Quantity * II.UnitPrice ELSE 0 END), 0) AS SaleAmount
                FROM Invoices I
                LEFT JOIN InvoiceItems II ON II.InvoiceID = I.InvoiceID
                WHERE date(I.InvoiceDate) = ?
                GROUP BY I.InvoiceID, I.InvoiceDate
                HAVING SaleAmount > 0
                ORDER BY I.InvoiceDate DESC
                """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, date.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new DayTransactionRow(
                            rs.getInt("InvoiceID"),
                            "بيع",
                            rs.getDouble("SaleAmount"),
                            rs.getString("InvoiceDate"),
                            "SALE"
                    ));
                }
            }
        }

        return rows;
    }

    private List<DayTransactionRow> loadReturnTransactions(Connection con, LocalDate date) throws Exception {
        List<DayTransactionRow> rows = new ArrayList<>();
        String sql = """
                SELECT R.ReturnID,
                       R.InvoiceID,
                       CASE
                           WHEN R.DateReturned <> '' AND R.DateReturned NOT GLOB '*[^0-9]*' THEN datetime(CAST(R.DateReturned AS INTEGER) / 1000, 'unixepoch', 'localtime')
                           ELSE R.DateReturned
                       END AS DisplayDateReturned,
                       R.QuantityReturned,
                       P.ProductName,
                       COALESCE((
                           SELECT II.UnitPrice
                           FROM InvoiceItems II
                           WHERE II.InvoiceID = R.InvoiceID
                             AND II.ProductName = P.ProductName
                             AND II.Quantity > 0
                           ORDER BY II.ItemID ASC
                           LIMIT 1
                       ), P.UnitPrice) AS UnitPrice
                FROM Returns R
                JOIN Products P ON P.ProductID = R.ProductID
                WHERE date(
                    CASE
                        WHEN R.DateReturned <> '' AND R.DateReturned NOT GLOB '*[^0-9]*' THEN datetime(CAST(R.DateReturned AS INTEGER) / 1000, 'unixepoch', 'localtime')
                        ELSE R.DateReturned
                    END
                ) = ?
                ORDER BY
                    CASE
                        WHEN R.DateReturned <> '' AND R.DateReturned NOT GLOB '*[^0-9]*' THEN CAST(R.DateReturned AS INTEGER)
                        ELSE strftime('%s', R.DateReturned) * 1000
                    END DESC
                """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, date.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    double amount = rs.getInt("QuantityReturned") * rs.getDouble("UnitPrice");
                    rows.add(new DayTransactionRow(
                            rs.getInt("InvoiceID"),
                            "مرتجع",
                            amount,
                            rs.getString("DisplayDateReturned"),
                            "RETURN",
                            rs.getInt("ReturnID"),
                            rs.getString("ProductName"),
                            rs.getInt("QuantityReturned"),
                            rs.getDouble("UnitPrice")
                    ));
                }
            }
        }

        return rows;
    }

    private SummaryStats calculateSummary(List<DayTransactionRow> rows) {
        double totalSales = 0;
        double totalReturns = 0;

        for (DayTransactionRow row : rows) {
            if ("SALE".equals(row.getTransactionKind())) {
                totalSales += row.getAmount();
            } else if ("RETURN".equals(row.getTransactionKind())) {
                totalReturns += row.getAmount();
            }
        }

        return new SummaryStats(totalSales, totalReturns, rows.size(), totalSales - totalReturns);
    }

    private void updateSummary(SummaryStats summary) {
        lblSalesTotal.setText(String.format("%.2f EGP", summary.totalSales));
        lblReturnsTotal.setText(String.format("%.2f EGP", summary.totalReturns));
        lblTransactionCount.setText(String.valueOf(summary.totalTransactions));
        lblExpectedCash.setText(String.format("%.2f EGP", summary.expectedCash));
    }

    private void showTransactionDetails(DayTransactionRow row) {
        if ("SALE".equals(row.getTransactionKind())) {
            showSaleDetails(row);
            return;
        }

        showReturnDetails(row);
    }

    private void showSaleDetails(DayTransactionRow row) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("تفاصيل الفاتورة");
        dialog.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);
        dialog.getDialogPane().setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);

        Label header = new Label("فاتورة بيع رقم " + row.getInvoiceNumber());
        header.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label dateLabel = new Label("التاريخ: " + row.getTransactionDate());
        Label amountLabel = new Label("الإجمالي: " + String.format("%.2f EGP", row.getAmount()));

        ListView<String> itemsList = new ListView<>();
        itemsList.setPrefHeight(240);

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("""
                     SELECT ProductName, Quantity, UnitPrice
                     FROM InvoiceItems
                     WHERE InvoiceID = ? AND Quantity > 0
                     ORDER BY ItemID ASC
                     """)) {

            ps.setInt(1, row.getInvoiceNumber());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String itemText = rs.getString("ProductName")
                            + " | الكمية: " + rs.getInt("Quantity")
                            + " | السعر: " + String.format("%.2f", rs.getDouble("UnitPrice")) + " EGP"
                            + " | الإجمالي: " + String.format("%.2f", rs.getInt("Quantity") * rs.getDouble("UnitPrice")) + " EGP";
                    itemsList.getItems().add(itemText);
                }
            }
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "تعذر تحميل تفاصيل الفاتورة:\n" + ex.getMessage()).showAndWait();
            return;
        }

        VBox content = new VBox(10, header, dateLabel, amountLabel, new Label("الأصناف:"), itemsList);
        content.setPadding(new Insets(12));
        content.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setMinWidth(620);
        dialog.showAndWait();
    }

    private void showReturnDetails(DayTransactionRow row) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("تفاصيل المرتجع");
        dialog.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);
        dialog.getDialogPane().setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));
        grid.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);

        grid.add(new Label("رقم الفاتورة:"), 0, 0);
        grid.add(new Label(String.valueOf(row.getInvoiceNumber())), 1, 0);

        grid.add(new Label("نوع الحركة:"), 0, 1);
        grid.add(new Label(row.getInvoiceType()), 1, 1);

        grid.add(new Label("التاريخ:"), 0, 2);
        grid.add(new Label(row.getTransactionDate()), 1, 2);

        grid.add(new Label("المنتج:"), 0, 3);
        grid.add(new Label(row.getProductName()), 1, 3);

        grid.add(new Label("الكمية المرتجعة:"), 0, 4);
        grid.add(new Label(String.valueOf(row.getQuantity())), 1, 4);

        grid.add(new Label("سعر الوحدة:"), 0, 5);
        grid.add(new Label(String.format("%.2f EGP", row.getUnitPrice())), 1, 5);

        grid.add(new Label("قيمة المرتجع:"), 0, 6);
        grid.add(new Label(String.format("%.2f EGP", row.getAmount())), 1, 6);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setMinWidth(420);
        dialog.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }

    public static class DayTransactionRow {
        private final SimpleIntegerProperty invoiceNumber;
        private final SimpleStringProperty invoiceType;
        private final SimpleDoubleProperty amount;
        private final SimpleStringProperty transactionDate;
        private final SimpleStringProperty transactionKind;
        private final int referenceId;
        private final String productName;
        private final int quantity;
        private final double unitPrice;

        public DayTransactionRow(int invoiceNumber, String invoiceType, double amount, String transactionDate, String transactionKind) {
            this(invoiceNumber, invoiceType, amount, transactionDate, transactionKind, 0, "", 0, 0);
        }

        public DayTransactionRow(int invoiceNumber, String invoiceType, double amount, String transactionDate,
                                 String transactionKind, int referenceId, String productName, int quantity, double unitPrice) {
            this.invoiceNumber = new SimpleIntegerProperty(invoiceNumber);
            this.invoiceType = new SimpleStringProperty(invoiceType);
            this.amount = new SimpleDoubleProperty(amount);
            this.transactionDate = new SimpleStringProperty(transactionDate);
            this.transactionKind = new SimpleStringProperty(transactionKind);
            this.referenceId = referenceId;
            this.productName = productName;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }

        public int getInvoiceNumber() {
            return invoiceNumber.get();
        }

        public String getInvoiceType() {
            return invoiceType.get();
        }

        public double getAmount() {
            return amount.get();
        }

        public String getTransactionDate() {
            return transactionDate.get();
        }

        public String getTransactionKind() {
            return transactionKind.get();
        }

        public int getReferenceId() {
            return referenceId;
        }

        public String getProductName() {
            return productName;
        }

        public int getQuantity() {
            return quantity;
        }

        public double getUnitPrice() {
            return unitPrice;
        }
    }

    private static class SummaryStats {
        private final double totalSales;
        private final double totalReturns;
        private final int totalTransactions;
        private final double expectedCash;

        private SummaryStats(double totalSales, double totalReturns, int totalTransactions, double expectedCash) {
            this.totalSales = totalSales;
            this.totalReturns = totalReturns;
            this.totalTransactions = totalTransactions;
            this.expectedCash = expectedCash;
        }
    }
}
