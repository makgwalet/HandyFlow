package za.co.handyflow.platform.clinic.dto;

/** @param overrideReason reason for signing with a required step missing; @param redFlagOverrideReason reason for signing with red flags not reviewed (Q-5) */
public record SignRequest(String overrideReason, String redFlagOverrideReason) {
    public SignRequest(String overrideReason) { this(overrideReason, null); }
}
