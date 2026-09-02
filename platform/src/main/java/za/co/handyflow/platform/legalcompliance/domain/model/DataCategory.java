package za.co.handyflow.platform.legalcompliance.domain.model;

/**
 * Which population of data subjects a PopiaProcessingActivity or
 * DsarRequest concerns. CUSTOMER exists here too (not just deferred to
 * CRM's own CustomerConsent) because the org-wide processing-activity
 * register needs to list customer-data processing alongside employee/
 * supplier/marketing processing on ONE register — see package-info.
 */
public enum DataCategory {
    CUSTOMER,
    EMPLOYEE,
    SUPPLIER,
    MARKETING_CONTACT,
    OTHER
}
