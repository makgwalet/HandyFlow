package za.co.handyflow.platform.invoicing.domain.model;

public enum InvoiceType {
    STANDARD,
    RECURRING_INSTANCE,  // spawned automatically by a RecurringSchedule
    RETAINER             // upfront / committed-hours invoice
}
