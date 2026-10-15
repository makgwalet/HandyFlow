package za.co.handyflow.platform.earthmoving.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.earthmoving.application.internal.EarthSiteReconciliationService;
import za.co.handyflow.platform.earthmoving.application.internal.EarthSiteService;
import za.co.handyflow.platform.earthmoving.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/earthmoving/sites")
@RequiredArgsConstructor
@Tag(name = "Earthmoving - Sites", description = "Reusable sites and linking older deployments to records")
public class EarthSiteController {

    private final EarthSiteService service;
    private final EarthSiteReconciliationService reconciliation;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('EARTHMOVING_READ')")
    public ResponseEntity<ApiResponse<List<SiteResponse>>> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), includeInactive)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('EARTHMOVING_WRITE')")
    public ResponseEntity<ApiResponse<SiteResponse>> create(@Valid @RequestBody SaveSiteRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Site added", service.create(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('EARTHMOVING_WRITE')")
    public ResponseEntity<ApiResponse<SiteResponse>> update(@PathVariable UUID id, @Valid @RequestBody SaveSiteRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success(service.update(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('EARTHMOVING_WRITE')")
    public ResponseEntity<ApiResponse<SiteResponse>> deactivate(@PathVariable UUID id) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success(service.deactivate(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/reconciliation")
    @PreAuthorize("hasAuthority('EARTHMOVING_ADMIN')")
    @Operation(summary = "How older typed deployments match sites and customers: matched, ambiguous, unmatched, with counts that reconcile")
    public ResponseEntity<ApiResponse<ReconciliationResponse>> report() {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success(reconciliation.report(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/reconciliation/apply-verified")
    @PreAuthorize("hasAuthority('EARTHMOVING_ADMIN')")
    @Operation(summary = "Link only exact one-to-one matches; leaves everything else for a person")
    public ResponseEntity<ApiResponse<LinkResultResponse>> applyVerified() {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success(reconciliation.applyVerified(TenantContext.getTenantIdAsObject(), UserContext.getCurrentUserId())));
    }

    @PostMapping("/reconciliation/resolve")
    @PreAuthorize("hasAuthority('EARTHMOVING_ADMIN')")
    @Operation(summary = "A person chooses the record that a typed name means")
    public ResponseEntity<ApiResponse<LinkResultResponse>> resolve(@Valid @RequestBody ResolveLinksRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success(reconciliation.resolve(TenantContext.getTenantIdAsObject(), request, UserContext.getCurrentUserId())));
    }
}
