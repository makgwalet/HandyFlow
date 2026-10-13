package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * The visit summary. Since patch 0203 it is a Document Studio document ({@link ClinicClinicalDocumentService#visitSummary}): printed on the
 * letterhead, numbered and verifiable, one standing copy per visit, replaced by an updated copy when the visit changes (an addendum, say).
 * Kept as a class for its callers (the download endpoint and the visit-summary email).
 */
@Service
@RequiredArgsConstructor
public class ClinicConsultationSummaryPdfService {

    private final ClinicClinicalDocumentService documents;

    public byte[] generate(TenantId tenantId, UUID consultationId) {
        return documents.visitSummary(tenantId, consultationId, null, currentUserOrNull()).pdf();
    }

    private static UUID currentUserOrNull() {
        try { return za.co.handyflow.platform.shared.UserContext.getCurrentUserId(); } catch (RuntimeException e) { return null; }
    }
}
