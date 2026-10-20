package za.co.handyflow.platform.fleet.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fleet.application.internal.FleetComplianceService;
import za.co.handyflow.platform.fleet.application.internal.VehicleDocumentService;
import za.co.handyflow.platform.fleet.application.internal.VehicleInsuranceService;
import za.co.handyflow.platform.fleet.domain.model.VehicleDocumentType;
import za.co.handyflow.platform.fleet.dto.InsuranceResponse;
import za.co.handyflow.platform.fleet.dto.UpsertInsuranceRequest;
import za.co.handyflow.platform.fleet.dto.VehicleComplianceResponse;
import za.co.handyflow.platform.fleet.dto.VehicleDocumentResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Document vault, insurance profile and compliance board for a vehicle.
 * Same three-tier permission split as FleetController: FLEET_READ to view and
 * download, FLEET_MANAGE to upload / edit, FLEET_ADMIN to delete (evidence removal is
 * the hard-to-undo action here).
 */
@RestController
@RequestMapping("/api/v1/fleet/vehicles/{vehicleId}")
@RequiredArgsConstructor
@Tag(name = "Fleet - Documents & Insurance", description = "Vehicle document vault, insurance profile and compliance status")
public class VehicleDocumentController {

    private final VehicleDocumentService documentService;
    private final VehicleInsuranceService insuranceService;
    private final FleetComplianceService complianceService;
    private final FeatureGuard featureGuard;

    // ── Documents ─────────────────────────────────────────────────────────────

    @GetMapping("/documents")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "List a vehicle's documents (current only by default; includeSuperseded=true adds version history)")
    public ResponseEntity<ApiResponse<List<VehicleDocumentResponse>>> listDocuments(
            @PathVariable UUID vehicleId,
            @RequestParam(required = false) VehicleDocumentType type,
            @RequestParam(defaultValue = "false") boolean includeSuperseded) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(
                documentService.list(TenantContext.getTenantIdAsObject(), vehicleId, type, includeSuperseded)));
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Upload a vehicle document. Singleton types (licence disc, roadworthy, insurance, ...) supersede the previous version; "
            + "licence disc / roadworthy / insurance require expiryDate and update the vehicle's expiry")
    public ResponseEntity<ApiResponse<VehicleDocumentResponse>> uploadDocument(
            @PathVariable UUID vehicleId,
            @RequestPart("file") MultipartFile file,
            @RequestParam VehicleDocumentType type,
            @RequestParam(required = false) String documentNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issueDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate,
            @RequestParam(required = false) String issuer,
            @RequestParam(required = false) String notes) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Document uploaded",
                        documentService.upload(TenantContext.getTenantIdAsObject(), vehicleId,
                                UserContext.getCurrentUserId(), file, type,
                                documentNumber, issueDate, expiryDate, issuer, notes)));
    }

    @GetMapping("/documents/{documentId}/download")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "Download a vehicle document's file")
    public ResponseEntity<Resource> downloadDocument(@PathVariable UUID vehicleId, @PathVariable UUID documentId) {
        featureGuard.requireModule("fleet");
        VehicleDocumentService.DocumentDownload dl =
                documentService.download(TenantContext.getTenantIdAsObject(), vehicleId, documentId);
        // Always an attachment + nosniff: never let the browser render uploaded content inline in our origin.
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(dl.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(dl.contentType()))
                .contentLength(dl.sizeBytes())
                .body(new InputStreamResource(dl.stream()));
    }

    @DeleteMapping("/documents/{documentId}")
    @PreAuthorize("hasAuthority('FLEET_ADMIN')")
    @Operation(summary = "Delete a document (soft delete — the stored file is retained as evidence)")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(@PathVariable UUID vehicleId, @PathVariable UUID documentId) {
        featureGuard.requireModule("fleet");
        documentService.delete(TenantContext.getTenantIdAsObject(), vehicleId, documentId,
                UserContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Document deleted", null));
    }

    // ── Insurance profile ─────────────────────────────────────────────────────

    @GetMapping("/insurance")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "Get the vehicle's insurance profile (data is null if none recorded yet)")
    public ResponseEntity<ApiResponse<InsuranceResponse>> getInsurance(@PathVariable UUID vehicleId) {
        featureGuard.requireModule("fleet");
        InsuranceResponse profile = insuranceService
                .get(TenantContext.getTenantIdAsObject(), vehicleId).orElse(null);
        return ResponseEntity.ok(ApiResponse.success(profile));
    }

    @PutMapping("/insurance")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Create or replace the vehicle's insurance profile; policy expiry updates the vehicle's insurance expiry")
    public ResponseEntity<ApiResponse<InsuranceResponse>> upsertInsurance(
            @PathVariable UUID vehicleId, @Valid @RequestBody UpsertInsuranceRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Insurance profile saved",
                insuranceService.upsert(TenantContext.getTenantIdAsObject(), vehicleId,
                        UserContext.getCurrentUserId(), request)));
    }

    // ── Compliance board ──────────────────────────────────────────────────────

    @GetMapping("/compliance")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "Compliance status for one vehicle: licence disc, roadworthy, insurance, tracking, service")
    public ResponseEntity<ApiResponse<VehicleComplianceResponse>> getCompliance(@PathVariable UUID vehicleId) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(
                complianceService.getCompliance(TenantContext.getTenantIdAsObject(), vehicleId)));
    }
}