package za.co.handyflow.platform.collectionsagency.dto;

/** Shared shape for markDefaulted/cancel — both just take an optional free-text reason. */
public record ReasonRequest(String reason) {}
