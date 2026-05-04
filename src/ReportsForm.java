import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ReportsForm extends Application {
    private static final String APP_BG = "#F5F7FB";
    private static final String CARD_BG = "#FFFFFF";
    private static final String BORDER = "#D9E1EC";
    private static final String PRIMARY = "#2F6FED";
    private static final String TEXT_MAIN = "#1F2937";
    private static final String TEXT_MUTED = "#667085";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private Label lblSales;
    private Label lblReturns;
    private Label lblProfit;
    private Label lblTransactions;
    private Label lblStatus;
    private Label lblEmptyState;
    private DatePicker dpFrom;
    private DatePicker dpTo;
    private TextField txtSearch;
    private ComboBox<String> cmbSort;
    private ProgressIndicator progressIndicator;
    private TableView<ReportRow> table;
    private final ObservableList<ReportRow> reportRows = FXCollections.observableArrayList();
    private FilteredList<ReportRow> filteredRows;

    @Override
    public void start(Stage stage) {
        AppIcon.apply(stage);
        lblSales = createMetricValueLabel();
        lblReturns = createMetricValueLabel();
        lblProfit = createMetricValueLabel();
        lblTransactions = createMetricValueLabel();
        lblStatus = new Label("جاهز");
        lblStatus.setStyle("-fx-text-fill: " + TEXT_MUTED + "; -fx-font-size: 13px;");

        dpFrom = new DatePicker(LocalDate.now());
        dpTo = new DatePicker(LocalDate.now());

        txtSearch = new TextField();
        txtSearch.setPromptText("ابحث باسم الصنف");
        styleTextInput(txtSearch);

        cmbSort = new ComboBox<>();
        cmbSort.getItems().addAll("الأكثر مبيعًا", "الأعلى قيمة", "الاسم");
        cmbSort.setValue("الأكثر مبيعًا");
        styleComboBox(cmbSort);

        Button btnGenerate = createPrimaryButton("Generate Report");
        btnGenerate.setOnAction(e -> loadReports());

        Button btnReset = createSecondaryButton("Reset Filters");
        btnReset.setOnAction(e -> resetFilters());

        MenuItem exportPdf = new MenuItem("Export PDF");
        exportPdf.setOnAction(e -> exportPdf(stage));

        MenuItem exportExcel = new MenuItem("Export Excel");
        exportExcel.setOnAction(e -> exportExcel(stage));

        MenuButton btnExport = new MenuButton("Export", null, exportPdf, exportExcel);
        btnExport.setStyle(buttonBaseStyle("#FFFFFF", PRIMARY, true));

        filteredRows = new FilteredList<>(reportRows, item -> true);

        table = createTable();
        table.setItems(filteredRows);

        progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(false);
        progressIndicator.setMaxSize(46, 46);

        lblEmptyState = new Label("لا توجد بيانات لعرضها");
        lblEmptyState.setStyle("-fx-text-fill: " + TEXT_MUTED + "; -fx-font-size: 15px;");
        lblEmptyState.setVisible(false);

        VBox infoSection = createSection(
                "Report Info",
                "راجع ملخص المبيعات والمرتجعات خلال الفترة المحددة.",
                createMetricsGrid()
        );

        VBox filtersSection = createSection(
                "Filters",
                "حدد الفترة، ابحث عن صنف، ثم أنشئ التقرير أو صدّره.",
                createFiltersContent(btnGenerate, btnReset, btnExport)
        );

        StackPane resultsContent = new StackPane(table, progressIndicator, lblEmptyState);
        StackPane.setAlignment(progressIndicator, Pos.CENTER);
        StackPane.setAlignment(lblEmptyState, Pos.CENTER);
        VBox.setVgrow(resultsContent, Priority.ALWAYS);

        VBox resultsSection = createSection(
                "Results",
                "قائمة الأصناف المباعة داخل الفترة المختارة.",
                resultsContent
        );
        VBox.setVgrow(resultsSection, Priority.ALWAYS);

        VBox root = new VBox(18, createHeader(), infoSection, filtersSection, resultsSection);
        root.setPadding(new Insets(22));
        root.setStyle("-fx-background-color: " + APP_BG + ";");

        Scene scene = new Scene(root, 980, 720);
        stage.setTitle("تقارير المبيعات");
        stage.setScene(scene);
        stage.show();

        txtSearch.textProperty().addListener((obs, oldValue, newValue) -> applyTableFilter());
        cmbSort.valueProperty().addListener((obs, oldValue, newValue) -> sortRows());

        loadReports();
    }

    private VBox createHeader() {
        Label title = new Label("التقارير");
        title.setStyle("-fx-font-size: 28px; -fx-font-weight: 700; -fx-text-fill: " + TEXT_MAIN + ";");

        Label subtitle = new Label("واجهة مبسطة لعرض النتائج، الفلترة، والتصدير بسرعة.");
        subtitle.setStyle("-fx-font-size: 14px; -fx-text-fill: " + TEXT_MUTED + ";");

        VBox header = new VBox(6, title, subtitle, lblStatus);
        header.setPadding(new Insets(0, 0, 2, 0));
        return header;
    }

    private VBox createSection(String titleText, String subtitleText, Region content) {
        Label title = new Label(titleText);
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: 700; -fx-text-fill: " + TEXT_MAIN + ";");

        Label subtitle = new Label(subtitleText);
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: " + TEXT_MUTED + ";");
        subtitle.setWrapText(true);

        VBox box = new VBox(14, title, subtitle, content);
        box.setPadding(new Insets(18));
        box.setStyle(
                "-fx-background-color: " + CARD_BG + ";" +
                "-fx-background-radius: 16;" +
                "-fx-border-color: " + BORDER + ";" +
                "-fx-border-radius: 16;" +
                "-fx-effect: dropshadow(gaussian, rgba(15,23,42,0.08), 18, 0.12, 0, 4);"
        );
        return box;
    }

    private Region createMetricsGrid() {
        HBox row = new HBox(14,
                createMetricCard("إجمالي المبيعات", lblSales),
                createMetricCard("إجمالي المرتجعات", lblReturns),
                createMetricCard("صافي الربح", lblProfit),
                createMetricCard("عدد العمليات", lblTransactions)
        );
        row.setAlignment(Pos.CENTER_LEFT);
        row.setFillHeight(true);
        return row;
    }

    private VBox createMetricCard(String title, Label valueLabel) {
        Label lblTitle = new Label(title);
        lblTitle.setStyle("-fx-font-size: 13px; -fx-text-fill: " + TEXT_MUTED + "; -fx-font-weight: 600;");

        VBox card = new VBox(8, lblTitle, valueLabel);
        card.setPadding(new Insets(14));
        card.setMinWidth(200);
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setStyle(
                "-fx-background-color: linear-gradient(to bottom right, #FFFFFF, #F8FAFC);" +
                "-fx-background-radius: 14;" +
                "-fx-border-color: " + BORDER + ";" +
                "-fx-border-radius: 14;"
        );
        return card;
    }

    private Label createMetricValueLabel() {
        Label label = new Label("0.00 EGP");
        label.setStyle("-fx-font-size: 22px; -fx-font-weight: 700; -fx-text-fill: " + TEXT_MAIN + ";");
        return label;
    }

    private Region createFiltersContent(Button btnGenerate, Button btnReset, MenuButton btnExport) {
        Label lblFrom = createFieldLabel("من تاريخ *");
        Label lblTo = createFieldLabel("إلى تاريخ *");
        Label lblSearch = createFieldLabel("بحث بالصنف");
        Label lblSort = createFieldLabel("ترتيب النتائج");

        styleDatePicker(dpFrom);
        styleDatePicker(dpTo);

        VBox fromBox = new VBox(6, lblFrom, dpFrom);
        VBox toBox = new VBox(6, lblTo, dpTo);
        VBox searchBox = new VBox(6, lblSearch, txtSearch);
        VBox sortBox = new VBox(6, lblSort, cmbSort);

        fromBox.setMinWidth(180);
        toBox.setMinWidth(180);
        sortBox.setMinWidth(180);
        HBox.setHgrow(searchBox, Priority.ALWAYS);

        HBox filtersRow = new HBox(14, fromBox, toBox, sortBox, searchBox);
        filtersRow.setAlignment(Pos.BOTTOM_LEFT);

        Label helper = new Label("يمكنك تصفية النتائج بالاسم، ترتيبها، ثم تصدير النسخة الحالية.");
        helper.setStyle("-fx-font-size: 12px; -fx-text-fill: " + TEXT_MUTED + ";");

        HBox actionRow = new HBox(10, btnGenerate, btnReset, btnExport);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        return new VBox(12, filtersRow, helper, actionRow);
    }

    private Label createFieldLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 13px; -fx-font-weight: 600; -fx-text-fill: " + TEXT_MAIN + ";");
        return label;
    }

    private TableView<ReportRow> createTable() {
        TableView<ReportRow> reportTable = new TableView<>();
        reportTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        reportTable.setPlaceholder(new Label(""));
        reportTable.setStyle(
                "-fx-background-color: transparent;" +
                "-fx-border-color: transparent;" +
                "-fx-font-size: 14px;"
        );

        TableColumn<ReportRow, Number> colIndex = new TableColumn<>("#");
        colIndex.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(reportTable.getItems().indexOf(cell.getValue()) + 1));
        colIndex.setSortable(false);
        colIndex.setMaxWidth(60);

        TableColumn<ReportRow, String> colName = new TableColumn<>("الصنف");
        colName.setCellValueFactory(new PropertyValueFactory<>("productName"));

        TableColumn<ReportRow, Number> colQty = new TableColumn<>("الكمية");
        colQty.setCellValueFactory(new PropertyValueFactory<>("quantitySold"));
        colQty.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<ReportRow, String> colSales = new TableColumn<>("قيمة المبيعات");
        colSales.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatMoney(cell.getValue().getSalesValue())));
        colSales.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<ReportRow, String> colAverage = new TableColumn<>("متوسط السعر");
        colAverage.setCellValueFactory(cell -> new ReadOnlyStringWrapper(formatMoney(cell.getValue().getAveragePrice())));
        colAverage.setStyle("-fx-alignment: CENTER-RIGHT;");

        reportTable.getColumns().addAll(colIndex, colName, colQty, colSales, colAverage);
        reportTable.setRowFactory(tv -> {
            javafx.scene.control.TableRow<ReportRow> row = new javafx.scene.control.TableRow<>();
            row.itemProperty().addListener((obs, oldItem, newItem) -> {
                if (newItem == null) {
                    row.setStyle("");
                } else {
                    String bg = row.getIndex() % 2 == 0 ? "#FFFFFF" : "#F8FAFC";
                    row.setStyle("-fx-background-color: " + bg + ";");
                }
            });
            return row;
        });
        return reportTable;
    }

    private void loadReports() {
        LocalDate from = dpFrom.getValue();
        LocalDate to = dpTo.getValue();

        if (from == null || to == null) {
            showError("يرجى اختيار تاريخ البداية والنهاية.");
            return;
        }

        if (from.isAfter(to)) {
            showError("تاريخ البداية يجب أن يكون قبل أو يساوي تاريخ النهاية.");
            return;
        }

        progressIndicator.setVisible(true);
        lblEmptyState.setVisible(false);
        table.setDisable(true);
        lblStatus.setText("جاري إنشاء التقرير...");

        Task<ReportData> task = new Task<>() {
            @Override
            protected ReportData call() throws Exception {
                return fetchReportData(from, to);
            }
        };

        task.setOnSucceeded(event -> {
            ReportData data = task.getValue();
            reportRows.setAll(data.rows());
            updateSummary(data);
            applyTableFilter();
            sortRows();
            progressIndicator.setVisible(false);
            table.setDisable(false);
            lblEmptyState.setVisible(filteredRows.isEmpty());
            lblStatus.setText("تم إنشاء التقرير بنجاح");
        });

        task.setOnFailed(event -> {
            progressIndicator.setVisible(false);
            table.setDisable(false);
            reportRows.clear();
            updateSummary(new ReportData(0, 0, 0, 0, List.of()));
            lblEmptyState.setVisible(true);
            lblStatus.setText("حدث خطأ أثناء تحميل التقرير");
            showError("فشل تحميل التقرير: " + task.getException().getMessage());
        });

        Thread thread = new Thread(task, "reports-loader");
        thread.setDaemon(true);
        thread.start();
    }

    private ReportData fetchReportData(LocalDate from, LocalDate to) throws Exception {
        double totalSales = 0;
        double totalReturns = 0;
        int totalTransactions = 0;
        List<ReportRow> rows = new ArrayList<>();

        try (Connection con = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT IFNULL(SUM(InvoiceTotal), 0), COUNT(*) " +
                            "FROM Invoices WHERE date(InvoiceDate) BETWEEN date(?) AND date(?)")) {
                ps.setString(1, from.toString());
                ps.setString(2, to.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        totalSales = rs.getDouble(1);
                        totalTransactions = rs.getInt(2);
                    }
                }
            }

            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT IFNULL(SUM(R.QuantityReturned * IFNULL(P.UnitPrice, 0)), 0) " +
                            "FROM Returns R " +
                            "LEFT JOIN Products P ON P.ProductID = R.ProductID " +
                            "WHERE (" +
                            "date(CASE " +
                            "WHEN R.DateReturned <> '' AND R.DateReturned NOT GLOB '*[^0-9]*' THEN datetime(CAST(R.DateReturned AS INTEGER) / 1000, 'unixepoch', 'localtime') " +
                            "ELSE R.DateReturned END) BETWEEN date(?) AND date(?) " +
                            ")")) {
                ps.setString(1, from.toString());
                ps.setString(2, to.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        totalReturns = rs.getDouble(1);
                    }
                }
            }

            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT ProductName, IFNULL(SUM(Quantity), 0) AS QtySold, " +
                            "IFNULL(SUM(Quantity * UnitPrice), 0) AS SalesValue, " +
                            "IFNULL(AVG(UnitPrice), 0) AS AveragePrice " +
                            "FROM InvoiceItems " +
                            "WHERE InvoiceID IN (" +
                            "SELECT InvoiceID FROM Invoices WHERE date(InvoiceDate) BETWEEN date(?) AND date(?)" +
                            ") " +
                            "GROUP BY ProductName")) {
                ps.setString(1, from.toString());
                ps.setString(2, to.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new ReportRow(
                                rs.getString("ProductName"),
                                rs.getInt("QtySold"),
                                rs.getDouble("SalesValue"),
                                rs.getDouble("AveragePrice")
                        ));
                    }
                }
            }
        }

        return new ReportData(totalSales, totalReturns, totalSales - totalReturns, totalTransactions, rows);
    }

    private void updateSummary(ReportData data) {
        lblSales.setText(formatMoney(data.totalSales()));
        lblReturns.setText(formatMoney(data.totalReturns()));
        lblProfit.setText(formatMoney(data.netProfit()));
        lblTransactions.setText(String.valueOf(data.totalTransactions()));
    }

    private void applyTableFilter() {
        String search = txtSearch.getText() == null ? "" : txtSearch.getText().trim().toLowerCase();
        filteredRows.setPredicate(row -> search.isEmpty() || row.getProductName().toLowerCase().contains(search));
        lblEmptyState.setVisible(filteredRows.isEmpty() && !progressIndicator.isVisible());
    }

    private void sortRows() {
        String sortBy = cmbSort.getValue();
        Comparator<ReportRow> comparator;

        if ("الأعلى قيمة".equals(sortBy)) {
            comparator = Comparator.comparingDouble(ReportRow::getSalesValue).reversed();
        } else if ("الاسم".equals(sortBy)) {
            comparator = Comparator.comparing(ReportRow::getProductName, String.CASE_INSENSITIVE_ORDER);
        } else {
            comparator = Comparator.comparingInt(ReportRow::getQuantitySold).reversed();
        }

        FXCollections.sort(reportRows, comparator);
    }

    private void resetFilters() {
        dpFrom.setValue(LocalDate.now());
        dpTo.setValue(LocalDate.now());
        txtSearch.clear();
        cmbSort.setValue("الأكثر مبيعًا");
        reportRows.clear();
        updateSummary(new ReportData(0, 0, 0, 0, List.of()));
        lblStatus.setText("تمت إعادة ضبط الفلاتر");
        lblEmptyState.setVisible(true);
    }

    private void exportExcel(Stage stage) {
        if (filteredRows.isEmpty()) {
            showError("لا توجد بيانات لتصديرها.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Excel");
        chooser.setInitialFileName("report-" + LocalDate.now() + ".xlsx");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel", "*.xlsx"));
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             FileOutputStream outputStream = new FileOutputStream(file)) {
            XSSFSheet sheet = workbook.createSheet("Reports");

            Row header = sheet.createRow(0);
            String[] headers = {"Product Name", "Quantity Sold", "Sales Value", "Average Price"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
            }

            for (int i = 0; i < filteredRows.size(); i++) {
                ReportRow rowData = filteredRows.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(rowData.getProductName());
                row.createCell(1).setCellValue(rowData.getQuantitySold());
                row.createCell(2).setCellValue(rowData.getSalesValue());
                row.createCell(3).setCellValue(rowData.getAveragePrice());
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
            lblStatus.setText("تم تصدير ملف Excel بنجاح");
        } catch (Exception ex) {
            showError("تعذر تصدير Excel: " + ex.getMessage());
        }
    }

    private void exportPdf(Stage stage) {
        if (filteredRows.isEmpty()) {
            showError("لا توجد بيانات لتصديرها.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export PDF");
        chooser.setInitialFileName("report-" + LocalDate.now() + ".pdf");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }

        Document document = new Document();
        try {
            PdfWriter.getInstance(document, new FileOutputStream(file));
            document.open();

            Font titleFont = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD, BaseColor.BLACK);
            Font textFont = new Font(Font.FontFamily.HELVETICA, 11, Font.NORMAL, BaseColor.DARK_GRAY);

            Paragraph title = new Paragraph("Sales Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(new Paragraph("From: " + DATE_FORMAT.format(dpFrom.getValue()) + "  To: " + DATE_FORMAT.format(dpTo.getValue()), textFont));
            document.add(new Paragraph(" "));

            PdfPTable summaryTable = new PdfPTable(2);
            summaryTable.setWidthPercentage(100);
            summaryTable.addCell(createPdfCell("Total Sales", true));
            summaryTable.addCell(createPdfCell(lblSales.getText(), false));
            summaryTable.addCell(createPdfCell("Total Returns", true));
            summaryTable.addCell(createPdfCell(lblReturns.getText(), false));
            summaryTable.addCell(createPdfCell("Net Profit", true));
            summaryTable.addCell(createPdfCell(lblProfit.getText(), false));
            summaryTable.addCell(createPdfCell("Transactions", true));
            summaryTable.addCell(createPdfCell(lblTransactions.getText(), false));
            document.add(summaryTable);
            document.add(new Paragraph(" "));

            PdfPTable dataTable = new PdfPTable(4);
            dataTable.setWidthPercentage(100);
            dataTable.setWidths(new float[]{4, 2, 2.5f, 2.5f});
            dataTable.addCell(createPdfCell("Product", true));
            dataTable.addCell(createPdfCell("Qty", true));
            dataTable.addCell(createPdfCell("Sales Value", true));
            dataTable.addCell(createPdfCell("Average Price", true));

            for (ReportRow row : filteredRows) {
                dataTable.addCell(createPdfCell(row.getProductName(), false));
                dataTable.addCell(createPdfCell(String.valueOf(row.getQuantitySold()), false));
                dataTable.addCell(createPdfCell(formatMoney(row.getSalesValue()), false));
                dataTable.addCell(createPdfCell(formatMoney(row.getAveragePrice()), false));
            }

            document.add(dataTable);
            lblStatus.setText("تم تصدير ملف PDF بنجاح");
        } catch (Exception ex) {
            showError("تعذر تصدير PDF: " + ex.getMessage());
        } finally {
            document.close();
        }
    }

    private PdfPCell createPdfCell(String text, boolean header) {
        Font font = new Font(Font.FontFamily.HELVETICA, 11, header ? Font.BOLD : Font.NORMAL, header ? BaseColor.WHITE : BaseColor.BLACK);
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(8);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        if (header) {
            cell.setBackgroundColor(new BaseColor(47, 111, 237));
        } else {
            cell.setBackgroundColor(BaseColor.WHITE);
        }
        return cell;
    }

    private Button createPrimaryButton(String text) {
        Button button = new Button(text);
        button.setStyle(buttonBaseStyle(PRIMARY, "#FFFFFF", false));
        return button;
    }

    private Button createSecondaryButton(String text) {
        Button button = new Button(text);
        button.setStyle(buttonBaseStyle("#FFFFFF", TEXT_MAIN, true));
        return button;
    }

    private String buttonBaseStyle(String bgColor, String textColor, boolean bordered) {
        return "-fx-background-color: " + bgColor + ";" +
                "-fx-text-fill: " + textColor + ";" +
                "-fx-font-size: 13px;" +
                "-fx-font-weight: 700;" +
                "-fx-background-radius: 12;" +
                "-fx-border-radius: 12;" +
                "-fx-padding: 10 16 10 16;" +
                (bordered ? "-fx-border-color: " + BORDER + ";" : "-fx-border-color: transparent;");
    }

    private void styleTextInput(TextField textField) {
        textField.setStyle(
                "-fx-background-color: #FFFFFF;" +
                "-fx-border-color: " + BORDER + ";" +
                "-fx-border-radius: 12;" +
                "-fx-background-radius: 12;" +
                "-fx-padding: 10 12 10 12;" +
                "-fx-font-size: 13px;"
        );
    }

    private void styleDatePicker(DatePicker datePicker) {
        datePicker.setStyle(
                "-fx-background-color: #FFFFFF;" +
                "-fx-border-color: " + BORDER + ";" +
                "-fx-border-radius: 12;" +
                "-fx-background-radius: 12;" +
                "-fx-font-size: 13px;"
        );
    }

    private void styleComboBox(ComboBox<String> comboBox) {
        comboBox.setStyle(
                "-fx-background-color: #FFFFFF;" +
                "-fx-border-color: " + BORDER + ";" +
                "-fx-border-radius: 12;" +
                "-fx-background-radius: 12;" +
                "-fx-font-size: 13px;"
        );
    }

    private String formatMoney(double value) {
        return String.format("%.2f EGP", value);
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }

    public static class ReportRow {
        private final String productName;
        private final int quantitySold;
        private final double salesValue;
        private final double averagePrice;

        public ReportRow(String productName, int quantitySold, double salesValue, double averagePrice) {
            this.productName = productName;
            this.quantitySold = quantitySold;
            this.salesValue = salesValue;
            this.averagePrice = averagePrice;
        }

        public String getProductName() {
            return productName;
        }

        public int getQuantitySold() {
            return quantitySold;
        }

        public double getSalesValue() {
            return salesValue;
        }

        public double getAveragePrice() {
            return averagePrice;
        }
    }

    public record ReportData(double totalSales, double totalReturns, double netProfit, int totalTransactions, List<ReportRow> rows) {
    }
}
