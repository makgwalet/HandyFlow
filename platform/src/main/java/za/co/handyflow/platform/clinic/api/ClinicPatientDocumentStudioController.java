package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.clinic.application.internal.ClinicClinicalDocumentService;
import za.co.handyflow.platform.clinic.application.internal.ClinicDocumentDeliveryService;
import za.co.handyflow.platform.clinic.application.internal.ClinicIssuedDocumentService;
import za.co.handyflow.platform.clinic.application.internal.ClinicIssuedDocumentService.PatientDocumentRow;
import za.co.handyflow.platform.clinic.domain.repository.ClinicConsultationRepository;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Document Studio in the patient file (patch 0203): a patient's numbered documents, downloading the kept copy again, sending one to the
 * patient as its own step (CLINIC-DEC-024), reissuing a prescription with a reason, and the record of a consent decision.
 * Every path sits under /patients/{id} or /consultations/{id}, so restricted records and break-glass apply as everywhere else;
 * downloads and sends count as documents there.
 */
@RestController
@RequestMapping("/api/v1/clinic")
@RequiredArgsConstructor
@Tag(name = "Clinic patient documents", description = "Numbered documents for one patient: list, download, send, reissue")
public class ClinicPatientDocumentStudioController {

    public record ReissueRequest(String reason, UUID letterheadId) {}

    /** The documents of one visit, with whose they are (the screen needs the patient to send one). */
    public record VisitDocuments(UUID patientId, List<PatientDocumentRow> documents) {}

    private final ClinicIssuedDocumentService issued;
    private final ClinicDocumentDeliveryService delivery;
    private final ClinicClinicalDocumentService documents;
    private final ClinicConsultationRepository consultationRepo;

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }

    private static UUID me() {
        try { return za.co.handyflow.platform.shared.UserContext.getCurrentUserId(); } catch (RuntimeException e) { return null; }
    }

    @GetMapping("/patients/{patientId}/issued-documents")
    @PreAuthorize("hasAuthority('CLINIC_DOCUMENT_READ')")
    @Operation(summary = "A patient's numbered documents, newest first, with the last time each was sent")
    public ResponseEntity<ApiResponse<List<PatientDocumentRow>>> list(@PathVariable UUID patientId, @RequestParam(required = false) UUID consultationId,
                                                                      @RequestParam(required = false) String type) {
        return ResponseEntity.ok(ApiResponse.success("Success", issued.forPatient(tenant().getValue(), patientId, consultationId, type)));
    }

    @GetMapping("/consultations/{consultationId}/issued-documents")
    @PreAuthorize("hasAuthority('CLINIC_DOCUMENT_READ')")
    @Operation(summary = "The numbered documents of one visit")
    public ResponseEntity<ApiResponse<VisitDocuments>> forVisit(@PathVariable UUID consultationId, @RequestParam(required = false) String type) {
        var c = consultationRepo.findActiveById(tenant(), consultationId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", consultationId.toString()));
        return ResponseEntity.ok(ApiResponse.success("Success",
                new VisitDocuments(c.getPatientId(), issued.forPatient(tenant().getValue(), c.getPatientId(), consultationId, type))));
    }

    @GetMapping("/patients/{patientId}/issued-documents/{documentId}/pdf")
    @PreAuthorize("hasAuthority('CLINIC_DOCUMENT_DOWNLOAD')")
    @Operation(summary = "Download the kept copy of a numbered document (the same number, not a new one)")
    public ResponseEntity<byte[]> download(@PathVariable UUID patientId, @PathVariable UUID documentId) {
        var copy = issued.copy(tenant().getValue(), patientId, documentId);
        return pdf(copy.pdf(), copy.number() + ".pdf");
    }

    @PostMapping("/patients/{patientId}/issued-documents/{documentId}/email")
    @PreAuthorize("hasAuthority('CLINIC_DOCUMENT_EMAIL')")
    @Operation(summary = "Email the kept copy to the patient's own address on file; recorded")
    public ResponseEntity<ApiResponse<ClinicDocumentDeliveryService.Sent>> email(@PathVariable UUID patientId, @PathVariable UUID documentId) {
        return ResponseEntity.ok(ApiResponse.success("Sent", delivery.emailToPatient(tenant(), patientId, documentId, me())));
    }

    @PostMapping("/consultations/{consultationId}/reissued-prescription-pdf")
    @PreAuthorize("hasAuthority('CLINIC_PRESCRIPTION_CREATE')")
    @Operation(summary = "Reissue the visit's prescription with a reason; the earlier copy is withdrawn")
    public ResponseEntity<byte[]> reissuePrescription(@PathVariable UUID consultationId, @RequestBody ReissueRequest body) {
        String reason = body == null ? null : body.reason();
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Give a reason for reissuing the prescription");
        var doc = documents.prescription(tenant(), consultationId, body.letterheadId(), reason, me());
        return pdf(doc.pdf(), (doc.number() == null ? "prescription-" + consultationId : doc.number()) + ".pdf");
    }

    @GetMapping("/patients/{patientId}/consent/{consentId}/record-pdf")
    @PreAuthorize("hasAuthority('CLINIC_CONSENT_HISTORY_READ')")
    @Operation(summary = "A numbered record of one consent decision, with signature lines")
    public ResponseEntity<byte[]> consentRecord(@PathVariable UUID patientId, @PathVariable UUID consentId,
                                                @RequestParam(required = false) UUID letterheadId) {
        var doc = documents.consentRecord(tenant(), patientId, consentId, letterheadId, me());
        return pdf(doc.pdf(), doc.number() + ".pdf");
    }

    private static ResponseEntity<byte[]> pdf(byte[] pdf, String fileName) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName.replaceAll("[^A-Za-z0-9._-]", "_") + "\"")
                .contentType(MediaType.APPLICATION_PDF).contentLength(pdf.length).body(pdf);
    }
}
