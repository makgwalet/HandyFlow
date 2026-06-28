// security/api/GuardController.java

package za.co.handyflow.platform.security.api;

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
import za.co.handyflow.platform.security.application.internal.GuardService;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/security/guards")
@RequiredArgsConstructor
@Tag(name = "Security - Guards", description = "Guard management and status workflow")
public class GuardController {

    private final GuardService guardService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List all guards with optional name/PSiRA search")
    public ResponseEntity<ApiResponse<Page<GuardResponse>>> getGuards(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(
                guardService.getGuards(TenantContext.getTenantIdAsObject(), search, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Get a single guard by ID")
    public ResponseEntity<ApiResponse<GuardResponse>> getGuard(@PathVariable UUID id) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(
                guardService.getGuard(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Register a new guard")
    public ResponseEntity<ApiResponse<GuardResponse>> createGuard(
            @Valid @RequestBody CreateGuardRequest request) {
        featureGuard.requireModule("security");
        var guard = guardService.createGuard(TenantContext.getTenantIdAsObject(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Guard created", guard));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Update guard details (name, PSiRA, grade, etc.)")
    public ResponseEntity<ApiResponse<GuardResponse>> updateGuard(
            @PathVariable UUID id,
            @Valid @RequestBody CreateGuardRequest request) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("Guard updated",
                guardService.updateGuard(TenantContext.getTenantIdAsObject(), id, request)));
    }

    /**
     * Fixes bug #5: PATCH /guards/{id}/status was missing entirely.
     * The GuardsTab.tsx "Change Status" modal called this endpoint and always
     * received a 404 in production.
     *
     * WHY PATCH and not PUT?
     * PATCH = partial update (only the status changes, nothing else on the guard).
     * PUT   = full replacement (would require re-sending all guard fields).
     * Status change is a targeted, semantically distinct operation — PATCH is
     * the correct HTTP verb.
     *
     * WHY separate from PUT /guards/{id}?
     * Status changes are HR/legal events (suspension, termination) with their own
     * required-note validation and audit trail.  Mixing them into the main update
     * endpoint would conflate two very different operations.
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(
            summary = "Change guard operational status",
            description = "Changes status: ACTIVE → ON_LEAVE / SUSPENDED / UNDER_INVESTIGATION / TERMINATED. " +
                    "SUSPENDED and TERMINATED require a written note (reason)."
    )
    public ResponseEntity<ApiResponse<GuardResponse>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateGuardStatusRequest request) {
        featureGuard.requireModule("security");
        var tenantId   = TenantContext.getTenantIdAsObject();
        var changedBy  = TenantContext.getCurrentUserId(); // returns UUID of authenticated user
        return ResponseEntity.ok(ApiResponse.success("Guard status updated",
                guardService.updateStatus(tenantId, id, request, changedBy)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_DELETE')")
    @Operation(summary = "Soft-delete a guard record (preserves shift/incident history)")
    public ResponseEntity<ApiResponse<Void>> deleteGuard(@PathVariable UUID id) {
        featureGuard.requireModule("security");
        var tenantId  = TenantContext.getTenantIdAsObject();
        var deletedBy = TenantContext.getCurrentUserId(); // Fix bug #19: was null
        guardService.deleteGuard(tenantId, id, deletedBy);
        return ResponseEntity.ok(ApiResponse.success("Guard removed", null));
    }

    @PostMapping("/{id}/photo")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(
            summary = "Upload guard photo",
            description = "Dev mode: accepts base64 but stores PENDING_UPLOAD placeholder. " +
                    "Production: send a CDN URL from an S3 presigned upload instead."
    )
    public ResponseEntity<ApiResponse<GuardResponse>> uploadPhoto(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("Photo updated",
                guardService.updatePhoto(TenantContext.getTenantIdAsObject(), id,
                        body.get("photoBase64"))));
    }
}
