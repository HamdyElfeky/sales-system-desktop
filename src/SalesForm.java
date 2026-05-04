import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SalesForm extends Application {
    private final List<SaleItem> currentInvoiceItems = new ArrayList<>();
    private double totalInvoice = 0;
    private ListView<String> lstInvoice;
    private Label lblTotal;
    private PauseTransition barcodeDelay;
    private TextField txtSearch;
    private ListView<SearchResult> lstSearchResults;

    @Override
    public void start(Stage stage) {
        AppIcon.apply(stage);
        txtSearch = new TextField();
        txtSearch.setPromptText("ابحث باسم المنتج أو QR");

        Button btnAddProduct = new Button("➕ إضافة منتج جديد");
        btnAddProduct.setOnAction(e -> {
            PasswordField passwordField = new PasswordField();
            passwordField.setPromptText("أدخل كلمة السر");

            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("التحقق من الصلاحيات");
            dialog.getDialogPane().setContent(passwordField);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            dialog.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    if (PasswordManager.isValidPassword(passwordField.getText())) {
                        stage.close();
                        StockForm.getInstance().start(new Stage());
                    } else {
                        new Alert(Alert.AlertType.ERROR, "❌ كلمة السر غير صحيحة!").showAndWait();
                    }
                }
            });
        });

        Button btnOldInvoices = new Button("📜 فواتير قديمة");
        btnOldInvoices.setOnAction(e -> {
            stage.close();
            new OldInvoicesForm().start(new Stage());
        });

        Button btnSave = new Button("💾 حفظ الفاتورة");
        btnSave.setOnAction(e -> saveInvoice());

        Button btnHistory = new Button("📋 سجل المرتجعات");
        btnHistory.setOnAction(e -> new ReturnHistoryForm().start(new Stage()));

        Button btnClear = new Button("🧹 مسح الفاتورة");
        btnClear.setOnAction(e -> clearCurrentInvoice());

        lstSearchResults = new ListView<>();
        lstSearchResults.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(SearchResult item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayText);
            }
        });

        lstInvoice = new ListView<>();
        lstInvoice.setCellFactory(list -> new InvoiceCell());

        lblTotal = new Label("الإجمالي: 0.00 EGP");

        barcodeDelay = new PauseTransition(Duration.millis(150));
        txtSearch.textProperty().addListener((obs, oldVal, newVal) -> barcodeDelay.playFromStart());

        barcodeDelay.setOnFinished(e -> {
            String input = txtSearch.getText().trim();
            if (input.isEmpty()) {
                return;
            }

            if (tryAddProductByBarcode(input)) {
                txtSearch.clear();
                txtSearch.requestFocus();
                return;
            }

            searchProduct(input);
        });

        lstSearchResults.setOnMouseClicked(e -> {
            SearchResult selected = lstSearchResults.getSelectionModel().getSelectedItem();
            if (selected == null || selected.messageRow) {
                return;
            }
            openQuantityDialog(selected.productName, selected.unitPrice, selected.stock);
        });

        lstInvoice.setOnMouseClicked(e -> {
            String selected = lstInvoice.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }

            String name = selected.substring(0, selected.indexOf(" × "));
            SaleItem item = currentInvoiceItems.stream()
                    .filter(i -> i.getProductName().equalsIgnoreCase(name))
                    .findFirst()
                    .orElse(null);

            if (item == null) {
                return;
            }

            TextInputDialog dialog = new TextInputDialog(String.valueOf(item.getQuantity()));
            dialog.setHeaderText("تعديل الكمية لـ: " + name);
            dialog.setContentText("أدخل الكمية الجديدة:");

            dialog.showAndWait().ifPresent(qtyStr -> {
                try {
                    int newQty = Integer.parseInt(qtyStr);
                    if (newQty <= 0) {
                        currentInvoiceItems.remove(item);
                    } else {
                        item.setQuantity(newQty);
                    }

                    refreshInvoiceList();
                    txtSearch.requestFocus();

                } catch (NumberFormatException ex) {
                    new Alert(Alert.AlertType.ERROR, "⚠️ أدخل كمية صحيحة!").showAndWait();
                }
            });
        });

        TitledPane searchPane = new TitledPane("🔍 نتائج البحث", lstSearchResults);
        TitledPane invoicePane = new TitledPane("🧾 الفاتورة الحالية", lstInvoice);
        searchPane.setCollapsible(false);
        invoicePane.setCollapsible(false);


        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        root.getChildren().addAll(
                txtSearch,
                searchPane,
                invoicePane,
                lblTotal,
                new HBox(10, btnSave, btnClear, btnAddProduct, btnOldInvoices, btnHistory)
        );

        Scene scene = new Scene(root, 620, 680);
        stage.setTitle("Sales System");
        stage.setScene(scene);
        stage.show();
        txtSearch.requestFocus();
    }

    private class InvoiceCell extends ListCell<String> {
        private final HBox layout = new HBox();
        private final Label lblText = new Label();
        private final Button btnRemove = new Button("✖");

        InvoiceCell() {
            btnRemove.setStyle("-fx-background-color: red; -fx-text-fill: white; -fx-font-weight: bold;");
            btnRemove.setOnAction(e -> {
                String text = getItem();
                if (text != null) {
                    String name = text.substring(0, text.indexOf(" × "));
                    currentInvoiceItems.removeIf(i -> i.getProductName().equalsIgnoreCase(name));
                    refreshInvoiceList();
                    txtSearch.requestFocus();
                }
            });

            layout.setSpacing(10);
            layout.getChildren().addAll(lblText, btnRemove);
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
            } else {
                lblText.setText(item);
                setGraphic(layout);
            }
        }
    }

    private void openQuantityDialog(String name, double price, Integer stock) {
        TextInputDialog qtyDialog = new TextInputDialog("1");
        String stockLabel = stock == null ? "N/A" : String.valueOf(stock);
        qtyDialog.setHeaderText("Ø§Ù„ÙƒÙ…ÙŠØ© Ø§Ù„Ù…Ø·Ù„ÙˆØ¨Ø© Ù…Ù† " + name + " (Ø§Ù„Ù…ØªÙˆÙØ±: " + stockLabel + ")");
        qtyDialog.setContentText("Ø£Ø¯Ø®Ù„ Ø§Ù„ÙƒÙ…ÙŠØ©:");

        qtyDialog.showAndWait().ifPresent(qtyStr -> {
            try {
                int qty = Integer.parseInt(qtyStr);
                if (qty <= 0) {
                    new Alert(Alert.AlertType.WARNING, "âš ï¸ Ø§Ù„ÙƒÙ…ÙŠØ© ÙŠØ¬Ø¨ Ø£Ù† ØªÙƒÙˆÙ† Ø£ÙƒØ¨Ø± Ù…Ù† 0!").showAndWait();
                    return;
                }
                if (stock != null && qty > stock) {
                    new Alert(Alert.AlertType.WARNING, "âš ï¸ Ø§Ù„ÙƒÙ…ÙŠØ© Ø§Ù„Ù…Ø·Ù„ÙˆØ¨Ø© ØºÙŠØ± Ù…ØªÙˆÙØ±Ø© Ø¨Ø§Ù„Ù…Ø®Ø²ÙˆÙ†!").showAndWait();
                    return;
                }

                addOrIncrementInvoiceItem(name, qty, price);
                refreshInvoiceList();
                txtSearch.clear();
                txtSearch.requestFocus();

            } catch (NumberFormatException ex) {
                new Alert(Alert.AlertType.ERROR, "âš ï¸ Ø£Ø¯Ø®Ù„ Ø±Ù‚Ù…Ù‹Ø§ ØµØ­ÙŠØ­Ù‹Ø§ Ù„Ù„ÙƒÙ…ÙŠØ©!").showAndWait();
            }
        });
    }

    private void addOrIncrementInvoiceItem(String name, int qty, double price) {
        SaleItem existingItem = currentInvoiceItems.stream()
                .filter(i -> i.getProductName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);

        if (existingItem != null) {
            existingItem.setQuantity(existingItem.getQuantity() + qty);
        } else {
            currentInvoiceItems.add(new SaleItem(name, qty, price));
        }
    }

    private void refreshInvoiceList() {
        lstInvoice.getItems().clear();
        totalInvoice = 0;
        for (SaleItem item : currentInvoiceItems) {
            double lineTotal = item.getQuantity() * item.getUnitPrice();
            lstInvoice.getItems().add(item.getProductName() + " × " + item.getQuantity() + " = " + String.format("%.2f", lineTotal) + " EGP");
            totalInvoice += lineTotal;
        }
        lblTotal.setText("الإجمالي: " + String.format("%.2f", totalInvoice) + " EGP");
    }

    private void searchProduct(String keyword) {
        lstSearchResults.getItems().clear();
        if (keyword.isEmpty()) {
            return;
        }

        String sql = "SELECT ProductName, UnitPrice, Quantity FROM Products WHERE ProductName LIKE ? OR Barcode LIKE ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, "%" + keyword + "%");
            ps.setString(2, "%" + keyword + "%");
            ResultSet rs = ps.executeQuery();

            List<SearchResult> results = new ArrayList<>();
            while (rs.next()) {
                String name = rs.getString("ProductName");
                double price = rs.getDouble("UnitPrice");
                Integer stock = rs.getObject("Quantity") == null ? null : rs.getInt("Quantity");
                results.add(SearchResult.product(name, price, stock));
            }

            if (results.isEmpty()) {
                lstSearchResults.getItems().add(SearchResult.message("❌ لا يوجد منتج بهذا الاسم أو الكود"));
                return;
            }

            lstSearchResults.getItems().setAll(results);

            if (results.size() == 1 && keyword.matches("\\d+")) {
                SearchResult onlyResult = results.get(0);
                addOrIncrementInvoiceItem(onlyResult.productName, 1, onlyResult.unitPrice);
                refreshInvoiceList();
                txtSearch.clear();
                txtSearch.requestFocus();
            }

        } catch (Exception ex) {
            lstSearchResults.getItems().add(SearchResult.message("⚠️ Error: " + ex.getMessage()));
        }
    }

    private boolean tryAddProductByBarcode(String barcode) {
        String sql = "SELECT ProductName, UnitPrice, Quantity FROM Products WHERE Barcode = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, barcode);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String name = rs.getString("ProductName");
                double price = rs.getDouble("UnitPrice");
                Integer stock = rs.getObject("Quantity") == null ? null : rs.getInt("Quantity");

                if (stock != null && stock <= 0) {
                    new Alert(Alert.AlertType.WARNING, "âš ï¸ Ø§Ù„Ù…Ù†ØªØ¬ ØºÙŠØ± Ù…ØªÙˆÙØ± Ø¨Ø§Ù„Ù…Ø®Ø²ÙˆÙ†!").showAndWait();
                    return true;
                }

                addOrIncrementInvoiceItem(name, 1, price);
                refreshInvoiceList();
                return true;
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return false;
    }

    private void saveInvoice() {
        if (currentInvoiceItems.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "⚠️ لا توجد منتجات في الفاتورة!").showAndWait();
            return;
        }

        javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
            @Override
            protected Void call() {
                try (Connection con = DatabaseConnection.getConnection()) {
                    con.setAutoCommit(false);

                    String sqlInvoice = "INSERT INTO Invoices (InvoiceDate, InvoiceTotal, CustomerName, CustomerPhone) VALUES (?, ?, ?, ?)";
                    try (PreparedStatement psInvoice = con.prepareStatement(sqlInvoice, Statement.RETURN_GENERATED_KEYS)) {
                        psInvoice.setString(1, DatabaseConnection.currentDateTimeText());
                        psInvoice.setDouble(2, totalInvoice);
                        psInvoice.setString(3, "");
                        psInvoice.setString(4, "");
                        psInvoice.executeUpdate();

                        ResultSet rs = psInvoice.getGeneratedKeys();
                        rs.next();
                        int invoiceId = rs.getInt(1);

                        String sqlItem = "INSERT INTO InvoiceItems (InvoiceID, ProductName, Quantity, UnitPrice) VALUES (?, ?, ?, ?)";
                        try (PreparedStatement psItem = con.prepareStatement(sqlItem);
                             PreparedStatement psStock = con.prepareStatement("UPDATE Products SET Quantity = Quantity - ? WHERE ProductName = ? AND Quantity IS NOT NULL")) {

                            for (SaleItem item : currentInvoiceItems) {
                                psItem.setInt(1, invoiceId);
                                psItem.setString(2, item.getProductName());
                                psItem.setInt(3, item.getQuantity());
                                psItem.setDouble(4, item.getUnitPrice());
                                psItem.addBatch();

                                psStock.setInt(1, item.getQuantity());
                                psStock.setString(2, item.getProductName());
                                psStock.addBatch();
                            }
                            psItem.executeBatch();
                            psStock.executeBatch();
                        }

                        con.commit();
                        InvoicePrinter.PrintResult printResult = InvoicePrinter.printInvoice(invoiceId);

                        Platform.runLater(() -> {
                            String message = "✅ تم حفظ الفاتورة بنجاح!\nرقم الفاتورة: " + invoiceId
                                    + "\nتم إنشاء ملف الفاتورة هنا:\n" + printResult.getPdfPath();
                            if (printResult.isPrinted()) {
                                message += "\nتم إرسال الفاتورة للطباعة.";
                            } else {
                                message += "\nتعذر الطباعة التلقائية. يمكنك طباعة ملف الـ PDF يدويًا.";
                            }

                            new Alert(Alert.AlertType.INFORMATION, message).showAndWait();
                            clearCurrentInvoice();
                        });
                    } catch (Exception ex) {
                        con.rollback();
                        throw ex;
                    }
                } catch (Exception ex) {
                    Platform.runLater(() ->
                            new Alert(Alert.AlertType.ERROR, "❌ خطأ أثناء حفظ الفاتورة!\n" + ex.getMessage()).showAndWait());
                }
                return null;
            }
        };

        new Thread(task).start();
    }

    private void clearCurrentInvoice() {
        currentInvoiceItems.clear();
        if (lstInvoice != null) {
            lstInvoice.getItems().clear();
        }
        if (lstSearchResults != null) {
            lstSearchResults.getItems().clear();
        }
        totalInvoice = 0;
        if (lblTotal != null) {
            lblTotal.setText("Ø§Ù„Ø¥Ø¬Ù…Ø§Ù„ÙŠ: 0.00 EGP");
        }
        if (txtSearch != null) {
            txtSearch.clear();
            txtSearch.requestFocus();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    private static class SearchResult {
        private final String productName;
        private final double unitPrice;
        private final Integer stock;
        private final String displayText;
        private final boolean messageRow;

        private SearchResult(String productName, double unitPrice, Integer stock, String displayText, boolean messageRow) {
            this.productName = productName;
            this.unitPrice = unitPrice;
            this.stock = stock;
            this.displayText = displayText;
            this.messageRow = messageRow;
        }

        private static SearchResult product(String productName, double unitPrice, Integer stock) {
            String stockLabel = stock == null ? "N/A" : String.valueOf(stock);
            return new SearchResult(
                    productName,
                    unitPrice,
                    stock,
                    productName + " - " + unitPrice + " EGP (Stock: " + stockLabel + ")",
                    false
            );
        }

        private static SearchResult message(String text) {
            return new SearchResult("", 0, null, text, true);
        }
    }
}