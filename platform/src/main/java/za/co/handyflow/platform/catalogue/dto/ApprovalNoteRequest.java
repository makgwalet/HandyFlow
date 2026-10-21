package za.co.handyflow.platform.catalogue.dto;

/** A free-text note on a request or a decision. Required (3 characters) when rejecting. */
public record ApprovalNoteRequest(String note) {}
