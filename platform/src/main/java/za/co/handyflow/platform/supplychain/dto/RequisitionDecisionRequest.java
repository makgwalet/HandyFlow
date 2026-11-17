package za.co.handyflow.platform.supplychain.dto;

/** note: optional for approve, required for reject / cancel. */
public record RequisitionDecisionRequest(String note) {}
