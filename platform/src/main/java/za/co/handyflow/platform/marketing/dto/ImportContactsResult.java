package za.co.handyflow.platform.marketing.dto;

public record ImportContactsResult(int imported, int skippedSuppressed, int skippedInvalid) {}