package za.co.handyflow.platform.legalpractice.dto;

/** {@code selfApprovalReason} is only used when the approver is also the requester, and only when the firm allows that. */
public record ApproveTrustPaymentRequest(String selfApprovalReason) {}
