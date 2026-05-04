import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import java.awt.Desktop;
import java.io.File;
import java.io.FileOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class InvoicePrinter {

    private static final String STORE_NAME = "محل حمادة";
    private static final String STORE_ADDRESS = "العنوان: .........";
    private static final String STORE_PHONE = "هاتف: 01234567890";
    private static final float RECEIPT_WIDTH = 260f;
    private static final float PAGE_MARGIN = 12f;
    private static final String LRM = "\u200E";

    public static PrintResult printInvoice(int invoiceId) throws Exception {
        ReceiptData receiptData = loadReceiptData(invoiceId);
        String pdfPath = buildReceiptPdf(receiptData);
        boolean printed = tryPrintPdf(pdfPath);
        return new PrintResult(pdfPath, printed);
    }

    private static ReceiptData loadReceiptData(int invoiceId) throws Exception {
        try (Connection con = DatabaseConnection.getConnection()) {
            String invoiceSql = """
                    SELECT InvoiceID, InvoiceDate, InvoiceTotal, COALESCE(CustomerName, '') AS CustomerName, COALESCE(CustomerPhone, '') AS CustomerPhone
                    FROM Invoices
                    WHERE InvoiceID = ?
                    """;

            try (PreparedStatement psInvoice = con.prepareStatement(invoiceSql)) {
                psInvoice.setInt(1, invoiceId);

                try (ResultSet rsInvoice = psInvoice.executeQuery()) {
                    if (!rsInvoice.next()) {
                        throw new IllegalStateException("Invoice not found: " + invoiceId);
                    }

                    ReceiptData receiptData = new ReceiptData();
                    receiptData.invoiceId = rsInvoice.getInt("InvoiceID");
                    receiptData.invoiceDateTime = parseDateTime(rsInvoice.getString("InvoiceDate"));
                    receiptData.customerName = "";
                    receiptData.customerPhone = "";
                    receiptData.subtotal = rsInvoice.getDouble("InvoiceTotal");
                    receiptData.discount = 0;
                    receiptData.netTotal = receiptData.subtotal - receiptData.discount;
                    receiptData.paid = receiptData.netTotal;
                    receiptData.remaining = 0;

                    String itemsSql = """
                            SELECT ProductName, Quantity, UnitPrice
                            FROM InvoiceItems
                            WHERE InvoiceID = ? AND Quantity > 0
                            ORDER BY ItemID ASC
                            """;

                    try (PreparedStatement psItems = con.prepareStatement(itemsSql)) {
                        psItems.setInt(1, invoiceId);
                        try (ResultSet rsItems = psItems.executeQuery()) {
                            while (rsItems.next()) {
                                ReceiptItem item = new ReceiptItem();
                                item.name = rsItems.getString("ProductName");
                                item.quantity = rsItems.getInt("Quantity");
                                item.price = rsItems.getDouble("UnitPrice");
                                item.total = item.quantity * item.price;
                                receiptData.items.add(item);
                            }
                        }
                    }

                    return receiptData;
                }
            }
        }
    }

    private static String buildReceiptPdf(ReceiptData data) throws Exception {
        String downloadsDir = System.getProperty("user.home") + File.separator + "Downloads";
        String folderPath = downloadsDir + File.separator + "SalesSystem" + File.separator + "Invoices" + "_" + java.time.LocalDate.now();
        File folder = new File(folderPath);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        String pdfPath = folderPath + File.separator + "Invoice_" + data.invoiceId + ".pdf";

        float calculatedHeight = 560f + (data.items.size() * 30f);
        Rectangle pageSize = new Rectangle(RECEIPT_WIDTH, Math.max(720f, calculatedHeight));
        Document document = new Document(pageSize, PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN, PAGE_MARGIN);
        PdfWriter.getInstance(document, new FileOutputStream(pdfPath));
        document.open();

        BaseFont baseFont = BaseFont.createFont("C:\\Windows\\Fonts\\arial.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        Font normalFont = new Font(baseFont, 9, Font.NORMAL);
        Font boldFont = new Font(baseFont, 12, Font.BOLD);
        Font smallBoldFont = new Font(baseFont, 9, Font.BOLD);
        Font itemFont = new Font(baseFont, 8, Font.NORMAL);
        Font moneyFont = new Font(baseFont, 8, Font.NORMAL);

        addTextRow(document, STORE_NAME, boldFont, Element.ALIGN_CENTER, true);
        addTextRow(document, STORE_ADDRESS, normalFont, Element.ALIGN_CENTER, true);
        addTextRow(document, STORE_PHONE, normalFont, Element.ALIGN_CENTER, true);
        addDivider(document);

        addLabelValueRow(document, "رقم الفاتورة:", "#" + ltr(data.invoiceId), boldFont, boldFont);
        addTwoInfoRow(
                document,
                "التاريخ: " + ltr(data.invoiceDateTime.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))),
                "الوقت: " + ltr(data.invoiceDateTime.format(DateTimeFormatter.ofPattern("hh:mm a"))),
                normalFont
        );

        addDivider(document);
        addItemsTable(document, data.items, smallBoldFont, itemFont, moneyFont);
        addDivider(document);

        addTextRow(document, "عدد الأصناف: " + ltr(data.items.size()), normalFont, Element.ALIGN_CENTER, true);
        addDivider(document);

        addSummaryRow(document, "إجمالي المشتريات:", formatMoney(data.subtotal) + " ج.م", normalFont, moneyFont);
        addSummaryRow(document, "خصم إضافي:", formatMoney(data.discount) + " ج.م", normalFont, moneyFont);
        addDivider(document);
        addSummaryRow(document, "الاجمالي:", formatMoney(data.netTotal) + " ج.م", boldFont, boldFont);
        addDivider(document);
        addSummaryRow(document, "المدفوع:", formatMoney(data.paid) + " ج.م", normalFont, moneyFont);
        addSummaryRow(document, "المتبقي:", formatMoney(data.remaining) + " ج.م", normalFont, moneyFont);
        addDivider(document);

        addTextRow(document, "شكراً لزيارتكم - نتمنى رؤيتكم مجدداً", normalFont, Element.ALIGN_CENTER, true);
        addDivider(document);

        document.close();
        return pdfPath;
    }

    private static void addItemsTable(Document document, List<ReceiptItem> items, Font headerFont, Font itemFont, Font moneyFont) throws Exception {
        PdfPTable table = new PdfPTable(new float[]{1.2f, 1.2f, 1.2f, 5.5f});
        table.setWidthPercentage(100);
        table.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);

        table.addCell(tableCell("الصنف", headerFont, Element.ALIGN_CENTER, true, true));
        table.addCell(tableCell("الكمية", headerFont, Element.ALIGN_CENTER, true, true));
        table.addCell(tableCell("السعر", headerFont, Element.ALIGN_CENTER, true, true));
        table.addCell(tableCell("الإجمالي", headerFont, Element.ALIGN_CENTER, true, true));

        for (ReceiptItem item : items) {
            // خليها ALIGN_LEFT عشان تبعد عن عمود الكمية اللي على شمالها
            PdfPCell nameCell = tableCell(item.name, itemFont, Element.ALIGN_LEFT, false, false);
            nameCell.setRunDirection(PdfWriter.RUN_DIRECTION_LTR);
            nameCell.setNoWrap(false);

            PdfPCell qtyCell = tableCell(ltr(item.quantity), moneyFont, Element.ALIGN_CENTER, false, true);
            PdfPCell priceCell = tableCell(ltr(formatMoney(item.price)), moneyFont, Element.ALIGN_CENTER, false, true);
            PdfPCell totalCell = tableCell(ltr(formatMoney(item.total)), moneyFont, Element.ALIGN_CENTER, false, true);

            table.addCell(nameCell);
            table.addCell(qtyCell);
            table.addCell(priceCell);
            table.addCell(totalCell);
        }

        document.add(table);
    }

    private static void addLabelValueRow(Document document, String label, String value, Font labelFont, Font valueFont) throws Exception {
        PdfPTable table = new PdfPTable(new float[]{1.45f, 1.55f});
        table.setWidthPercentage(100);
        table.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.addCell(infoCell(label, labelFont, Element.ALIGN_RIGHT));
        table.addCell(infoCell(value, valueFont, Element.ALIGN_LEFT));
        document.add(table);
    }

    private static void addTwoInfoRow(Document document, String rightText, String leftText, Font font) throws Exception {
        PdfPTable table = new PdfPTable(new float[]{1f, 1f});
        table.setWidthPercentage(100);
        table.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.addCell(infoCell(rightText, font, Element.ALIGN_RIGHT));
        table.addCell(infoCell(leftText, font, Element.ALIGN_LEFT));
        document.add(table);
    }

    private static void addSummaryRow(Document document, String label, String value, Font labelFont, Font valueFont) throws Exception {
        PdfPTable table = new PdfPTable(new float[]{2.75f, 1.45f});
        table.setWidthPercentage(100);
        table.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.addCell(tableCell(label, labelFont, Element.ALIGN_RIGHT, false, true));
        table.addCell(tableCell(ltr(value), valueFont, Element.ALIGN_LEFT, false, true));
        document.add(table);
    }

    private static void addTextRow(Document document, String text, Font font, int alignment, boolean rtl) throws Exception {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.addCell(tableCell(text, font, alignment, false, rtl));
        document.add(table);
    }

    private static PdfPCell infoCell(String text, Font font, int alignment) {
        return tableCell(text, font, alignment, false, true);
    }

    private static PdfPCell tableCell(String text, Font font, int alignment, boolean header, boolean rtl) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setRunDirection(rtl ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingTop(header ? 3f : 2f);
        cell.setPaddingBottom(header ? 5f : 3f);
        cell.setPaddingLeft(1.5f);
        cell.setPaddingRight(1.5f);
        cell.setNoWrap(false);
        return cell;
    }

    private static void addDivider(Document document) throws Exception {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell(new Phrase(""));
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderWidthBottom(0.8f);
        cell.setPaddingTop(0f);
        cell.setPaddingBottom(6f);
        cell.setPaddingLeft(0f);
        cell.setPaddingRight(0f);
        table.addCell(cell);
        document.add(table);
    }

    private static boolean tryPrintPdf(String pdfPath) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.PRINT)) {
                Desktop.getDesktop().print(new File(pdfPath));
                return true;
            }
        } catch (Exception ex) {
            System.out.println("PDF print failed: " + ex.getMessage());
        }
        return false;
    }

    private static String ltr(Object value) {
        return LRM + String.valueOf(value) + LRM;
    }

    private static String formatMoney(double value) {
        return String.format("%.2f", value);
    }

    private static String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now();
        }

        try {
            return LocalDateTime.parse(value.replace(' ', 'T'));
        } catch (Exception ignored) {
            return LocalDateTime.now();
        }
    }

    private static class ReceiptData {
        private int invoiceId;
        private LocalDateTime invoiceDateTime;
        private String customerName;
        private String customerPhone;
        private double subtotal;
        private double discount;
        private double netTotal;
        private double paid;
        private double remaining;
        private final List<ReceiptItem> items = new ArrayList<>();
    }

    private static class ReceiptItem {
        private String name;
        private int quantity;
        private double price;
        private double total;
    }

    public static class PrintResult {
        private final String pdfPath;
        private final boolean printed;

        public PrintResult(String pdfPath, boolean printed) {
            this.pdfPath = pdfPath;
            this.printed = printed;
        }

        public String getPdfPath() {
            return pdfPath;
        }

        public boolean isPrinted() {
            return printed;
        }
    }
}
