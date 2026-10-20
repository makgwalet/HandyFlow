package za.co.handyflow.platform.fleet.domain.model;

/** Answer to one checklist item. PENDING means not yet answered. */
public enum InspectionOutcome {
    PENDING, PASS, FAIL, NOT_APPLICABLE
}