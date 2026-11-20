package za.co.handyflow.platform.legalpractice.dto;

/** A note is required when the person approving also submitted it (where the firm allows that). */
public record ApproveKycRequest(String note) {
}
