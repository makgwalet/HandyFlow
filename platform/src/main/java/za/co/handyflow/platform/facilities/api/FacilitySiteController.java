package za.co.handyflow.platform.facilities.api;

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
import za.co.handyflow.platform.facilities.application.internal.FacilitySiteService;
import za.co.handyflow.platform.facilities.dto.SiteResponse;
import za.co.handyflow.platform.facilities.dto.UpsertSiteRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/facilities/sites")
@RequiredArgsConstructor
@Tag(name = "Facilities - Sites", description = "Site/premises register")
public class FacilitySiteController {

    private final FacilitySiteService siteService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('FACILITIES_READ','FACILITIES_MANAGE','FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<Page<SiteResponse>>> getSites(@PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(siteService.getSites(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('FACILITIES_READ','FACILITIES_MANAGE','FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<SiteResponse>> getSite(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(siteService.getSite(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<SiteResponse>> createSite(@Valid @RequestBody UpsertSiteRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Site created", siteService.createSite(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<SiteResponse>> updateSite(@PathVariable UUID id, @Valid @RequestBody UpsertSiteRequest request) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Site updated", siteService.updateSite(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<SiteResponse>> closeSite(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Site closed", siteService.closeSite(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAnyAuthority('FACILITIES_MANAGE','FACILITIES_ADMIN')")
    public ResponseEntity<ApiResponse<SiteResponse>> reopenSite(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success("Site reopened", siteService.reopenSite(TenantContext.getTenantIdAsObject(), id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('FACILITIES_ADMIN')")
    @Operation(summary = "Soft-delete a site. Restricted to ADMIN, matching every other module's own delete-tier convention.")
    public ResponseEntity<ApiResponse<Void>> deleteSite(@PathVariable UUID id) {
        featureGuard.requireModule("facilities");
        siteService.deleteSite(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Site deleted", null));
    }
}
