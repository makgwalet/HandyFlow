package za.co.handyflow.platform.supplychain.domain.enums;

/**
 * Compliance document types a supplier can be asked for. Which ones are REQUIRED is tenant policy
 * (app.scm.supplier.required-documents), not code. {@link #isExpiryRequired()} types lose their meaning
 * without an expiry date, so one must be captured.
 */
public enum SupplierDocType {
    TAX_CLEARANCE(true),
    VAT_REGISTRATION(false),
    COID_GOOD_STANDING(true),
    BBBEE_CERTIFICATE(true),
    COMPANY_REGISTRATION(false),
    BANK_CONFIRMATION(false),
    INSURANCE_CERTIFICATE(true),
    PROFESSIONAL_REGISTRATION(true),
    ISO_CERTIFICATE(true),
    OTHER(false);

    private final boolean expiryRequired;

    SupplierDocType(boolean expiryRequired) { this.expiryRequired = expiryRequired; }

    public boolean isExpiryRequired() { return expiryRequired; }
}
