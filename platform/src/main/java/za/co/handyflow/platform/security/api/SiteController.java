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
@Tag(name = "Security - Sites", description = "Client site management with QR checkpoints")
public class SiteController {

    private final SiteService  siteService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<Page<SiteResponse>>> getSites(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        featureGuard.requireModule("security");
        var tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                siteService.getSites(tenantId, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Get site with all checkpoints and their QR codes")
    public ResponseEntity<ApiResponse<SiteResponse>> getSite(@PathVariable UUID id) {
        featureGuard.requireModule("security");
        var tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(siteService.getSite(tenantId, id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Create a new site")
    public ResponseEntity<ApiResponse<SiteResponse>> createSite(
            @Valid @RequestBody CreateSiteRequest request
    ) {
        featureGuard.requireModule("security");
        var tenantId = TenantContext.getTenantIdAsObject();
        var site = siteService.createSite(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Site created", site));
    }

    @PostMapping("/{id}/checkpoints")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Add a checkpoint to a site — generates unique QR code")
    public ResponseEntity<ApiResponse<SiteResponse>> addCheckpoint(
            @PathVariable UUID id,
            @Valid @RequestBody CreateCheckpointRequest request
    ) {
        featureGuard.requireModule("security");
        var tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Checkpoint added",
                        siteService.addCheckpoint(tenantId, id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_DELETE')")
    public ResponseEntity<ApiResponse<Void>> deleteSite(@PathVariable UUID id) {
        featureGuard.requireModule("security");
        var tenantId = TenantContext.getTenantIdAsObject();
        siteService.deleteSite(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success("Site deleted", null));
    }

    @PostMapping("/{id}/terminate")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Terminate site contract")
    public ResponseEntity<ApiResponse<SiteResponse>> terminateSite(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        featureGuard.requireModule("security");
        var tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success("Contract terminated",
                siteService.terminateSite(tenantId, id, body.get("reason"))));
    }
}