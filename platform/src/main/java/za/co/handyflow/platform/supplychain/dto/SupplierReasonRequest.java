package za.co.handyflow.platform.supplychain.dto;

/** Reason / note for a supplier lifecycle action. Required for everything except approve. */
public record SupplierReasonRequest(String reason) {}
