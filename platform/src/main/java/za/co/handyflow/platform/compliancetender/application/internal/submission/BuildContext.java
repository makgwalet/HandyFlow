package za.co.handyflow.platform.compliancetender.application.internal.submission;

import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Everything a section source may need for one build. The text fields and document choice come from the
 * person building; the rest from the caller's rights. {@code mayIncludePricing} is COMPLIANCE_MANAGE/ADMIN
 * (ADR-005 decision 2).
 *
 * @param companyProfileText null = use the current company profile; non-blank = customised for this tender
 * @param documentIds        the compliance documents chosen for the Supporting documents section
 */
public record BuildContext(TenantId tenantId, UUID tenderId, boolean mayIncludePricing, String coverLetterText,
                           String companyProfileText, List<UUID> documentIds) {

    public BuildContext {
        documentIds = documentIds == null ? List.of() : List.copyOf(documentIds);
    }
}
