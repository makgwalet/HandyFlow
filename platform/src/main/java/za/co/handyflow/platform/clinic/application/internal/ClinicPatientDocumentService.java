package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.clinic.domain.model.*;
import za.co.handyflow.platform.clinic.domain.repository.*;
import za.co.handyflow.platform.clinic.dto.DocumentDtos.*;
import za.co.handyflow.platform.shared.FileStorageService;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.UserContext;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The documents register of a patient (patch 0163): files uploaded from outside, sick notes and referral letters when they
 * are issued, and the documents the system produces from the record on request (visit summary, prescription, lab report).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicPatientDocumentService {

    private final ClinicPatientRepository         patientRepo;
    private final ClinicConsultationRepository    consultationRepo;
    private final ClinicPrescriptionRepository    prescriptionRepo;
    private final ClinicLabResultRepository       labRepo;
    private final ClinicPatientDocumentRepository documentRepo;
    private final FileStorageService              storage;
    private final ClinicStaffNames                staffNames;

    @Transactional(readOnly = true)
    public RegisterResponse register(TenantId t, UUID patientId) {
        requirePatient(t, patientId);
        List<ClinicPatientDocument> stored = documentRepo.findLive(t, patientId);
        Map<UUID, String> names = staffNames.names(t.getValue(), stored.stream().map(ClinicPatientDocument::getCreatedBy).toList());

        List<RegisterItem> items = new ArrayList<>();
        for (ClinicPatientDocument d : stored) items.add(DocumentRegister.stored(d, names.get(d.getCreatedBy()), patientId));

        Set<UUID> withMedicines = prescriptionRepo.findByPatient(t, patientId).stream()
                .map(ClinicPrescription::getConsultationId).filter(Objects::nonNull).collect(Collectors.toSet());
        for (ClinicConsultation c : consultationRepo.findByPatient(t, patientId)) {
            if (!DocumentRegister.hasRecordDocuments(c.getStatus())) continue;
            items.addAll(DocumentRegister.forVisit(c.getId(), c.getConsultedAt(), c.getChiefComplaint(), withMedicines.contains(c.getId())));
        }
        for (ClinicLabResult l : labRepo.findByPatient(t, patientId)) {
            if (l.getPdfUrl() == null || l.getPdfUrl().isBlank()) continue;
            items.add(DocumentRegister.labReport(l.getId(), l.getCollectedAt() != null ? l.getCollectedAt() : l.getReceivedAt(),
                    l.getLabReference(), l.getPdfFilename()));
        }
        return new RegisterResponse(patientId, DocumentRegister.newestFirst(items));
    }

    @Transactional
    public RegisterItem upload(TenantId t, UUID patientId, MultipartFile file, String type, String title, LocalDate date,
                               String notes, UUID consultationId) {
        requirePatient(t, patientId);
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Choose a file to upload");
        if (consultationId != null) requireVisitOf(t, patientId, consultationId);
        byte[] bytes;
        try { bytes = file.getBytes(); } catch (IOException e) { throw new IllegalStateException("The file could not be read", e); }
        var c = DocumentRules.upload(type, title, date, notes, file.getOriginalFilename(), file.getContentType(), bytes,
                LocalDate.now(AppointmentRules.CLINIC_ZONE));
        String key = store(t, patientId, c.fileName(), c.contentType(), bytes);
        UUID by = currentUserOrNull();
        var d = documentRepo.save(ClinicPatientDocument.create(t, patientId, consultationId, c.type(), "UPLOADED", c.title(), c.date(),
                c.notes(), key, c.fileName(), c.contentType(), bytes.length, by));
        String addedBy = by == null ? null : staffNames.names(t.getValue(), List.of(by)).get(by);
        return DocumentRegister.stored(d, addedBy, patientId);
    }

    /** A sick note or referral letter has just been issued: keep a copy in the register. Never blocks issuing it. */
    @Transactional
    public void recordIssued(TenantId t, UUID consultationId, String type, String title, byte[] pdf) {
        ClinicConsultation c = consultationRepo.findActiveById(t, consultationId).orElse(null);
        if (c == null) return;
        recordIssuedFor(t, c.getPatientId(), consultationId, type, title, pdf);
    }

    /** Same, for a letter that may have no visit ({@code consultationId} null). */
    @Transactional
    public void recordIssuedFor(TenantId t, UUID patientId, UUID consultationId, String type, String title, byte[] pdf) {
        try {
            if (pdf == null || pdf.length == 0 || !DocumentRules.ISSUED_TYPES.contains(type)) return;
            LocalDate today = LocalDate.now(AppointmentRules.CLINIC_ZONE);
            if (title.length() > DocumentRules.TITLE_MAX) title = title.substring(0, DocumentRules.TITLE_MAX);
            String name = DocumentRules.safeName(title, "application/pdf");
            String key = store(t, patientId, name, "application/pdf", pdf);
            documentRepo.save(ClinicPatientDocument.create(t, patientId, consultationId, type, "ISSUED", title, today, null,
                    key, name, "application/pdf", pdf.length, currentUserOrNull()));
        } catch (RuntimeException e) {
            log.warn("Could not keep a copy of the issued {} for consultation {}: {}", type, consultationId, e.getMessage());
        }
    }

    public record Download(byte[] content, String fileName, String contentType) {}

    @Transactional(readOnly = true)
    public Download download(TenantId t, UUID patientId, UUID docId) {
        ClinicPatientDocument d = documentRepo.findOne(t, patientId, docId)
                .filter(x -> !x.isVoided()).orElseThrow(() -> new ResourceNotFoundException("Document", docId.toString()));
        try { return new Download(storage.retrieve(d.getStorageKey()), d.getFileName(), d.getContentType()); }
        catch (IOException e) { throw new IllegalStateException("The document could not be read from storage", e); }
    }

    /** Takes a document out of the list, with the reason kept. The file itself is kept in storage. */
    @Transactional
    public void remove(TenantId t, UUID patientId, UUID docId, String reason) {
        String why = DocumentRules.reason(reason);
        ClinicPatientDocument d = documentRepo.findOne(t, patientId, docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", docId.toString()));
        d.voidIt(currentUserOrNull(), why);
        documentRepo.save(d);
    }

    private String store(TenantId t, UUID patientId, String name, String contentType, byte[] bytes) {
        try { return storage.store("clinic-patient-documents/" + t.getValue() + "/" + patientId, name, contentType, bytes); }
        catch (IOException e) { throw new IllegalStateException("The document could not be stored", e); }
    }

    private void requirePatient(TenantId t, UUID patientId) {
        patientRepo.findActiveById(t, patientId).orElseThrow(() -> new ResourceNotFoundException("Patient", patientId.toString()));
    }

    private void requireVisitOf(TenantId t, UUID patientId, UUID consultationId) {
        ClinicConsultation c = consultationRepo.findActiveById(t, consultationId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", consultationId.toString()));
        if (!patientId.equals(c.getPatientId())) throw new IllegalArgumentException("That visit belongs to a different patient");
    }

    private static UUID currentUserOrNull() {
        try { return UserContext.getCurrentUserId(); } catch (RuntimeException e) { return null; }
    }
}
