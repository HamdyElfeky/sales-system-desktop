import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class StockForm extends Application {
    private static final int RESET_CLICK_TRIGGER = 10;
    private static final Path INVOICES_DIR = Paths.get("invoices");

    private static StockForm instance;

    private TableView<Product> table;
    private final ObservableList<Product> products = FXCollections.observableArrayList();
    private TextField txtSearch;
    private int refreshClickCount = 0;
    private boolean resetInProgress = false;
    private PauseTransition refreshResetTimer;

    public static StockForm getInstance() {
        if (instance == null) {
            instance = new StockForm();
        }
        return instance;
    }

    public StockForm() {
        instance = this;
    }

    @Override
    public void start(Stage stage) {
        AppIcon.apply(stage);
        table = new TableView<>();

        TableColumn<Product, Integer> colId = new TableColumn<>("ID");
        colId.setCellValueFactory(new PropertyValueFactory<>("productId"));

        TableColumn<Product, String> colName = new TableColumn<>("اسم المنتج");
        colName.setCellValueFactory(new PropertyValueFactory<>("productName"));

        TableColumn<Product, Double> colPrice = new TableColumn<>("السعر");
        colPrice.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));

        TableColumn<Product, Integer> colQty = new TableColumn<>("الكمية");
        colQty.setCellValueFactory(new PropertyValueFactory<>("quantityDisplay"));

        TableColumn<Product, String> colCat = new TableColumn<>("الفئة");
        colCat.setCellValueFactory(new PropertyValueFactory<>("category"));

        TableColumn<Product, String> colCode = new TableColumn<>("الباركود");
        colCode.setCellValueFactory(new PropertyValueFactory<>("barcode"));

        table.getColumns().addAll(colId, colName, colPrice, colQty, colCat, colCode);
        table.setEditable(false);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        txtSearch = new TextField();
        txtSearch.setPromptText("ابحث بالاسم أو الباركود");
        txtSearch.textProperty().addListener((obs, oldVal, newVal) -> loadProducts());

        refreshResetTimer = new PauseTransition(javafx.util.Duration.seconds(3));
        refreshResetTimer.setOnFinished(event -> refreshClickCount = 0);

        loadProducts();

        Button btnRefresh = new Button("تحديث");
        btnRefresh.setOnAction(e -> handleRefreshButtonClick());

        Button btnAdd = new Button("إضافة منتج");
        btnAdd.setOnAction(e -> new AddProductForm(StockForm.getInstance()).start(new Stage()));

        Button btnEdit = new Button("تعديل المنتج");
        btnEdit.setOnAction(e -> askForPasswordAndEdit());

        Button btnChangePassword = new Button("Change Password");
        btnChangePassword.setOnAction(e -> openChangePasswordDialog());

        Button btnDelete = new Button("حذف المنتج");
        btnDelete.setOnAction(e -> deleteSelectedProduct());

        Button btnReports = new Button("التقارير");
        btnReports.setOnAction(e -> new ReportsForm().start(new Stage()));

        Button btnDaySales = new Button("مبيعات اليوم");
        btnDaySales.setOnAction(e -> new DaySalesForm().start(new Stage()));

        Button btnBack = new Button("رجوع");
        btnBack.setOnAction(e -> {
            stage.close();
            new SalesForm().start(new Stage());
        });

        HBox buttons = new HBox(10, btnRefresh, btnAdd, btnEdit, btnChangePassword, btnDelete, btnReports, btnDaySales, btnBack);
        buttons.setPadding(new Insets(10));

        VBox root = new VBox(10, txtSearch, table, buttons);
        root.setPadding(new Insets(15));

        Scene scene = new Scene(root, 980, 520);
        stage.setTitle("إدارة المخزون");
        stage.setScene(scene);
        stage.show();
    }

    public void loadProducts() {
        products.clear();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT * FROM Products " +
                             "WHERE (? = '' OR LOWER(ProductName) LIKE ? OR LOWER(COALESCE(Barcode, '')) LIKE ?) " +
                             "ORDER BY ProductName ASC")) {

            String searchValue = txtSearch == null ? "" : txtSearch.getText().trim().toLowerCase();
            String searchPattern = "%" + searchValue + "%";
            ps.setString(1, searchValue);
            ps.setString(2, searchPattern);
            ps.setString(3, searchPattern);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Integer quantity = rs.getObject("Quantity") == null ? null : rs.getInt("Quantity");
                products.add(new Product(
                        rs.getInt("ProductID"),
                        rs.getString("ProductName"),
                        rs.getDouble("UnitPrice"),
                        quantity,
                        rs.getString("Category"),
                        rs.getString("Barcode")
                ));
            }

            table.setItems(products);

        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "خطأ في تحميل البيانات: " + ex.getMessage()).showAndWait();
        }
    }

    private void handleRefreshButtonClick() {
        loadProducts();
        if (resetInProgress) {
            return;
        }

        refreshClickCount++;
        refreshResetTimer.playFromStart();

        if (refreshClickCount >= RESET_CLICK_TRIGGER) {
            refreshClickCount = 0;
            refreshResetTimer.stop();
            openResetPasswordDialog();
        }
    }

    private void askForPasswordAndEdit() {
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("ادخل كلمة السر");

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("التحقق");
        dialog.getDialogPane().setContent(passwordField);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                if (PasswordManager.isValidPassword(passwordField.getText())) {
                    openEditDialog();
                } else {
                    new Alert(Alert.AlertType.ERROR, "كلمة السر خطأ!").showAndWait();
                }
            }
        });
    }

    private void openChangePasswordDialog() {
        PasswordField currentPasswordField = new PasswordField();
        currentPasswordField.setPromptText("Current Password");

        PasswordField newPasswordField = new PasswordField();
        newPasswordField.setPromptText("New Password");

        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Confirm New Password");

        GridPane form = new GridPane();
        form.setVgap(10);
        form.setHgap(10);
        form.setPadding(new Insets(15));

        form.addRow(0, new Label("Current Password:"), currentPasswordField);
        form.addRow(1, new Label("New Password:"), newPasswordField);
        form.addRow(2, new Label("Confirm New Password:"), confirmPasswordField);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Change Password");
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(response -> {
            if (response != ButtonType.OK) {
                return;
            }

            String currentPassword = currentPasswordField.getText().trim();
            String newPassword = newPasswordField.getText().trim();
            String confirmPassword = confirmPasswordField.getText().trim();

            if (!PasswordManager.isValidPassword(currentPassword)) {
                new Alert(Alert.AlertType.ERROR, "Wrong current password").showAndWait();
                currentPasswordField.requestFocus();
                return;
            }

            if (newPassword.isEmpty()) {
                new Alert(Alert.AlertType.ERROR, "New password cannot be empty").showAndWait();
                newPasswordField.requestFocus();
                return;
            }

            if (!newPassword.equals(confirmPassword)) {
                new Alert(Alert.AlertType.ERROR, "New password and confirm password do not match").showAndWait();
                confirmPasswordField.requestFocus();
                return;
            }

            try {
                PasswordManager.changePassword(currentPassword, newPassword);
                new Alert(Alert.AlertType.INFORMATION, "Password changed successfully").showAndWait();
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "Unable to save the new password: " + ex.getMessage()).showAndWait();
            }
        });
    }

    private void openResetPasswordDialog() {
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Enter reset password");

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Reset System");
        dialog.getDialogPane().setContent(passwordField);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(response -> {
            if (response != ButtonType.OK) {
                return;
            }

            if (!"01021700887".equals(passwordField.getText().trim())) {
                new Alert(Alert.AlertType.ERROR, "Incorrect password").showAndWait();
                return;
            }

            Alert confirm = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    "Are you sure you want to reset all data?",
                    ButtonType.YES,
                    ButtonType.NO
            );

            confirm.showAndWait().ifPresent(confirmResponse -> {
                if (confirmResponse == ButtonType.YES) {
                    performSystemReset();
                }
            });
        });
    }

    private void performSystemReset() {
        if (resetInProgress) {
            return;
        }

        resetInProgress = true;
        table.setDisable(true);

        Task<Void> resetTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try (Connection con = DatabaseConnection.getConnection();
                     Statement stmt = con.createStatement()) {

                    con.setAutoCommit(false);
                    stmt.execute("PRAGMA foreign_keys = OFF");
                    stmt.executeUpdate("DELETE FROM InvoiceItems");
                    stmt.executeUpdate("DELETE FROM Returns");
                    stmt.executeUpdate("DELETE FROM Invoices");
                    stmt.executeUpdate("DELETE FROM Products");
                    stmt.executeUpdate("DELETE FROM sqlite_sequence");
                    con.commit();
                    stmt.execute("PRAGMA foreign_keys = ON");
                } catch (Exception ex) {
                    throw ex;
                }

                PasswordManager.resetToDefaultPassword();
                deleteSavedInvoiceFiles();
                return null;
            }
        };

        resetTask.setOnSucceeded(event -> {
            resetInProgress = false;
            refreshClickCount = 0;
            table.setDisable(false);
            clearUiState();
            new Alert(Alert.AlertType.INFORMATION, "System reset completed successfully").showAndWait();
        });

        resetTask.setOnFailed(event -> {
            resetInProgress = false;
            refreshClickCount = 0;
            table.setDisable(false);
            Throwable error = resetTask.getException();
            new Alert(
                    Alert.AlertType.ERROR,
                    "Reset failed: " + (error == null ? "Unknown error" : error.getMessage())
            ).showAndWait();
        });

        Thread resetThread = new Thread(resetTask, "system-reset-thread");
        resetThread.setDaemon(true);
        resetThread.start();
    }

    private void deleteSavedInvoiceFiles() throws IOException {
        if (!Files.exists(INVOICES_DIR) || !Files.isDirectory(INVOICES_DIR)) {
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(INVOICES_DIR)) {
            for (Path path : stream) {
                Files.deleteIfExists(path);
            }
        }
    }

    private void clearUiState() {
        products.clear();
        table.getItems().clear();
        table.getSelectionModel().clearSelection();
        loadProducts();
    }

    private void openEditDialog() {
        Product selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            new Alert(Alert.AlertType.WARNING, "اختر منتج").showAndWait();
            return;
        }

        TextField txtName = new TextField(selected.getProductName());
        TextField txtPrice = new TextField(String.valueOf(selected.getUnitPrice()));
        TextField txtQty = new TextField(selected.getQuantity() == null ? "" : String.valueOf(selected.getQuantity()));
        txtQty.setPromptText("????? ?????? ?? N/A");
        TextField txtCat = new TextField(selected.getCategory());
        TextField txtBarcode = new TextField(selected.getBarcode());

        GridPane form = new GridPane();
        form.setVgap(10);
        form.setHgap(10);
        form.setPadding(new Insets(15));

        form.addRow(0, new Label("اسم المنتج:"), txtName);
        form.addRow(1, new Label("السعر:"), txtPrice);
        form.addRow(2, new Label("الكمية:"), txtQty);
        form.addRow(3, new Label("الفئة:"), txtCat);
        form.addRow(4, new Label("الباركود:"), txtBarcode);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("تعديل المنتج");
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try (Connection con = DatabaseConnection.getConnection()) {
                    PreparedStatement ps = con.prepareStatement(
                            "UPDATE Products SET ProductName=?, UnitPrice=?, Quantity=?, Category=?, Barcode=? WHERE ProductID=?"
                    );
                    ps.setString(1, txtName.getText());
                    ps.setDouble(2, Double.parseDouble(txtPrice.getText()));
                    String qtyText = txtQty.getText().trim();
                    if (qtyText.isEmpty()) {
                        ps.setNull(3, java.sql.Types.INTEGER);
                    } else {
                        ps.setInt(3, Integer.parseInt(qtyText));
                    }
                    ps.setString(4, txtCat.getText());
                    ps.setString(5, txtBarcode.getText());
                    ps.setInt(6, selected.getProductId());
                    ps.executeUpdate();

                    new Alert(Alert.AlertType.INFORMATION, "تم التعديل!").showAndWait();
                    loadProducts();

                } catch (Exception ex) {
                    new Alert(Alert.AlertType.ERROR, "خطأ: " + ex.getMessage()).showAndWait();
                }
            }
        });
    }

    private void deleteSelectedProduct() {
        Product selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            new Alert(Alert.AlertType.WARNING, "اختر منتج!").showAndWait();
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "هل أنت متأكد من الحذف؟", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                try (Connection con = DatabaseConnection.getConnection()) {
                    PreparedStatement ps = con.prepareStatement("DELETE FROM Products WHERE ProductID=?");
                    ps.setInt(1, selected.getProductId());
                    ps.executeUpdate();

                    new Alert(Alert.AlertType.INFORMATION, "تم الحذف!").showAndWait();
                    loadProducts();

                } catch (Exception ex) {
                    new Alert(Alert.AlertType.ERROR, "خطأ: " + ex.getMessage()).showAndWait();
                }
            }
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
