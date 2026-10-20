package za.co.handyflow.platform.fleet.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fleet.application.internal.AssetAttachmentService;
import za.co.handyflow.platform.fleet.domain.model.AttachmentStatus;
import za.co.handyflow.platform.fleet.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/** Implements and attachments. FLEET_READ to view; FLEET_MANAGE for every change. No delete — retire instead. */
@RestController
@RequestMapping("/api/v1/fleet/attachments")
@RequiredArgsConstructor
@Tag(name = "Fleet - Attachments", description = "Implements and attachments hitched to tractors, plant and forklifts")
public class AssetAttachmentController {

    private final AssetAttachmentService attachmentService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "List attachments by name; filter by status (AVAILABLE, ATTACHED, RETIRED)")
    public ResponseEntity<ApiResponse<Page<AttachmentResponse>>> list(
            @RequestParam(required = false) AttachmentStatus status, @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(attachmentService.list(TenantContext.getTenantIdAsObject(), status,
                org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "An attachment with its hitching history, lifetime hours in use and cost per hour")
    public ResponseEntity<ApiResponse<AttachmentDetailResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(attachmentService.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    public ResponseEntity<ApiResponse<AttachmentDetailResponse>> create(@Valid @RequestBody SaveAttachmentRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Attachment registered",
                attachmentService.create(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    public ResponseEntity<ApiResponse<AttachmentDetailResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody SaveAttachmentRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Attachment updated",
                attachmentService.update(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/{id}/attach")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Hitch an available attachment to a machine (not a trailer). Hours default to the machine's current engine hours")
    public ResponseEntity<ApiResponse<AttachmentDetailResponse>> attach(
            @PathVariable UUID id, @Valid @RequestBody AttachRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Attachment hitched",
                attachmentService.attach(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/{id}/detach")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    @Operation(summary = "Unhitch an attachment; banks the machine hours it ran")
    public ResponseEntity<ApiResponse<AttachmentDetailResponse>> detach(
            @PathVariable UUID id, @Valid @RequestBody(required = false) DetachRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Attachment unhitched",
                attachmentService.detach(TenantContext.getTenantIdAsObject(), id,
                        request != null ? request : new DetachRequest(null, null))));
    }

    @PostMapping("/{id}/retire")
    @PreAuthorize("hasAuthority('FLEET_MANAGE')")
    public ResponseEntity<ApiResponse<AttachmentDetailResponse>> retire(@PathVariable UUID id) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Attachment retired",
                attachmentService.retire(TenantContext.getTenantIdAsObject(), id)));
    }
}