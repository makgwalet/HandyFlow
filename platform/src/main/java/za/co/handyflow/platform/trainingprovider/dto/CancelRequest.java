package za.co.handyflow.platform.trainingprovider.dto;

/** Reusable reason-only body for cancel/revoke actions across the module. */
public record CancelRequest(String reason) {}
