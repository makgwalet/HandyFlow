package za.co.handyflow.platform.legalpractice.dto;

/** {@code note} is optional, but required when some client balances disagree with the ledger. */
public record ApproveReconciliationRequest(String note) {}
