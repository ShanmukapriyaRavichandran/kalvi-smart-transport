public class FeeRecord {

    private String studentId;
    private double amount;
    private boolean paid;
    private String paymentMethod;
    private String transactionId;
    private String paymentDate;
    private String receiptNo;

    public FeeRecord(String studentId, double amount, boolean paid) {
        this(studentId, amount, paid, null, null, null, null);
    }

    public FeeRecord(String studentId, double amount, boolean paid,
                     String paymentMethod, String transactionId,
                     String paymentDate, String receiptNo) {
        this.studentId = studentId;
        this.amount = amount;
        this.paid = paid;
        this.paymentMethod = paymentMethod;
        this.transactionId = transactionId;
        this.paymentDate = paymentDate;
        this.receiptNo = receiptNo;
    }

    public String getStudentId() {
        return studentId;
    }

    public double getAmount() {
        return amount;
    }

    public boolean isPaid() {
        return paid;
    }

    public void setPaid(boolean paid) {
        this.paid = paid;
    }

    public String getPaymentMethod() {
        return paymentMethod != null ? paymentMethod : (paid ? "Online Payment" : "Pending");
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getTransactionId() {
        return transactionId != null ? transactionId : (paid ? "TXN-KLV-VERIFIED" : "--");
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getPaymentDate() {
        return paymentDate != null ? paymentDate : "--";
    }

    public void setPaymentDate(String paymentDate) {
        this.paymentDate = paymentDate;
    }

    public String getReceiptNo() {
        return receiptNo != null ? receiptNo : (paid ? "RCPT-2026-KLV" : "--");
    }

    public void setReceiptNo(String receiptNo) {
        this.receiptNo = receiptNo;
    }
}