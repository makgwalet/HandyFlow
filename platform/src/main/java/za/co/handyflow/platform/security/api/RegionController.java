package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.security.application.internal.RegionService;
import za.co.handyflow.platform.security.application.internal.SiteScopeService;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

@Tag(name = "Security - Regions")
@RestController
@RequiredArgsConstructor
public class RegionController {

    public record RegionRequest(String name, String description) {}

    public record BranchRegionRequest(UUID regionId) {}

    private final RegionService service;
    private final FeatureGuard featureGuard;

    /** Optional so a hand-built controller still works; in the application it is always there. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private SiteScopeService scope;

    /** Regions decide who sees what, so only a user with the whole company may change them. Reading the list stays open. */
    private TenantId wholeCompany() {
        TenantId t = TenantContext.getTenantIdAsObject();
        SiteScopeService.checkUnrestricted(scope, t, TenantContext.getCurrentUserId());
        return t;
    }

    @GetMapping("/api/v1/security/regions")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Regions with the branches in each and how many users are limited to each")
    public ResponseEntity<ApiResponse<List<RegionService.RegionView>>> list() {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("OK", service.list(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/api/v1/security/regions")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Create a region. Whole-company only")
    public ResponseEntity<ApiResponse<List<RegionService.RegionView>>> create(@RequestBody RegionRequest r) {
        featureGuard.requireModule("security");
        TenantId t = wholeCompany();
        return ResponseEntity.status(201).body(ApiResponse.success("OK", service.create(t, TenantContext.getCurrentUserId(), r.name(), r.description())));
    }

    @PutMapping("/api/v1/security/regions/{id}")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Rename a region or change its description. Whole-company only")
    public ResponseEntity<ApiResponse<List<RegionService.RegionView>>> update(@PathVariable UUID id, @RequestBody RegionRequest r) {
        featureGuard.requireModule("security");
        TenantId t = wholeCompany();
        return ResponseEntity.ok(ApiResponse.success("OK", service.update(t, TenantContext.getCurrentUserId(), id, r.name(), r.description())));
    }

    @DeleteMapping("/api/v1/security/regions/{id}")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Delete a region (its branches stay, with no region). Refused while any user is limited to it. Whole-company only")
    public ResponseEntity<ApiResponse<List<RegionService.RegionView>>> delete(@PathVariable UUID id) {
        featureGuard.requireModule("security");
        TenantId t = wholeCompany();
        return ResponseEntity.ok(ApiResponse.success("OK", service.delete(t, TenantContext.getCurrentUserId(), id)));
    }

    @PutMapping("/api/v1/security/branches/{branchId}/region")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Put a branch in a region, or take it out (regionId left out). Changes what users limited to those regions can see. Whole-company only")
    public ResponseEntity<ApiResponse<List<RegionService.RegionView>>> assign(@PathVariable UUID branchId, @RequestBody BranchRegionRequest r) {
        featureGuard.requireModule("security");
        TenantId t = wholeCompany();
        return ResponseEntity.ok(ApiResponse.success("OK", service.assignBranch(t, TenantContext.getCurrentUserId(), branchId, r.regionId())));
    }
}
