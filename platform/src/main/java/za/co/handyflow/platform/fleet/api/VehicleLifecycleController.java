package za.co.handyflow.platform.fleet.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fleet.application.internal.VehicleLifecycleService;
import za.co.handyflow.platform.fleet.domain.model.LifecycleEvidenceType;
import za.co.handyflow.platform.fleet.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Asset lifecycle: history, theft / recovery, write-off, sale / transfer / disposal.
 * <p>
 * Permissions: FLEET_READ to view; FLEET_MANAGE for simple transitions and for theft
 * reporting and recovery (speed matters when a vehicle is stolen); FLEET_ADMIN for the
 * irreversible asset events — write-off and sale / transfer / disposal — and to delete evidence.
 */
@RestController
@RequestMapping("/api/v1/fleet/vehicles/{vehicleId}/lifecycle")
@RequiredArgsConstructor
@Tag(name = "Fleet - Lifecycle", description = "Vehicle asset lifecycle: theft, write-off, sale, transfer, disposal")
public class VehicleLifecycleController {

    private final VehicleLifecycleService lifecycleService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "Current lifecycle and operational status, plus the full lifecycle history with details and evidence")
    public ResponseEntity<ApiResponse<VehicleLifecycleResponse>> get(@PathVariable UUID vehicleId) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(
                lifecycleService.get(TenantContext.getTenantIdAsObject(), vehicleId)));
    }

    @PostMapping("/transition")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Simple moves: ACQUIRED, ACTIVE, IMPOUNDED, RETIRED. Stolen / write-off / disposal use their own endpoints")
    public ResponseEntity<ApiResponse<VehicleLifecycleResponse>> transition(
            @PathVariable UUID vehicleId, @Valid @RequestBody LifecycleTransitionRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Lifecycle updated",
                lifecycleService.transition(TenantContext.getTenantIdAsObject(), vehicleId,
                        UserContext.getCurrentUserId(), request)));
    }

    // ── Theft / missing / recovery ────────────────────────────────────────────

    @PostMapping("/theft")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Report the vehicle STOLEN or MISSING. Notifies admins; the vehicle can no longer start trips")
    public ResponseEntity<ApiResponse<VehicleLifecycleResponse>> reportTheft(
            @PathVariable UUID vehicleId, @Valid @RequestBody ReportTheftRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Theft report recorded",
                lifecycleService.reportTheft(TenantContext.getTenantIdAsObject(), vehicleId,
                        UserContext.getCurrentUserId(), request)));
    }

    @PutMapping("/theft")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Update police, insurer and tracking details on the open theft report")
    public ResponseEntity<ApiResponse<VehicleLifecycleResponse>> updateTheft(
            @PathVariable UUID vehicleId, @Valid @RequestBody UpdateTheftReportRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Theft report updated",
                lifecycleService.updateTheftReport(TenantContext.getTenantIdAsObject(), vehicleId, request)));
    }

    @PostMapping("/recovery")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Record recovery of a stolen / missing vehicle and return it to ACTIVE")
    public ResponseEntity<ApiResponse<VehicleLifecycleResponse>> recover(
            @PathVariable UUID vehicleId, @Valid @RequestBody RecordRecoveryRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Recovery recorded",
                lifecycleService.recordRecovery(TenantContext.getTenantIdAsObject(), vehicleId,
                        UserContext.getCurrentUserId(), request)));
    }

    // ── Write-off ─────────────────────────────────────────────────────────────

    @PostMapping("/write-off")
    @PreAuthorize("hasAuthority('FLEET_ADMIN')")
    @Operation(summary = "Declare the vehicle a write-off (optionally linked to an accident case, which moves to WRITTEN_OFF too)")
    public ResponseEntity<ApiResponse<VehicleLifecycleResponse>> writeOff(
            @PathVariable UUID vehicleId, @Valid @RequestBody RecordWriteOffRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Write-off recorded",
                lifecycleService.recordWriteOff(TenantContext.getTenantIdAsObject(), vehicleId,
                        UserContext.getCurrentUserId(), request)));
    }

    @PutMapping("/write-off")
    @PreAuthorize("hasAuthority('FLEET_ADMIN')")
    @Operation(summary = "Update salvage and insurance-settlement details while the vehicle is WRITTEN_OFF")
    public ResponseEntity<ApiResponse<VehicleLifecycleResponse>> updateWriteOff(
            @PathVariable UUID vehicleId, @Valid @RequestBody RecordWriteOffRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Write-off updated",
                lifecycleService.updateWriteOff(TenantContext.getTenantIdAsObject(), vehicleId, request)));
    }

    // ── Sale / transfer / disposal ────────────────────────────────────────────

    @PostMapping("/disposal")
    @PreAuthorize("hasAuthority('FLEET_ADMIN')")
    @Operation(summary = "Sell, transfer or dispose of the vehicle (SOLD / TRANSFERRED / DISPOSED). Financed vehicles need "
            + "financeCleared; the book value is snapshotted so gain / loss is fixed")
    public ResponseEntity<ApiResponse<VehicleLifecycleResponse>> dispose(
            @PathVariable UUID vehicleId, @Valid @RequestBody RecordDisposalRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Disposal recorded",
                lifecycleService.recordDisposal(TenantContext.getTenantIdAsObject(), vehicleId,
                        UserContext.getCurrentUserId(), request)));
    }

    // ── Evidence ──────────────────────────────────────────────────────────────

    @PostMapping(value = "/events/{eventId}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Attach evidence (PDF, image, DOCX; max 15 MB) to a lifecycle event — SAPS report, sale agreement, "
            + "settlement letter, transfer papers, assessor report...")
    public ResponseEntity<ApiResponse<LifecycleEvidenceResponse>> uploadEvidence(
            @PathVariable UUID vehicleId, @PathVariable UUID eventId,
            @RequestPart("file") MultipartFile file,
            @RequestParam LifecycleEvidenceType type,
            @RequestParam(required = false) String description) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Evidence attached",
                lifecycleService.uploadEvidence(TenantContext.getTenantIdAsObject(), vehicleId, eventId,
                        UserContext.getCurrentUserId(), file, type, description)));
    }

    @GetMapping("/events/{eventId}/evidence/{evidenceId}/download")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    public ResponseEntity<Resource> downloadEvidence(@PathVariable UUID vehicleId, @PathVariable UUID eventId,
                                                     @PathVariable UUID evidenceId) {
        featureGuard.requireModule("fleet");
        VehicleLifecycleService.EvidenceDownload dl = lifecycleService.downloadEvidence(
                TenantContext.getTenantIdAsObject(), vehicleId, eventId, evidenceId);
        // Always an attachment + nosniff: uploaded content is never rendered inline in our origin.
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(dl.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(dl.contentType()))
                .contentLength(dl.sizeBytes())
                .body(new InputStreamResource(dl.stream()));
    }

    @DeleteMapping("/events/{eventId}/evidence/{evidenceId}")
    @PreAuthorize("hasAuthority('FLEET_ADMIN')")
    @Operation(summary = "Delete an evidence file (soft delete — the stored file is retained)")
    public ResponseEntity<ApiResponse<Void>> deleteEvidence(@PathVariable UUID vehicleId, @PathVariable UUID eventId,
                                                            @PathVariable UUID evidenceId) {
        featureGuard.requireModule("fleet");
        lifecycleService.deleteEvidence(TenantContext.getTenantIdAsObject(), vehicleId, eventId, evidenceId,
                UserContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Evidence deleted", null));
    }
}