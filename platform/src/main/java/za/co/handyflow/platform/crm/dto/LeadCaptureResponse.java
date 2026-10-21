package za.co.handyflow.platform.crm.dto;

/** status is CREATED, DUPLICATE (already known, nothing added) or IGNORED. */
public record LeadCaptureResponse(String status) {}
