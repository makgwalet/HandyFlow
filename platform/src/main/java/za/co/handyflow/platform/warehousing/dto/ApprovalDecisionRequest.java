package za.co.handyflow.platform.warehousing.dto;

import jakarta.validation.constraints.Size;

/** The note a second person leaves when approving or rejecting. Required (5+ characters) only when rejecting. */
public record ApprovalDecisionRequest(@Size(max = 500) String note) {}
