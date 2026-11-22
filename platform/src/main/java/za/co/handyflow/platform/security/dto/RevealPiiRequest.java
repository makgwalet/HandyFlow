package za.co.handyflow.platform.security.dto;

/** field: ID_NUMBER or BANK_ACCOUNT. reason: at least 10 characters, written to the audit log. */
public record RevealPiiRequest(String field, String reason) {}
