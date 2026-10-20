package za.co.handyflow.platform.fleet.domain.model;

/** Traffic-light status of one compliance item. Lower severity() = worse. */
public enum ComplianceStatus {
    EXPIRED(0), MISSING(1), DUE(2), EXPIRING_SOON(3), VALID(4), NOT_APPLICABLE(5);

    private final int severity;

    ComplianceStatus(int severity) { this.severity = severity; }

    public int severity() { return severity; }
}