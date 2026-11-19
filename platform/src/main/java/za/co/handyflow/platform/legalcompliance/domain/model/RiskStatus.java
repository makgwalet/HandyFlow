package za.co.handyflow.platform.legalcompliance.domain.model;

/** IDENTIFIED (no assessment yet) → ASSESSED → TREATING or ACCEPTED → CLOSED. */
public enum RiskStatus {
    IDENTIFIED, ASSESSED, TREATING, ACCEPTED, CLOSED
}
