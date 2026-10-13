package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.application.internal.ClinicIssuedDocumentService.Issued;
import za.co.handyflow.platform.clinic.domain.model.ClinicPatient;
import za.co.handyflow.platform.clinic.domain.repository.ClinicConsultationRepository;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPatientRepository;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * The referral letter. Since patch 0200 it is made by {@link ClinicClinicalDocumentService} (letterhead, number, hash, verification code);
 * what remains here is the copy emailed to the patient, which the letter has always sent when the patient has an email address.
 * Delivery is a side effect of issuing, as before; CLINIC-DEC-024 wants delivery to be its own step, which is still to do.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicReferralPdfService {

    private final ClinicClinicalDocumentService documents;
    private final ClinicConsultationRepository consultationRepo;
    private final ClinicPatientRepository patientRepo;
    private final EmailService emailService;

    @Transactional
    public Issued issue(TenantId tenantId, UUID consultationId, String specialistName, String specialty, String reason, String urgency,
                        String additionalNotes, UUID letterheadId, UUID by) {
        Issued doc = documents.issueReferral(tenantId, consultationId, specialistName, specialty, reason, urgency, additionalNotes, letterheadId, by);
        consultationRepo.findActiveById(tenantId, consultationId)
                .flatMap(c -> patientRepo.findActiveById(tenantId, c.getPatientId()))
                .ifPresent(p -> sendReferralEmail(p, doc.pdf(), specialistName, specialty));
        return doc;
    }

    private void sendReferralEmail(ClinicPatient patient, byte[] pdfBytes, String specialistName, String specialty) {
        try {
            if (patient.getEmail() == null || patient.getEmail().isBlank()) return;
            String greetingName = patient.getFirstName() != null ? patient.getFirstName() : "there";
            String referredTo = (specialistName != null && !specialistName.isBlank() ? specialistName : "a specialist")
                    + (specialty != null && !specialty.isBlank() ? " (" + specialty + ")" : "");
            String html = "<p>Dear " + greetingName + ",</p>"
                    + "<p>You have been referred to " + referredTo + ". A copy of the referral letter is attached "
                    + "for your records \u2014 please bring it to your appointment.</p>"
                    + "<p>If you have any questions, please contact the practice.</p>";
            emailService.sendWithAttachment(patient.getEmail(), "Your referral letter", html, "referral-letter.pdf", pdfBytes);
            log.info("Sent referral letter patient={}", patient.getId());
        } catch (Exception e) {
            log.warn("Referral letter not emailed for patient={}: {}", patient.getId(), e.getMessage());
        }
    }
}
