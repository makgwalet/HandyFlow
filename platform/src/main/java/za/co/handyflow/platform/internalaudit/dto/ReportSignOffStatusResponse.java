package za.co.handyflow.platform.internalaudit.dto;

// Deliberately thin -- the real detail (per-step approver/status/
// comment) already lives in ApprovalRequestResponse/ApprovalStepResponse
// (approvals module), reused directly rather than duplicated into a
// parallel shape. This wraps just enough for the engagement list/detail
// view to show a status badge without a second round trip.
public record ReportSignOffStatusResponse(String status, String approvalMode) {}
