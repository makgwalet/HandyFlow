package za.co.handyflow.platform.compliancetender.application.internal.submission;

import za.co.handyflow.platform.shared.TenantId;

/**
 * The reusable company-profile text (ADR-005 decision 3). Today it is built from the tenant's own details;
 * when a shared Business Profile exists it replaces this implementation and the builder does not change.
 */
public interface CompanyProfileProvider {

    /** Plain text, one fact per line; empty when nothing is recorded. */
    String currentProfileText(TenantId tenantId);
}
