package za.co.handyflow.platform.crm.dto;

/** The reason is optional; it is kept on the timeline and in the audit log. */
public record UndoConversionRequest(String reason) {}
