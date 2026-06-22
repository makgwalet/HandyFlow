package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.clinic.application.internal.ClinicLabService;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPractitionerRepository;
import za.co.handyflow.platform.clinic.dto.lab.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clinic/lab")
@RequiredArgsConstructor
@Tag(name = "Clinic Lab", description = "Lab results inbox")
public class ClinicLabController {

    private final ClinicLabService          labService;
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

    @PostMapping("/results")
    @PreAuthorize("hasAuthority('CLINIC_LAB_WRITE')")
    @Operation(summary = "Upload a lab result")
    public ResponseEntity<ApiResponse<LabResultResponse>> uploadResult(
            @RequestBody UploadLabResultRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Lab result uploaded",
                labService.uploadResult(TenantContext.getTenantIdAsObject(), req)));
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
    @Operation(summary = "Save Claude-generated plain-language interpretation")
    public ResponseEntity<ApiResponse<LabResultResponse>> setInterpretation(
            @PathVariable UUID id, @RequestParam String interpretation) {
        return ResponseEntity.ok(ApiResponse.success("Interpretation saved",
                labService.setInterpretation(TenantContext.getTenantIdAsObject(), id, interpretation)));
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
