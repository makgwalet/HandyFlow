// security/api/SiteController.java

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
import za.co.handyflow.platform.security.application.internal.SiteService;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/security/sites")
@RequiredArgsConstructor
@Tag(name = "Security - Sites", description = "Client site management with QR/NFC checkpoints")
public class SiteController {

    private final SiteService  siteService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List all active sites — checkpoints not included in list view")
    public ResponseEntity<ApiResponse<Page<SiteResponse>>> getSites(
            @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(
                siteService.getSites(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Get site detail with all checkpoints and their QR/NFC/BLE identifiers")
    public ResponseEntity<ApiResponse<SiteResponse>> getSite(@PathVariable UUID id) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(
                siteService.getSite(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Register a new client site")
    public ResponseEntity<ApiResponse<SiteResponse>> createSite(
            @Valid @RequestBody CreateSiteRequest request) {
        featureGuard.requireModule("security");
        var site = siteService.createSite(TenantContext.getTenantIdAsObject(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Site created", site));
    }

    @PostMapping("/{id}/checkpoints")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Add a checkpoint to a site — generates unique QR code automatically")
    public ResponseEntity<ApiResponse<SiteResponse>> addCheckpoint(
            @PathVariable UUID id,
            @Valid @RequestBody CreateCheckpointRequest request) {
        featureGuard.requireModule("security");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Checkpoint added",
                        siteService.addCheckpoint(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_DELETE')")
    @Operation(summary = "Soft-delete a site (preserves shift/incident/scan history)")
    public ResponseEntity<ApiResponse<Void>> deleteSite(@PathVariable UUID id) {
        featureGuard.requireModule("security");
        // Fix bug #19 pattern: pass actor ID
        UUID deletedBy = TenantContext.getCurrentUserId();
        siteService.deleteSite(TenantContext.getTenantIdAsObject(), id, deletedBy);
        return ResponseEntity.ok(ApiResponse.success("Site deleted", null));
    }

    @PostMapping("/{id}/terminate")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(
            summary = "Terminate site contract",
            description = "Sets contractStatus=TERMINATED, records terminationReason and terminatedAt timestamp."
    )
    public ResponseEntity<ApiResponse<SiteResponse>> terminateSite(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        featureGuard.requireModule("security");
        UUID terminatedBy = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("Contract terminated",
                siteService.terminateSite(TenantContext.getTenantIdAsObject(), id,
                        body.get("reason"), terminatedBy)));
    }
}
