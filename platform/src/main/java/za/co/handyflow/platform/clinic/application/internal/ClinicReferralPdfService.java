package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.application.internal.ClinicIssuedDocumentService.Issued;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * The referral letter. Made by {@link ClinicClinicalDocumentService} (letterhead, number, hash, verification code). Since patch 0203 it is
 * no longer emailed as part of issuing (CLINIC-DEC-024): sending a copy to the patient is a separate step, see {@link ClinicDocumentDeliveryService}.
 */
@Service
@RequiredArgsConstructor
public class ClinicReferralPdfService {

    private final ClinicClinicalDocumentService documents;

    @Transactional
    public Issued issue(TenantId tenantId, UUID consultationId, String specialistName, String specialty, String reason, String urgency,
                        String additionalNotes, UUID letterheadId, UUID by) {
        return documents.issueReferral(tenantId, consultationId, specialistName, specialty, reason, urgency, additionalNotes, letterheadId, by);
    }
}
