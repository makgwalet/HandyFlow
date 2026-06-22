package za.co.handyflow.platform.invoicing.domain.model;

public enum InvoiceStatus {
    DRAFT,
    ISSUED,
    PARTIALLY_PAID,
    PAID,
    OVERPAID,
    OVERDUE,
    CANCELLED
}
