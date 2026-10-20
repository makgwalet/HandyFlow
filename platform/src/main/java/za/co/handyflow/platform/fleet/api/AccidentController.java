package za.co.handyflow.platform.fleet.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fleet.application.internal.AccidentService;
import za.co.handyflow.platform.fleet.domain.model.AccidentEvidenceType;
import za.co.handyflow.platform.fleet.domain.model.AccidentStatus;
import za.co.handyflow.platform.fleet.dto.AccidentDetailsRequest;
import za.co.handyflow.platform.fleet.dto.AccidentEvidenceResponse;
import za.co.handyflow.platform.fleet.dto.AccidentResponse;
import za.co.handyflow.platform.fleet.dto.ChangeAccidentStatusRequest;
import za.co.handyflow.platform.fleet.dto.ReportAccidentRequest;
import za.co.handyflow.platform.fleet.dto.UpdateAccidentClaimRequest;
import za.co.handyflow.platform.fleet.dto.UpdateAccidentRepairRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Accident cases. FLEET_READ to view and download, FLEET_MANAGE to report and update (speed matters
 * after an accident), FLEET_ADMIN to remove evidence.
 */
@RestController
@RequestMapping("/api/v1/fleet/accidents")
@RequiredArgsConstructor
@Tag(name = "Fleet - Accidents", description = "Accident reports, insurance claims, repairs and evidence")
public class AccidentController {

    private final AccidentService accidentService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "List accidents, newest first; filter by vehicleId and/or status (REPORTED, UNDER_ASSESSMENT, IN_REPAIR, CLOSED, WRITTEN_OFF)")
    public ResponseEntity<ApiResponse<Page<AccidentResponse>>> list(
            @RequestParam(required = false) UUID vehicleId,
            @RequestParam(required = false) AccidentStatus status,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(
                accidentService.list(TenantContext.getTenantIdAsObject(), vehicleId, status, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    public ResponseEntity<ApiResponse<AccidentResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(accidentService.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Report an accident. Issues the reference (ACC-yyyy-nnnnn), pre-fills the claim insurer and excess "
            + "from the vehicle's insurance profile, and alerts the tenant admins (CRITICAL with SMS when anyone was hurt)")
    public ResponseEntity<ApiResponse<AccidentResponse>> report(@Valid @RequestBody ReportAccidentRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Accident reported",
                accidentService.report(TenantContext.getTenantIdAsObject(), UserContext.getCurrentUserId(), request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Edit what happened. Not allowed once the case is CLOSED or WRITTEN_OFF")
    public ResponseEntity<ApiResponse<AccidentResponse>> updateDetails(
            @PathVariable UUID id, @Valid @RequestBody AccidentDetailsRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Accident updated",
                accidentService.updateDetails(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PutMapping("/{id}/claim")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Update the insurance claim (legal status changes only; an insurer once lodged; settlement amount to settle)")
    public ResponseEntity<ApiResponse<AccidentResponse>> updateClaim(
            @PathVariable UUID id, @Valid @RequestBody UpdateAccidentClaimRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Claim updated",
                accidentService.updateClaim(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PutMapping("/{id}/repair")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Record the repairer, repair cost and return-to-service date")
    public ResponseEntity<ApiResponse<AccidentResponse>> updateRepair(
            @PathVariable UUID id, @Valid @RequestBody UpdateAccidentRepairRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Repair updated",
                accidentService.updateRepair(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Move the case along. CLOSED needs the claim resolved; a write-off is declared on the vehicle instead")
    public ResponseEntity<ApiResponse<AccidentResponse>> changeStatus(
            @PathVariable UUID id, @Valid @RequestBody ChangeAccidentStatusRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Status changed",
                accidentService.changeStatus(TenantContext.getTenantIdAsObject(), id, request)));
    }

    // ── Evidence ──────────────────────────────────────────────────────────────

    @GetMapping("/{id}/evidence")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    public ResponseEntity<ApiResponse<List<AccidentEvidenceResponse>>> listEvidence(@PathVariable UUID id) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(
                accidentService.listEvidence(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping(value = "/{id}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Attach a file (PDF, image, DOCX; max 15 MB; 50 per accident): photos, SAPS report, assessor report, quotes")
    public ResponseEntity<ApiResponse<AccidentEvidenceResponse>> uploadEvidence(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            @RequestParam AccidentEvidenceType type,
            @RequestParam(required = false) String description) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("File attached",
                accidentService.uploadEvidence(TenantContext.getTenantIdAsObject(), id,
                        UserContext.getCurrentUserId(), file, type, description)));
    }

    @GetMapping("/{id}/evidence/{evidenceId}/download")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    public ResponseEntity<Resource> downloadEvidence(@PathVariable UUID id, @PathVariable UUID evidenceId) {
        featureGuard.requireModule("fleet");
        AccidentService.EvidenceDownload dl =
                accidentService.downloadEvidence(TenantContext.getTenantIdAsObject(), id, evidenceId);
        // Always an attachment + nosniff: uploaded content is never rendered inline in our origin.
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(dl.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(dl.contentType()))
                .contentLength(dl.sizeBytes())
                .body(new InputStreamResource(dl.stream()));
    }

    @DeleteMapping("/{id}/evidence/{evidenceId}")
    @PreAuthorize("hasAuthority('FLEET_ADMIN')")
    @Operation(summary = "Remove a file from the case (soft delete — the stored file is retained)")
    public ResponseEntity<ApiResponse<Void>> deleteEvidence(@PathVariable UUID id, @PathVariable UUID evidenceId) {
        featureGuard.requireModule("fleet");
        accidentService.deleteEvidence(TenantContext.getTenantIdAsObject(), id, evidenceId,
                UserContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("File removed", null));
    }
}
