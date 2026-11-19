package za.co.handyflow.platform.legalcompliance.domain.model;

/** Acceptance is its own action (ACCEPTED status), so it is not a treatment strategy. */
public enum TreatmentStrategy {
    MITIGATE, TRANSFER, AVOID
}
