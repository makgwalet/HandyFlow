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
@Tag(name = "Security - Guards", description = "Guard management")
public class GuardController {

    private final GuardService guardService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List all guards")
    public ResponseEntity<ApiResponse<Page<GuardResponse>>> getGuards(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        featureGuard.requireModule("security");
        var tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                guardService.getGuards(tenantId, search, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<GuardResponse>> getGuard(@PathVariable UUID id) {
        featureGuard.requireModule("security");
        var tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(guardService.getGuard(tenantId, id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Create a new guard")
    public ResponseEntity<ApiResponse<GuardResponse>> createGuard(
            @Valid @RequestBody CreateGuardRequest request
    ) {
        featureGuard.requireModule("security");
        var tenantId = TenantContext.getTenantIdAsObject();
        var guard = guardService.createGuard(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Guard created", guard));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<ApiResponse<GuardResponse>> updateGuard(
            @PathVariable UUID id,
            @Valid @RequestBody CreateGuardRequest request
    ) {
        featureGuard.requireModule("security");
        var tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                guardService.updateGuard(tenantId, id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_DELETE')")
    public ResponseEntity<ApiResponse<Void>> deleteGuard(@PathVariable UUID id) {
        featureGuard.requireModule("security");
        var tenantId = TenantContext.getTenantIdAsObject();
        guardService.deleteGuard(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success("Guard deleted", null));
    }

    // Add this endpoint to GuardController:
    @PostMapping("/{id}/photo")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Upload guard photo (base64)")
    public ResponseEntity<ApiResponse<GuardResponse>> uploadPhoto(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("Photo updated",
                guardService.updatePhoto(TenantContext.getTenantIdAsObject(), id, body.get("photoBase64"))));
    }
}
