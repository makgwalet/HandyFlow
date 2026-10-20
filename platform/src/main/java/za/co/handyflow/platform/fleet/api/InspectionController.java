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
import za.co.handyflow.platform.fleet.application.internal.InspectionService;
import za.co.handyflow.platform.fleet.domain.model.InspectionStatus;
import za.co.handyflow.platform.fleet.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Running inspections: start → save answers → photos / signature → complete.
 * FLEET_READ to view, FLEET_MANAGE for every step that changes an inspection
 * (consistent with how trips are driven in FleetController).
 */
@RestController
@RequestMapping("/api/v1/fleet")
@RequiredArgsConstructor
@Tag(name = "Fleet - Inspections", description = "Vehicle inspections: checklists, photos, signature, auto-raised issues")
public class InspectionController {

    private final InspectionService inspectionService;
    private final FeatureGuard featureGuard;

    @GetMapping("/inspections")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "All inspections across the fleet, newest first (optional status filter)")
    public ResponseEntity<ApiResponse<Page<InspectionResponse>>> listAll(
            @RequestParam(required = false) InspectionStatus status,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(
                inspectionService.listAll(TenantContext.getTenantIdAsObject(), status, pageable)));
    }

    @GetMapping("/vehicles/{vehicleId}/inspections")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    public ResponseEntity<ApiResponse<Page<InspectionResponse>>> listForVehicle(
            @PathVariable UUID vehicleId, @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(
                inspectionService.listForVehicle(TenantContext.getTenantIdAsObject(), vehicleId, pageable)));
    }

    @PostMapping("/vehicles/{vehicleId}/inspections")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Start an inspection from a template; the checklist is copied into the inspection")
    public ResponseEntity<ApiResponse<InspectionDetailResponse>> start(
            @PathVariable UUID vehicleId, @Valid @RequestBody StartInspectionRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Inspection started",
                inspectionService.start(TenantContext.getTenantIdAsObject(), vehicleId,
                        UserContext.getCurrentUserId(), request)));
    }

    @GetMapping("/inspections/{id}")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    public ResponseEntity<ApiResponse<InspectionDetailResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(
                inspectionService.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @PutMapping("/inspections/{id}/answers")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Save answers (PASS / FAIL / NOT_APPLICABLE + comment) for one or many checklist items")
    public ResponseEntity<ApiResponse<InspectionDetailResponse>> saveAnswers(
            @PathVariable UUID id, @Valid @RequestBody SaveInspectionAnswersRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Answers saved",
                inspectionService.saveAnswers(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping(value = "/inspections/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Attach a photo (JPEG/PNG/WebP, max 10 MB) to the inspection, or to one checklist item via resultId")
    public ResponseEntity<ApiResponse<InspectionPhotoResponse>> uploadPhoto(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) UUID resultId,
            @RequestParam(required = false) String caption) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Photo attached",
                inspectionService.uploadPhoto(TenantContext.getTenantIdAsObject(), id,
                        UserContext.getCurrentUserId(), file, resultId, caption)));
    }

    @GetMapping("/inspections/{id}/photos/{photoId}/download")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    public ResponseEntity<Resource> downloadPhoto(@PathVariable UUID id, @PathVariable UUID photoId) {
        featureGuard.requireModule("fleet");
        return file(inspectionService.downloadPhoto(TenantContext.getTenantIdAsObject(), id, photoId));
    }

    @PostMapping(value = "/inspections/{id}/signature", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Record the inspector's signature as a PNG/JPEG image (max 1 MB) with the signer's name")
    public ResponseEntity<ApiResponse<InspectionDetailResponse>> uploadSignature(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            @RequestParam String signerName) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Signature recorded",
                inspectionService.uploadSignature(TenantContext.getTenantIdAsObject(), id, file, signerName)));
    }

    @GetMapping("/inspections/{id}/signature")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    public ResponseEntity<Resource> downloadSignature(@PathVariable UUID id) {
        featureGuard.requireModule("fleet");
        return file(inspectionService.downloadSignature(TenantContext.getTenantIdAsObject(), id));
    }

    @PostMapping("/inspections/{id}/complete")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Complete the inspection: validates required answers, photos and signature, locks it, "
            + "raises an issue for each failed item and notifies admins on failure")
    public ResponseEntity<ApiResponse<InspectionDetailResponse>> complete(
            @PathVariable UUID id, @Valid @RequestBody(required = false) CompleteInspectionRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Inspection completed",
                inspectionService.complete(TenantContext.getTenantIdAsObject(), id,
                        UserContext.getCurrentUserId(), request)));
    }

    @PostMapping("/inspections/{id}/cancel")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    public ResponseEntity<ApiResponse<InspectionDetailResponse>> cancel(@PathVariable UUID id) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Inspection cancelled",
                inspectionService.cancel(TenantContext.getTenantIdAsObject(), id)));
    }

    /** Always an attachment + nosniff: uploaded content is never rendered inline in our origin. */
    private ResponseEntity<Resource> file(InspectionService.FileDownload dl) {
        ResponseEntity.BodyBuilder b = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(dl.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(dl.contentType()));
        if (dl.sizeBytes() >= 0) b.contentLength(dl.sizeBytes());
        return b.body(new InputStreamResource(dl.stream()));
    }
}