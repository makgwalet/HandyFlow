package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.domain.model.ClinicPatient;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPatientRepository;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Sending an issued document to the patient (patch 0203, CLINIC-DEC-024): a separate, deliberate step with its own permission
 * (CLINIC_DOCUMENT_EMAIL), never a side effect of issuing. Only the kept copy is sent, only to the patient's own address on file,
 * and every send is recorded. A withdrawn document is never sent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicDocumentDeliveryService {

    private final ClinicIssuedDocumentService issued;
    private final ClinicPatientRepository patientRepo;
    private final TenantFacade tenantFacade;
    private final EmailService emailService;

    public record Sent(UUID documentId, String number, String recipient, Instant queuedAt) {}

    /** A failed send is recorded and kept: the transaction is not rolled back for it. */
    @Transactional(noRollbackFor = IllegalStateException.class)
    public Sent emailToPatient(TenantId t, UUID patientId, UUID documentId, UUID by) {
        ClinicPatient p = patientRepo.findActiveById(t, patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId.toString()));
        var copy = issued.copy(t.getValue(), patientId, documentId);
        if (copy.withdrawn()) throw new IllegalStateException(copy.number() + " has been withdrawn and cannot be sent.");
        String to = DocumentDeliveryRules.address(p.getEmail());
        if (to == null) throw new IllegalStateException("The patient has no usable email address on file. Add one to the profile first.");
        String practice = tenantFacade.findTenantDetails(t).map(d -> d.companyName()).orElse(null);
        var mail = DocumentDeliveryRules.email(copy.type(), copy.number(), p.getFirstName(), practice);
        try {
            emailService.sendWithAttachment(to, mail.subject(), mail.html(), mail.fileName(), copy.pdf());
        } catch (RuntimeException e) {
            issued.recordDelivery(t.getValue(), documentId, "EMAIL", to, "FAILED", trim(e.getMessage()), by);
            throw new IllegalStateException("The email could not be sent: " + e.getMessage());
        }
        issued.recordDelivery(t.getValue(), documentId, "EMAIL", to, "QUEUED", null, by);
        log.info("Queued {} {} by email for patient={}", copy.type(), copy.number(), patientId);
        return new Sent(documentId, copy.number(), to, Instant.now());
    }

    private static String trim(String s) { return s == null ? null : s.length() > 500 ? s.substring(0, 500) : s; }
}
