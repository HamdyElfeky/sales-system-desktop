public class Product {
    private int productId;
    private String productName;
    private double unitPrice;
    private Integer quantity;
    private String category;
    private String barcode;

    public Product(int productId, String productName, double unitPrice, Integer quantity, String category, String barcode) {
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.category = category;
        this.barcode = barcode;
    }

    public int getProductId() { return productId; }
    public String getProductName() { return productName; }
    public double getUnitPrice() { return unitPrice; }
    public Integer getQuantity() { return quantity; }
    public String getQuantityDisplay() { return quantity == null ? "N/A" : String.valueOf(quantity); }
    public boolean hasTrackedQuantity() { return quantity != null; }
    public String getCategory() { return category; }
    public String getBarcode() { return barcode; }

    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    @Override
    public String toString() {
        return productName + " (" + unitPrice + " EGP)";
    }
}
