package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.clinic.application.internal.ClinicLetterPdfService;
import za.co.handyflow.platform.clinic.application.internal.ClinicLetterTemplateService;
import za.co.handyflow.platform.clinic.application.internal.ClinicPatientDocumentService;
import za.co.handyflow.platform.clinic.dto.LetterDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Letter templates and the general letter. */
@RestController
@RequestMapping("/api/v1/clinic")
@RequiredArgsConstructor
@Tag(name = "Clinic letters", description = "Reusable letter templates and general letters")
public class ClinicLetterController {

    private final ClinicLetterTemplateService templates;
    private final ClinicLetterPdfService letterPdf;
    private final ClinicPatientDocumentService documents;

    @GetMapping("/letter-templates")
    @PreAuthorize("hasAuthority('CLINIC_DOCUMENT_READ')")
    @Operation(summary = "Letter templates, optionally of one kind (SICK_NOTE, REFERRAL, PRESCRIPTION_LETTER, GENERAL_LETTER)")
    public ResponseEntity<ApiResponse<List<TemplateResponse>>> list(@RequestParam(required = false) String kind) {
        return ResponseEntity.ok(ApiResponse.success("Success", templates.list(TenantContext.getTenantIdAsObject(), kind)));
    }

    @GetMapping("/letter-templates/merge-fields")
    @PreAuthorize("hasAuthority('CLINIC_DOCUMENT_READ')")
    @Operation(summary = "The merge fields a template may use")
    public ResponseEntity<ApiResponse<MergeFields>> mergeFields() {
        return ResponseEntity.ok(ApiResponse.success("Success", new MergeFields(templates.mergeFields())));
    }

    @PostMapping("/letter-templates")
    @PreAuthorize("hasAuthority('CLINIC_DOCUMENT_CREATE')")
    @Operation(summary = "Save a letter template")
    public ResponseEntity<ApiResponse<TemplateResponse>> create(@RequestBody TemplateRequest body) {
        return ResponseEntity.status(201).body(ApiResponse.success("Template saved", templates.create(TenantContext.getTenantIdAsObject(), body)));
    }

    @PutMapping("/letter-templates/{id}")
    @PreAuthorize("hasAuthority('CLINIC_DOCUMENT_CREATE')")
    @Operation(summary = "Change a letter template")
    public ResponseEntity<ApiResponse<TemplateResponse>> update(@PathVariable UUID id, @RequestBody TemplateRequest body) {
        return ResponseEntity.ok(ApiResponse.success("Template saved", templates.update(TenantContext.getTenantIdAsObject(), id, body)));
    }

    @DeleteMapping("/letter-templates/{id}")
    @PreAuthorize("hasAuthority('CLINIC_DOCUMENT_CREATE')")
    @Operation(summary = "Archive a letter template (letters already issued are unaffected)")
    public ResponseEntity<ApiResponse<Void>> archive(@PathVariable UUID id) {
        templates.archive(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Template archived", null));
    }

    @GetMapping("/letter-templates/{id}/render")
    @PreAuthorize("hasAuthority('CLINIC_DOCUMENT_READ')")
    @Operation(summary = "A template with the merge fields filled in, for a visit or for a patient (with no visit), optionally addressed to a recipient")
    public ResponseEntity<ApiResponse<RenderedTemplate>> render(@PathVariable UUID id, @RequestParam(required = false) UUID consultationId,
                                                                @RequestParam(required = false) UUID patientId,
                                                                @RequestParam(required = false) String recipientName,
                                                                @RequestParam(required = false) String recipientCompany,
                                                                @RequestParam(required = false) UUID signedBy) {
        return ResponseEntity.ok(ApiResponse.success("Success", templates.render(TenantContext.getTenantIdAsObject(), id, patientId, consultationId, recipientName, recipientCompany, signedBy)));
    }

    @PostMapping("/consultations/{id}/letter")
    @PreAuthorize("hasAuthority('CLINIC_DOCUMENT_CREATE')")
    @Operation(summary = "Write a general letter PDF for a visit; a copy is kept in the patient's documents")
    public ResponseEntity<byte[]> letter(@PathVariable UUID id, @RequestBody LetterRequest body) {
        var tenant = TenantContext.getTenantIdAsObject();
        byte[] pdf = letterPdf.generate(tenant, null, id, body.title(), body.body(), body.recipientName(), body.recipientCompany(), body.signedByPractitionerId());
        documents.recordIssued(tenant, id, "LETTER", body.title().trim(), pdf);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"letter-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF).contentLength(pdf.length).body(pdf);
    }

    @PostMapping("/patients/{id}/letter")
    @PreAuthorize("hasAuthority('CLINIC_DOCUMENT_CREATE')")
    @Operation(summary = "Write a general letter PDF for a patient (a visit is optional; the letter may be addressed to a person or company); a copy is kept in the patient's documents")
    public ResponseEntity<byte[]> patientLetter(@PathVariable UUID id, @RequestBody LetterRequest body) {
        var tenant = TenantContext.getTenantIdAsObject();
        byte[] pdf = letterPdf.generate(tenant, id, body.consultationId(), body.title(), body.body(), body.recipientName(), body.recipientCompany(), body.signedByPractitionerId());
        documents.recordIssuedFor(tenant, id, body.consultationId(), "LETTER", body.title().trim(), pdf);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"letter-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF).contentLength(pdf.length).body(pdf);
    }
}
