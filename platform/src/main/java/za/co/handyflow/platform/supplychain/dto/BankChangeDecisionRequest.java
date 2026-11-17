package za.co.handyflow.platform.supplychain.dto;

/**
 * verify: method = CALLBACK | SIGNED_LETTER | BANK_CONFIRMATION | IN_PERSON, note = how it was verified.
 * reject: note = reason (method ignored).
 */
public record BankChangeDecisionRequest(String method, String note) {}
