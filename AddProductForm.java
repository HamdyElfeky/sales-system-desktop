import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AddProductForm extends Application {

    private static final String DDG_BOT_MARKER = "Unfortunately, bots use DuckDuckGo too";
    private static final String[] USER_AGENTS = new String[] {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:138.0) Gecko/20100101 Firefox/138.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.4 Safari/605.1.15",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"
    };
    private static final String[] SEARCH_SOURCE_NAMES = new String[] {
            "Startpage",
            "DuckDuckGo",
            "DuckDuckGo Lite"
    };
    private static final String[] SEARCH_SOURCE_URLS = new String[] {
            "https://www.startpage.com/sp/search?query=%s",
            "https://html.duckduckgo.com/html/?q=%s",
            "https://lite.duckduckgo.com/lite/?q=%s"
    };

    private static final Pattern[] SPECIFIC_TITLE_PATTERNS = new Pattern[] {
            Pattern.compile("<a[^>]*class=\"[^\"]*result__a[^\"]*\"[^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
            Pattern.compile("<a[^>]*class=\"[^\"]*result-link[^\"]*\"[^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
            Pattern.compile("<h2[^>]*class=\"[^\"]*result__title[^\"]*\"[^>]*>\\s*<a[^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
    };
    private static final Pattern[] SPECIFIC_SNIPPET_PATTERNS = new Pattern[] {
            Pattern.compile("<a[^>]*class=\"[^\"]*result__snippet[^\"]*\"[^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
            Pattern.compile("<div[^>]*class=\"[^\"]*result__snippet[^\"]*\"[^>]*>(.*?)</div>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
            Pattern.compile("<td[^>]*class=\"[^\"]*result-snippet[^\"]*\"[^>]*>(.*?)</td>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
    };
    private static final Pattern[] GENERIC_TITLE_PATTERNS = new Pattern[] {
            Pattern.compile("<meta[^>]*property=\"og:title\"[^>]*content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
            Pattern.compile("<meta[^>]*name=\"title\"[^>]*content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
            Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
            Pattern.compile("<h1[^>]*>(.*?)</h1>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
            Pattern.compile("<h2[^>]*>(.*?)</h2>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
            Pattern.compile("<a[^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
    };
    private static final Pattern[] GENERIC_SNIPPET_PATTERNS = new Pattern[] {
            Pattern.compile("<meta[^>]*name=\"description\"[^>]*content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
            Pattern.compile("<meta[^>]*property=\"og:description\"[^>]*content=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
            Pattern.compile("<p[^>]*>(.*?)</p>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
            Pattern.compile("<div[^>]*>(.*?)</div>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
    };
    private static final Pattern JSON_TITLE_PATTERN = Pattern.compile("\"title\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");
    private static final Pattern JSON_DESCRIPTION_PATTERN = Pattern.compile("\"description\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");
    private static final Pattern JSON_BRAND_PATTERN = Pattern.compile("\"brand\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();
    private final ExecutorService lookupExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "barcode-lookup");
            thread.setDaemon(true);
            return thread;
        }
    });
    private final AtomicLong lookupSequence = new AtomicLong();
    private final Map<String, ProductLookupResult> sessionLookupCache = new HashMap<>();

    private TextField txtName;
    private TextField txtPrice;
    private TextField txtQuantity;
    private TextField txtCategory;
    private TextField txtBarcode;
    private Label lblStatus;
    private PauseTransition barcodeSearchDelay;
    private StockForm stockForm;
    private ToggleGroup searchSourceGroup;

    public AddProductForm(StockForm stockForm) {
        this.stockForm = stockForm;
    }

    public AddProductForm() {
    }

    @Override
    public void start(Stage stage) {
        AppIcon.apply(stage);
        Label lblTitle = new Label("إضافة منتج جديد");

        txtName = new TextField();
        txtName.setPromptText("اسم المنتج");

        txtPrice = new TextField();
        txtPrice.setPromptText("سعر الوحدة");

        txtQuantity = new TextField();
        txtQuantity.setPromptText("الكمية في المخزون (اختياري)");

        txtCategory = new TextField();
        txtCategory.setPromptText("الفئة (اختياري)");

        txtBarcode = new TextField();
        txtBarcode.setPromptText("الباركود أو QR");

        Label lblSearchSource = new Label("Search With");
        RadioButton rbStartpage = new RadioButton("Startpage");
        RadioButton rbDuckDuckGo = new RadioButton("DuckDuckGo");
        searchSourceGroup = new ToggleGroup();
        rbStartpage.setToggleGroup(searchSourceGroup);
        rbDuckDuckGo.setToggleGroup(searchSourceGroup);
        rbStartpage.setSelected(true);
        HBox searchSourceBox = new HBox(12, lblSearchSource, rbStartpage, rbDuckDuckGo);

        setupBarcodeAutoSearch();

        Button btnSave = new Button("حفظ المنتج");
        btnSave.setOnAction(e -> saveProduct());

        Button btnImport = new Button("استيراد من Excel");
        btnImport.setOnAction(e -> importFromExcel(stage));

        lblStatus = new Label("جاهز");

        VBox fields = new VBox(10, txtName, txtPrice, txtQuantity, txtCategory, txtBarcode, searchSourceBox);
        HBox buttons = new HBox(10, btnSave, btnImport);
        VBox root = new VBox(15, lblTitle, fields, buttons, lblStatus);
        root.setPadding(new Insets(15));

        Scene scene = new Scene(root, 400, 430);
        stage.setTitle("إضافة منتج جديد");
        stage.setScene(scene);
        stage.show();
    }

    private void setupBarcodeAutoSearch() {
        barcodeSearchDelay = new PauseTransition(Duration.millis(700));
        barcodeSearchDelay.setOnFinished(event -> searchBarcodeOnline(txtBarcode.getText().trim()));

        txtBarcode.textProperty().addListener((observable, oldValue, newValue) -> {
            String barcode = newValue == null ? "" : newValue.trim();

            if (barcode.isEmpty()) {
                barcodeSearchDelay.stop();
                lblStatusSafe("جاهز");
                return;
            }

            if (barcode.length() < 6) {
                barcodeSearchDelay.stop();
                lblStatusSafe("اكتب الباركود كاملاً...");
                return;
            }

            lblStatusSafe("جارٍ تجهيز البحث عن الباركود...");
            barcodeSearchDelay.playFromStart();
        });
    }

    private void searchBarcodeOnline(String barcode) {
        if (barcode.isEmpty() || barcode.length() < 6) {
            return;
        }

        ProductLookupResult cachedResult = sessionLookupCache.get(barcode);
        if (cachedResult != null) {
            applyBarcodeSearchResult(barcode, lookupSequence.incrementAndGet(), cachedResult);
            return;
        }

        long requestId = lookupSequence.incrementAndGet();
        lblStatusSafe("جارٍ البحث أونلاين عن الباركود...");

        CompletableFuture
                .supplyAsync(() -> fetchProductInfo(barcode), lookupExecutor)
                .thenAccept(result -> Platform.runLater(() -> applyBarcodeSearchResult(barcode, requestId, result)))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        if (requestId == lookupSequence.get() && barcode.equals(txtBarcode.getText().trim())) {
                            lblStatus.setText("تعذر البحث أونلاين عن الباركود");
                        }
                    });
                    return null;
                });
    }

    private ProductLookupResult fetchProductInfo(String barcode) {
        try {
            String[] queries = new String[] {
                    "\"" + barcode + "\"",
                    barcode + " product name",
                    barcode + " item name",
                    barcode + " barcode product",
                    barcode + " product",
                    barcode
            };

            String bestFallback = null;
            int[] sourceIndexes = getSelectedSearchSourceIndexes();

            for (String query : queries) {
                for (int i : sourceIndexes) {
                    SearchExtraction extraction = fetchSearchEngineExtraction(query, SEARCH_SOURCE_NAMES[i], SEARCH_SOURCE_URLS[i]);
                    if (extraction == null) {
                        continue;
                    }

                    String titleCandidate = chooseBestCandidate(extraction.title, barcode, false);
                    if (titleCandidate != null) {
                        return cacheAndReturn(barcode, titleCandidate);
                    }

                    String snippetCandidate = chooseBestCandidate(extraction.snippet, barcode, true);
                    if (snippetCandidate != null) {
                        return cacheAndReturn(barcode, snippetCandidate);
                    }

                    if (bestFallback == null && extraction.snippet != null && !extraction.snippet.isBlank()) {
                        bestFallback = extraction.snippet;
                    }
                }
            }

            ProductLookupResult apiResult = fetchUpcItemDbFallback(barcode);
            if (apiResult != null) {
                sessionLookupCache.put(barcode, apiResult);
                return apiResult;
            }

            if (bestFallback != null) {
                return cacheAndReturn(barcode, shortenFallback(bestFallback, 100));
            }

            return ProductLookupResult.notFound();
        } catch (Exception ex) {
            System.out.println("[Lookup] fatal error for barcode " + barcode + ": " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
            ex.printStackTrace();
            return ProductLookupResult.error();
        }
    }

    private ProductLookupResult cacheAndReturn(String barcode, String productName) {
        ProductLookupResult result = ProductLookupResult.found(productName, "General");
        sessionLookupCache.put(barcode, result);
        return result;
    }

    private SearchExtraction fetchSearchEngineExtraction(String query, String sourceName, String sourceUrlTemplate) throws Exception {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = String.format(sourceUrlTemplate, encodedQuery);
        for (int attempt = 1; attempt <= 2; attempt++) {
            String userAgent = selectUserAgent(sourceName, query, attempt);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", userAgent)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9,ar;q=0.8")
                    .header("Cache-Control", "no-cache")
                    .header("Pragma", "no-cache")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "none")
                    .header("Upgrade-Insecure-Requests", "1")
                    .timeout(java.time.Duration.ofSeconds(12))
                    .GET()
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                System.out.println("[" + sourceName + "][attempt " + attempt + "] user-agent=" + userAgent);
                System.out.println("[" + sourceName + "][attempt " + attempt + "] status=" + response.statusCode());

                String body = response.body();
                String preview = body == null ? "" : body.replaceAll("\\s+", " ").trim();
                if (preview.length() > 100) {
                    preview = preview.substring(0, 100);
                }
                System.out.println("[" + sourceName + "][attempt " + attempt + "] preview=" + preview);

                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    continue;
                }

                if ((sourceName.startsWith("DuckDuckGo") && body.contains(DDG_BOT_MARKER)) || isBotProtectionPage(body)) {
                    System.out.println("[" + sourceName + "][attempt " + attempt + "] blocked/bot page");
                    continue;
                }

                String title = extractFirstTitle(body, query);
                String snippet = extractFirstSnippet(body, query);
                if ((title != null && !title.isBlank()) || (snippet != null && !snippet.isBlank())) {
                    return new SearchExtraction(title, snippet, false);
                }
            } catch (Exception ex) {
                System.out.println("[" + sourceName + "][attempt " + attempt + "] error=" + ex.getClass().getSimpleName() + ": " + ex.getMessage());
                ex.printStackTrace();
            }
        }

        return null;
    }

    private String selectUserAgent(String sourceName, String query, int attempt) {
        int seed = Math.abs((sourceName + "|" + query + "|" + attempt).hashCode());
        return USER_AGENTS[seed % USER_AGENTS.length];
    }

    private int[] getSelectedSearchSourceIndexes() {
        if (searchSourceGroup != null && searchSourceGroup.getSelectedToggle() instanceof RadioButton selectedButton) {
            if ("DuckDuckGo".equalsIgnoreCase(selectedButton.getText())) {
                return new int[] {1, 2};
            }
        }

        return new int[] {0};
    }

    private ProductLookupResult fetchUpcItemDbFallback(String barcode) {
        try {
            String url = "https://api.upcitemdb.com/prod/trial/lookup?upc=" + URLEncoder.encode(barcode, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .header("Accept", "application/json")
                    .timeout(java.time.Duration.ofSeconds(12))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }

            String body = response.body();
            String title = extractJsonValue(body, JSON_TITLE_PATTERN);
            String description = extractJsonValue(body, JSON_DESCRIPTION_PATTERN);
            String brand = extractJsonValue(body, JSON_BRAND_PATTERN);

            String candidate = chooseBestCandidate(title, barcode, false);
            if (candidate != null) {
                return ProductLookupResult.found(candidate, safeCategory(brand));
            }

            candidate = chooseBestCandidate(description, barcode, true);
            if (candidate != null) {
                return ProductLookupResult.found(candidate, safeCategory(brand));
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private String extractJsonValue(String body, Pattern pattern) {
        Matcher matcher = pattern.matcher(body);
        if (!matcher.find()) {
            return null;
        }

        String text = matcher.group(1)
                .replace("\\\"", "\"")
                .replace("\\/", "/")
                .replace("\\n", " ")
                .replace("\\r", " ")
                .replace("\\t", " ");
        return normalizeExtractedText(text);
    }

    private String extractFirstTitle(String html, String query) {
        for (Pattern pattern : SPECIFIC_TITLE_PATTERNS) {
            Matcher matcher = pattern.matcher(html);
            while (matcher.find()) {
                String cleaned = normalizeExtractedText(matcher.group(1));
                if (isUsableSearchText(cleaned, query)) {
                    return cleaned;
                }
            }
        }

        for (Pattern pattern : GENERIC_TITLE_PATTERNS) {
            Matcher matcher = pattern.matcher(html);
            while (matcher.find()) {
                String cleaned = normalizeExtractedText(matcher.group(1));
                if (isUsableSearchText(cleaned, query)) {
                    return cleaned;
                }
            }
        }

        return null;
    }

    private String extractFirstSnippet(String html, String query) {
        for (Pattern pattern : SPECIFIC_SNIPPET_PATTERNS) {
            Matcher matcher = pattern.matcher(html);
            while (matcher.find()) {
                String cleaned = normalizeExtractedText(matcher.group(1));
                if (isUsableSearchText(cleaned, query)) {
                    return cleaned;
                }
            }
        }

        for (Pattern pattern : GENERIC_SNIPPET_PATTERNS) {
            Matcher matcher = pattern.matcher(html);
            while (matcher.find()) {
                String cleaned = normalizeExtractedText(matcher.group(1));
                if (isUsableSearchText(cleaned, query)) {
                    return cleaned;
                }
            }
        }

        String bodyText = normalizeExtractedText(html);
        if (bodyText == null || bodyText.isBlank()) {
            return null;
        }

        String plainQuery = query.replace("\"", "").trim();
        if (!plainQuery.isBlank()) {
            int index = bodyText.toLowerCase().indexOf(plainQuery.toLowerCase());
            if (index >= 0) {
                int end = Math.min(bodyText.length(), index + 160);
                return bodyText.substring(index, end).trim();
            }
        }

        return shortenFallback(bodyText, 100);
    }

    private String chooseBestCandidate(String text, String barcode, boolean allowShortenedFallback) {
        if (text == null || text.isBlank()) {
            return null;
        }

        String cleaned = cleanProductName(text, barcode);
        if (isValidProductName(cleaned)) {
            return cleaned;
        }

        if (allowShortenedFallback) {
            String shortened = shortenFallback(cleaned, 100);
            if (!shortened.isBlank() && !looksLikeNoise(shortened)) {
                return shortened;
            }
        }

        return null;
    }

    private String normalizeExtractedText(String text) {
        if (text == null) {
            return null;
        }

        return stripCssNoise(decodeHtml(text))
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String cleanProductName(String productName, String barcode) {
        String cleaned = stripCssNoise(productName)
                .replace(" at DuckDuckGo", "")
                .replace(" | DuckDuckGo", "")
                .replace(" - DuckDuckGo", "")
                .replace(" | Startpage", "")
                .replace(" - Brave Search", "")
                .replace(barcode, "")
                .replaceAll("\\s+", " ")
                .replaceAll("^[\\-:|]+", "")
                .trim();

        return cleaned.isBlank() ? productName.trim() : cleaned;
    }

    private boolean isValidProductName(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }

        String lower = name.toLowerCase();
        if (looksLikeNoise(lower)) {
            return false;
        }

        if (lower.contains("ebay") || lower.contains("amazon") || lower.contains("walmart")) {
            return false;
        }

        return name.matches(".*[\\p{L}].*");
    }

    private boolean isUsableSearchText(String text, String query) {
        if (text == null || text.isBlank()) {
            return false;
        }

        if (looksLikeNoise(text)) {
            return false;
        }

        if (text.length() < 3) {
            return false;
        }

        String normalizedQuery = query == null ? "" : query.replace("\"", "").trim().toLowerCase();
        if (normalizedQuery.isBlank()) {
            return true;
        }

        for (String part : normalizedQuery.split("\\s+")) {
            if (part.length() >= 4 && text.toLowerCase().contains(part)) {
                return true;
            }
        }

        return text.matches(".*[\\p{L}].*");
    }

    private boolean looksLikeNoise(String text) {
        String lower = text.toLowerCase();
        return lower.contains("duckduckgo")
                || lower.contains("startpage")
                || lower.contains("brave search")
                || lower.contains("qwant")
                || lower.contains("mojeek")
                || lower.contains("captcha")
                || lower.contains("verify you are human")
                || lower.contains("access denied")
                || lower.contains("cloudflare");
    }

    private boolean isBotProtectionPage(String html) {
        String lower = html.toLowerCase();
        return lower.contains("captcha")
                || lower.contains("unusual traffic")
                || lower.contains("verify you are human")
                || lower.contains("access denied")
                || lower.contains("blocked")
                || lower.contains("cloudflare")
                || lower.contains("automated requests");
    }

    private String shortenFallback(String text, int maxLength) {
        if (text == null) {
            return "";
        }

        String cleaned = text.replaceAll("\\s+", " ").trim();
        if (cleaned.length() <= maxLength) {
            return cleaned;
        }

        int cut = cleaned.lastIndexOf(' ', maxLength);
        if (cut < 40) {
            cut = maxLength;
        }

        return cleaned.substring(0, cut).trim();
    }

    private String safeCategory(String category) {
        return category == null || category.isBlank() ? "General" : category.trim();
    }

    private String decodeHtml(String text) {
        return text
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ")
                .replaceAll("<[^>]+>", "");
    }

    private String stripCssNoise(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String cleaned = text
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?i)\\.[A-Za-z0-9_-]+\\s*\\{[^}]*}", " ")
                .replaceAll("(?i)@media\\s*\\([^)]*\\)\\s*\\{[^}]*}", " ")
                .replaceAll("(?i)[A-Za-z-]+\\s*:\\s*[^;{}]+;?", " ");

        int cssTailIndex = cleaned.lastIndexOf('}');
        if (cssTailIndex >= 0 && cssTailIndex < cleaned.length() - 1) {
            String tail = cleaned.substring(cssTailIndex + 1).trim();
            if (!tail.isBlank()) {
                cleaned = tail;
            }
        }

        return cleaned.replaceAll("\\s+", " ").trim();
    }

    private void applyBarcodeSearchResult(String barcode, long requestId, ProductLookupResult result) {
        if (requestId != lookupSequence.get() || !barcode.equals(txtBarcode.getText().trim())) {
            return;
        }

        if (result.status == LookupStatus.FOUND) {
            txtName.setText(result.productName);
            if (txtCategory.getText().trim().isEmpty()) {
                txtCategory.setText(result.category);
            }
            lblStatus.setText("تم العثور على المنتج تلقائياً");
            return;
        }

        if (result.status == LookupStatus.NOT_FOUND) {
            lblStatus.setText("لم يتم العثور على نتيجة لهذا الباركود");
            return;
        }

        lblStatus.setText("حدث خطأ أثناء البحث أونلاين");
    }

    private void lblStatusSafe(String text) {
        if (lblStatus != null) {
            lblStatus.setText(text);
        }
    }

    private void saveProduct() {
        String name = txtName.getText().trim();
        String priceStr = txtPrice.getText().trim();
        String qtyStr = txtQuantity.getText().trim();
        String category = txtCategory.getText().trim();
        String barcode = txtBarcode.getText().trim();

        if (name.isEmpty() || priceStr.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "من فضلك املأ جميع الحقول المطلوبة!").showAndWait();
            return;
        }

        try (Connection con = DatabaseConnection.getConnection()) {
            String sql = "INSERT INTO Products (ProductName, UnitPrice, Quantity, Category, Barcode) VALUES (?, ?, ?, ?, ?)";
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setString(1, name);
            ps.setDouble(2, Double.parseDouble(priceStr));
            if (qtyStr.isEmpty()) {
                ps.setNull(3, java.sql.Types.INTEGER);
            } else {
                ps.setInt(3, Integer.parseInt(qtyStr));
            }
            ps.setString(4, category);
            ps.setString(5, barcode);
            ps.executeUpdate();

            lblStatus.setText("تم حفظ المنتج بنجاح!");

            txtName.clear();
            txtPrice.clear();
            txtQuantity.clear();
            txtCategory.clear();
            txtBarcode.clear();

            txtBarcode.requestFocus();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "خطأ أثناء الحفظ:\n" + ex.getMessage()).showAndWait();
        }
    }

    private String getStringValue(Cell cell) {
        if (cell == null) {
            return "";
        }

        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue().trim();
        }

        if (cell.getCellType() == CellType.NUMERIC) {
            NumberFormat nf = NumberFormat.getInstance(Locale.US);
            nf.setGroupingUsed(false);
            return nf.format(cell.getNumericCellValue());
        }

        return "";
    }

    private double getNumericValue(Cell cell) {
        if (cell == null) {
            return 0;
        }

        switch (cell.getCellType()) {
            case NUMERIC:
                return cell.getNumericCellValue();
            case STRING:
                try {
                    return Double.parseDouble(cell.getStringCellValue().trim());
                } catch (NumberFormatException e) {
                    return 0;
                }
            default:
                return 0;
        }
    }

    private void importFromExcel(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("اختر ملف Excel");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ملفات Excel", "*.xlsx"));

        File file = fileChooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }

        int importedCount = 0;
        int skippedDuplicates = 0;
        int failedCount = 0;

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis);
             Connection con = DatabaseConnection.getConnection()) {

            con.setAutoCommit(false);
            Sheet sheet = workbook.getSheetAt(0);

            String sql = "INSERT INTO Products (ProductName, UnitPrice, Quantity, Category, Barcode) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                for (Row row : sheet) {
                    if (row.getRowNum() == 0) {
                        continue;
                    }

                    String name = getStringValue(row.getCell(0));
                    double price = getNumericValue(row.getCell(1));
                    int qty = (int) getNumericValue(row.getCell(2));
                    String category = getStringValue(row.getCell(3));
                    String barcode = getStringValue(row.getCell(4));

                    if (name.isEmpty()) {
                        continue;
                    }

                    try {
                        ps.setString(1, name);
                        ps.setDouble(2, price);
                        ps.setInt(3, qty);
                        ps.setString(4, category);
                        ps.setString(5, barcode);

                        int affected = ps.executeUpdate();
                        if (affected > 0) {
                            importedCount++;
                        } else {
                            failedCount++;
                        }
                    } catch (Exception rowEx) {
                        String msg = rowEx.getMessage() == null ? "" : rowEx.getMessage().toLowerCase();
                        if (msg.contains("unique") || msg.contains("constraint") || msg.contains("duplicate")) {
                            skippedDuplicates++;
                        } else {
                            failedCount++;
                        }
                    }
                }

                con.commit();
            }

            String resultMsg = "عدد المنتجات التي تم استيرادها: " + importedCount
                    + "\nالمنتجات المتخطاة (باركود مكرر): " + skippedDuplicates
                    + "\nالأخطاء: " + failedCount;
            new Alert(Alert.AlertType.INFORMATION, resultMsg).showAndWait();
            lblStatus.setText("تم الاستيراد من Excel");

            if (stockForm != null) {
                Platform.runLater(() -> stockForm.loadProducts());
            } else {
                try {
                    StockForm inst = StockForm.getInstance();
                    if (inst != null) {
                        Platform.runLater(inst::loadProducts);
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "خطأ أثناء استيراد الملف:\n" + ex.getMessage()).showAndWait();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }

    private enum LookupStatus {
        FOUND,
        NOT_FOUND,
        ERROR
    }

    private static class ProductLookupResult {
        private final LookupStatus status;
        private final String productName;
        private final String category;

        private ProductLookupResult(LookupStatus status, String productName, String category) {
            this.status = status;
            this.productName = productName;
            this.category = category;
        }

        private static ProductLookupResult found(String productName, String category) {
            return new ProductLookupResult(LookupStatus.FOUND, productName, category);
        }

        private static ProductLookupResult notFound() {
            return new ProductLookupResult(LookupStatus.NOT_FOUND, "", "");
        }

        private static ProductLookupResult error() {
            return new ProductLookupResult(LookupStatus.ERROR, "", "");
        }
    }

    private static class SearchExtraction {
        private final String title;
        private final String snippet;
        private final boolean botPage;

        private SearchExtraction(String title, String snippet, boolean botPage) {
            this.title = title;
            this.snippet = snippet;
            this.botPage = botPage;
        }
    }
}
