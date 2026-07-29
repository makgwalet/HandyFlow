package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.clinic.application.internal.ClinicLabService;
import za.co.handyflow.platform.clinic.application.internal.ClinicLabSummaryPdfService;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPractitionerRepository;
import za.co.handyflow.platform.clinic.dto.lab.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clinic/lab")
@RequiredArgsConstructor
@Tag(name = "Clinic Lab", description = "Lab results inbox")
public class ClinicLabController {

    private final ClinicLabService          labService;
    private final ClinicLabSummaryPdfService labSummaryPdfService;
    private final ClinicPractitionerRepository practitionerRepo; // FIX #2 — resolve reviewer

    @GetMapping("/results")
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "List lab results, optionally filter by status")
    public ResponseEntity<ApiResponse<List<LabResultResponse>>> getResults(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                labService.getResults(TenantContext.getTenantIdAsObject(), status)));
    }

    @GetMapping("/patients/{patientId}/results")
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "Get all lab results for a specific patient")
    public ResponseEntity<ApiResponse<List<LabResultResponse>>> getPatientResults(
            @PathVariable UUID patientId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                labService.getPatientResults(TenantContext.getTenantIdAsObject(), patientId)));
    }

    // FIX #3 — upload/interpret/review/file require CLINIC_LAB_WRITE, not generic CLINIC_WRITE.
    // A receptionist (CLINIC_WRITE only) should not be interpreting or signing off lab results.

    @PostMapping(value = "/results", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('CLINIC_LAB_WRITE')")
    @Operation(summary = "Upload a lab result PDF — the actual file, not just a filename")
    public ResponseEntity<ApiResponse<LabResultResponse>> uploadResult(
            @RequestParam("file") MultipartFile file,
            @RequestParam String source,
            @RequestParam(required = false) String labReference,
            @RequestParam(required = false) String patientNameRaw,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant collectedAt) {
        return ResponseEntity.status(201).body(ApiResponse.success("Lab result uploaded",
                labService.uploadResult(TenantContext.getTenantIdAsObject(), file, source,
                        labReference, patientNameRaw, collectedAt)));
    }

    @GetMapping("/results/{id}/pdf")
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "Download the uploaded lab result PDF")
    public ResponseEntity<byte[]> downloadResultPdf(@PathVariable UUID id) {
        var file = labService.downloadResultPdf(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(file.content());
    }

    /**
     * FIX: "no lab result summary PDF" gap — distinct from the raw uploaded
     * file above: this renders the parsed/interpreted data as a clean
     * formatted document.
     */
    @GetMapping("/results/{id}/summary-pdf")
    @PreAuthorize("hasAuthority('CLINIC_READ')")
    @Operation(summary = "Download a formatted summary of the lab result (parsed markers + interpretation)")
    public ResponseEntity<byte[]> downloadResultSummaryPdf(@PathVariable UUID id) {
        byte[] pdf = labSummaryPdfService.generate(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"lab-summary-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @PostMapping("/results/{id}/match-patient")
    @PreAuthorize("hasAuthority('CLINIC_LAB_WRITE')")
    @Operation(summary = "Manually match a lab result to a patient")
    public ResponseEntity<ApiResponse<LabResultResponse>> matchPatient(
            @PathVariable UUID id, @RequestParam UUID patientId) {
        return ResponseEntity.ok(ApiResponse.success("Patient matched",
                labService.matchPatient(TenantContext.getTenantIdAsObject(), id, patientId)));
    }

    @PostMapping("/results/{id}/interpret")
    @PreAuthorize("hasAuthority('CLINIC_LAB_WRITE')")
    @Operation(summary = "Save a manually-written plain-language interpretation")
    public ResponseEntity<ApiResponse<LabResultResponse>> setInterpretation(
            @PathVariable UUID id, @RequestParam String interpretation) {
        return ResponseEntity.ok(ApiResponse.success("Interpretation saved",
                labService.setInterpretation(TenantContext.getTenantIdAsObject(), id, interpretation)));
    }

    /**
     * FIX: closes the "AI-assisted interpretation" loop — previously the
     * frontend called the Anthropic API directly from the browser with no
     * key attached at all, which could never have worked (and would leak
     * the key publicly if it ever did have one). This calls Claude
     * server-side instead, where the key actually lives.
     */
    @PostMapping("/results/{id}/interpret-ai")
    @PreAuthorize("hasAuthority('CLINIC_LAB_WRITE')")
    @Operation(summary = "Generate a Claude-based plain-language interpretation server-side and save it")
    public ResponseEntity<ApiResponse<LabResultResponse>> interpretWithAi(
            @PathVariable UUID id,
            @RequestParam(required = false) String patientFullName) {
        return ResponseEntity.ok(ApiResponse.success("Interpretation generated and saved",
                labService.interpretWithAi(TenantContext.getTenantIdAsObject(), id, patientFullName)));
    }

    /**
     * FIX #2 — resolve the logged-in practitioner from the security principal.
     * reviewedBy param now ignored — we look up the practitioner whose user account
     * matches the authenticated user's username (email). Falls back to null if no
     * practitioner record is linked (e.g., admin user signing off).
     *
     * FIX #3 — requires CLINIC_LAB_WRITE, not generic CLINIC_WRITE.
     * Only practitioners (who have CLINIC_LAB_WRITE) can sign off results.
     */
    @PostMapping("/results/{id}/review")
    @PreAuthorize("hasAuthority('CLINIC_LAB_WRITE')")
    @Operation(summary = "Mark a lab result as reviewed — reviewer resolved from logged-in user")
    public ResponseEntity<ApiResponse<LabResultResponse>> markReviewed(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails principal) {

        UUID reviewerUuid = resolveReviewerUuid(principal);
        return ResponseEntity.ok(ApiResponse.success("Marked as reviewed",
                labService.markReviewed(TenantContext.getTenantIdAsObject(), id, reviewerUuid)));
    }

    @PostMapping("/results/{id}/file")
    @PreAuthorize("hasAuthority('CLINIC_LAB_WRITE')")
    @Operation(summary = "File a lab result against a consultation")
    public ResponseEntity<ApiResponse<LabResultResponse>> fileResult(
            @PathVariable UUID id, @RequestParam UUID consultationId) {
        return ResponseEntity.ok(ApiResponse.success("Result filed",
                labService.fileResult(TenantContext.getTenantIdAsObject(), id, consultationId)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Resolves the UUID of the ClinicPractitioner linked to the authenticated user.
     * Matches by email (UserDetails.username == practitioner.email).
     * Returns null if the logged-in user has no practitioner record (admin, reception).
     */
    private UUID resolveReviewerUuid(UserDetails principal) {
        if (principal == null) return null;
        var tenantId = TenantContext.getTenantIdAsObject();
        return practitionerRepo.findAllActiveList(tenantId).stream()
                .filter(p -> principal.getUsername().equalsIgnoreCase(p.getEmail()))
                .map(p -> p.getId())
                .findFirst()
                .orElse(null);
    }
}