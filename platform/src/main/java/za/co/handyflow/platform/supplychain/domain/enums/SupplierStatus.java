package za.co.handyflow.platform.supplychain.domain.enums;

/**
 * Supplier standing.
 * BLACKLISTED is a hard stop — the service layer rejects new POs
 * against a blacklisted supplier before they are created.
 */
public enum SupplierStatus {
    ACTIVE,
    INACTIVE,
    BLACKLISTED
}