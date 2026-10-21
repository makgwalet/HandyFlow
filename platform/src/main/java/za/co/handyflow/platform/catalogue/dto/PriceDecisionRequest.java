package za.co.handyflow.platform.catalogue.dto;

/** Note for an approval (optional), a rejection (required) or a cancellation (optional). */
public record PriceDecisionRequest(String note) {}
