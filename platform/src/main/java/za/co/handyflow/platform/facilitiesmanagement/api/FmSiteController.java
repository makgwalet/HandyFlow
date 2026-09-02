package za.co.handyflow.platform.facilitiesmanagement.api;

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
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmSiteService;
import za.co.handyflow.platform.facilitiesmanagement.dto.CreateFmSiteRequest;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmSiteResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.UpdateFmSiteRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/facilitiesmanagement/sites")
@RequiredArgsConstructor
@Tag(name = "Facilities Management - Sites", description = "Client premises register")
public class FmSiteController {

    private final FmSiteService siteService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_READ','FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<Page<FmSiteResponse>>> getSites(
            @RequestParam(required = false) UUID clientId,
            @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(
                siteService.getSites(TenantContext.getTenantIdAsObject(), clientId, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_READ','FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmSiteResponse>> getSite(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(siteService.getSite(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmSiteResponse>> createSite(@Valid @RequestBody CreateFmSiteRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Site created", siteService.createSite(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmSiteResponse>> updateSite(@PathVariable UUID id, @RequestBody UpdateFmSiteRequest request) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Site updated", siteService.updateSite(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmSiteResponse>> closeSite(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Site closed", siteService.closeSite(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN')")
    public ResponseEntity<ApiResponse<FmSiteResponse>> reopenSite(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success("Site reopened", siteService.reopenSite(TenantContext.getTenantIdAsObject(), id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('FACILITIESMANAGEMENT_ADMIN')")
    @Operation(summary = "Soft-delete a site. Restricted to ADMIN, matching every other module's own delete-tier convention.")
    public ResponseEntity<ApiResponse<Void>> deleteSite(@PathVariable UUID id) {
        featureGuard.requireModule("facilitiesmanagement");
        siteService.deleteSite(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Site deleted", null));
    }
}
