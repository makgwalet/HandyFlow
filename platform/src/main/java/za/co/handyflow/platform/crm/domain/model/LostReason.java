package za.co.handyflow.platform.crm.domain.model;

/** Why a lead was lost. Required when a lead is moved to LOST. */
public enum LostReason {
    PRICE,
    COMPETITOR,
    NO_RESPONSE,
    NO_BUDGET,
    NOT_A_FIT,
    TIMING,
    /** Needs a note saying what happened. */
    OTHER
}
