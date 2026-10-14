package za.co.handyflow.platform.accountant.dto;

/** {@code note} is a free note when preparing, reviewing or signing off, and the reason when reopening. */
public record EngagementActionRequest(String note) {}
