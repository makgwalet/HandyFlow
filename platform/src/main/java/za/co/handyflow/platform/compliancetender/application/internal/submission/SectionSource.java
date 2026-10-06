package za.co.handyflow.platform.compliancetender.application.internal.submission;

import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * Where a section's content comes from (readiness, pricing, personnel, evidence, typed text...). One
 * implementation per section type, registered in the {@link SectionCatalogue}'s service wiring. A source
 * reads through the module's own services and facades; the builder never reaches into repositories.
 */
public interface SectionSource {

    SectionType type();

    /** @param mayIncludePricing whether the caller has COMPLIANCE_MANAGE/ADMIN (ADR-005 decision 2) */
    SectionContent load(TenantId tenantId, UUID tenderId, boolean mayIncludePricing);
}
