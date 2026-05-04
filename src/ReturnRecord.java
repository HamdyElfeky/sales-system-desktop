import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class ReturnRecord {
    private final IntegerProperty returnId;
    private final IntegerProperty invoiceId;
    private final StringProperty productName;
    private final IntegerProperty qtyReturned;
    private final StringProperty dateReturned;
    private final StringProperty customerName;
    private final StringProperty customerPhone;

    public ReturnRecord(int returnId, int invoiceId, String productName, int qtyReturned, String dateReturned,
                        String customerName, String customerPhone) {
        this.returnId = new SimpleIntegerProperty(returnId);
        this.invoiceId = new SimpleIntegerProperty(invoiceId);
        this.productName = new SimpleStringProperty(productName);
        this.qtyReturned = new SimpleIntegerProperty(qtyReturned);
        this.dateReturned = new SimpleStringProperty(dateReturned);
        this.customerName = new SimpleStringProperty(customerName == null ? "" : customerName);
        this.customerPhone = new SimpleStringProperty(customerPhone == null ? "" : customerPhone);
    }

    public IntegerProperty returnIdProperty() {
        return returnId;
    }

    public IntegerProperty invoiceIdProperty() {
        return invoiceId;
    }

    public StringProperty productNameProperty() {
        return productName;
    }

    public IntegerProperty qtyReturnedProperty() {
        return qtyReturned;
    }

    public StringProperty dateReturnedProperty() {
        return dateReturned;
    }

    public StringProperty customerNameProperty() {
        return customerName;
    }

    public StringProperty customerPhoneProperty() {
        return customerPhone;
    }
}
