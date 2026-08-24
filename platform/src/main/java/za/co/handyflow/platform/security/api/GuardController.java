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

/**
 * FIX: backlog 1.7 — every endpoint in this controller and SiteController
 * was gated on generic USER_READ/USER_CREATE/USER_UPDATE/USER_DELETE,
 * same bug class as Fuel (5.2), Accounting (8.1), and Fleet (12.1), all
 * already fixed. Two tiers plus a stricter delete tier, matching
 * Fleet/Fuel's exact precedent: SECURITY_READ, SECURITY_MANAGE for
 * routine writes (including status changes — even TERMINATED, matching
 * how HR's own terminateEmployee() stayed on its module's MANAGE tier
 * rather than a stricter one, for consistency across modules), and
 * SECURITY_ADMIN reserved for delete specifically. No new permission
 * migration needed — SECURITY_READ/SECURITY_MANAGE/SECURITY_ADMIN
 * already exist and are already auto-granted to every tenant's ADMIN
 * role.
 * <p>
 * WHILE INVESTIGATING: found the identical bug in two more Security
 * controllers not named in this specific finding — GuardAuthController
 * (enrol/revoke-tokens) and GuardScreeningController (whole class) — both
 * small and fixed alongside this. A third, ShiftController, has the same
 * pattern but is clearly a larger file I haven't fully seen — flagged
 * for its own pass rather than guessed at. GuardCpController is
 * confirmed DELIBERATELY ungated (codename-only response, documented
 * reasoning) — correctly left untouched.
 */
@RestController
@RequestMapping("/api/v1/security/guards")
@RequiredArgsConstructor
@Tag(name = "Security - Guards", description = "Guard management and status workflow")
public class GuardController {

    private final GuardService guardService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "List all guards with optional name/PSiRA search")
    public ResponseEntity<ApiResponse<Page<GuardResponse>>> getGuards(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(
                guardService.getGuards(TenantContext.getTenantIdAsObject(), search, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Get a single guard by ID")
    public ResponseEntity<ApiResponse<GuardResponse>> getGuard(@PathVariable UUID id) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(
                guardService.getGuard(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Register a new guard")
    public ResponseEntity<ApiResponse<GuardResponse>> createGuard(
            @Valid @RequestBody CreateGuardRequest request) {
        featureGuard.requireModule("security");
        var guard = guardService.createGuard(TenantContext.getTenantIdAsObject(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Guard created", guard));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Update guard details (name, PSiRA, grade, etc.)")
    public ResponseEntity<ApiResponse<GuardResponse>> updateGuard(
            @PathVariable UUID id,
            @Valid @RequestBody CreateGuardRequest request) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("Guard updated",
                guardService.updateGuard(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
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
        var changedBy  = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("Guard status updated",
                guardService.updateStatus(tenantId, id, request, changedBy)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SECURITY_ADMIN')")
    @Operation(summary = "Soft-delete a guard record (preserves shift/incident history)")
    public ResponseEntity<ApiResponse<Void>> deleteGuard(@PathVariable UUID id) {
        featureGuard.requireModule("security");
        var tenantId  = TenantContext.getTenantIdAsObject();
        var deletedBy = TenantContext.getCurrentUserId();
        guardService.deleteGuard(tenantId, id, deletedBy);
        return ResponseEntity.ok(ApiResponse.success("Guard removed", null));
    }

    @PostMapping("/{id}/photo")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(
            summary = "Upload guard photo",
            description = "Dev mode: accepts base64 but stores PENDING_UPLOAD placeholder. " +
                    "Production: send a CDN URL from an S3 presigned upload instead.")
    public ResponseEntity<ApiResponse<GuardResponse>> updatePhoto(
            @PathVariable UUID id, @RequestBody Map<String, String> body) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("Photo updated",
                guardService.updatePhoto(TenantContext.getTenantIdAsObject(), id, body.get("photoBase64"))));
    }
}