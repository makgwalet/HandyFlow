package za.co.handyflow.platform.clinic.domain.model;

/** Why a doctor sent a consultation back to the nurse. */
public enum HandoffReturnReason {
    MISSING_OBSERVATIONS,
    INCOMPLETE_HISTORY,
    WRONG_PATIENT_OR_VISIT,
    CLARIFICATION_NEEDED,
    OTHER
}
