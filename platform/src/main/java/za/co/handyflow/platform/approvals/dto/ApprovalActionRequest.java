package za.co.handyflow.platform.approvals.dto;

/** comment is optional on approve, worth encouraging (not requiring) on reject — see ApprovalController. */
public record ApprovalActionRequest(String comment) {}