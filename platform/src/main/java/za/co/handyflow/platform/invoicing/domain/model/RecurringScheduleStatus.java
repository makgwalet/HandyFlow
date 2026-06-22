package za.co.handyflow.platform.invoicing.domain.model;

public enum RecurringScheduleStatus {
    ACTIVE,
    PAUSED,
    CANCELLED,
    COMPLETED    // end date reached
}