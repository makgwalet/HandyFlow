package za.co.handyflow.platform.training.dto;

/** Reusable reason-only body for cancel/revoke actions across the module. */
public record CancelRequest(String reason) {}
