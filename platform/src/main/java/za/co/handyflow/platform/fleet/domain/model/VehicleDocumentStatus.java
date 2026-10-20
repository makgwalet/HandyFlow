package za.co.handyflow.platform.fleet.domain.model;

/** Whether a document is the live one or has been replaced by a newer version. "Expired" is computed from the expiry date, not stored. */
public enum VehicleDocumentStatus {
    CURRENT, SUPERSEDED
}