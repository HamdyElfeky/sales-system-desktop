public class SaleItem {
    private String productName;
    private int quantity;
    private double unitPrice;

    public SaleItem(String name, int qty, double price) {
        this.productName = name;
        this.quantity = qty;
        this.unitPrice = price;
    }

    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }
    public double getUnitPrice() { return unitPrice; }
    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
