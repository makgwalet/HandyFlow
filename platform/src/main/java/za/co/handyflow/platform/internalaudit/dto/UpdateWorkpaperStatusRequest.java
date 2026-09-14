package za.co.handyflow.platform.internalaudit.dto;

import jakarta.validation.constraints.NotBlank;

// status: PREPARE | REVIEW | SIGN_OFF | REOPEN -- an action, not a raw
// status string, matching AuditWorkpaperFile's own domain methods
// (markPrepared/markReviewed/signOff/reopen) rather than letting the
// caller set reviewStatus to an arbitrary value directly.
public record UpdateWorkpaperStatusRequest(@NotBlank String action) {}
