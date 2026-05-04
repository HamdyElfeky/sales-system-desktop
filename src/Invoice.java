import java.time.LocalDate;

public class Invoice {
    private int invoiceId;

    private double totalAmount;
    private LocalDate date;

    public Invoice(int invoiceId, double totalAmount, LocalDate date) {
        this.invoiceId = invoiceId;

        this.totalAmount = totalAmount;
        this.date = date;
    }

    public int getInvoiceId() {
        return invoiceId;
    }



    public double getTotalAmount() {
        return totalAmount;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setInvoiceId(int invoiceId) {
        this.invoiceId = invoiceId;
    }



    public void setTotalAmount(double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    @Override
    public String toString() {
        return "🧾 " + " - " + totalAmount + " EGP - " + date + " | ID:" + invoiceId;
    }
}
