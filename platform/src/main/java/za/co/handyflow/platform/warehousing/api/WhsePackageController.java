package za.co.handyflow.platform.warehousing.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.warehousing.application.internal.WhsePackageService;

import java.util.List;
import java.util.UUID;

/** Tenant staff see their package and usage; only platform administrators assign or change a package. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Warehousing - Packages", description = "Package, limits and usage")
public class WhsePackageController {

    private final WhsePackageService service;
    private final FeatureGuard featureGuard;

    public record AssignRequest(String packageCode, String enforcement, java.time.LocalDate effectiveFrom, java.time.LocalDate expiresOn, String note) {}
    public record OverrideRequest(String limit, int extra, java.time.LocalDate validUntil, String reason) {}

    @GetMapping("/api/v1/warehousing/package")
    @PreAuthorize("hasAnyAuthority('WAREHOUSING_READ','WAREHOUSING_MANAGE','WAREHOUSING_ADMIN')")
    public ResponseEntity<ApiResponse<WhsePackageService.Usage>> usage() {
        featureGuard.requireModule("warehousing");
        return ResponseEntity.ok(ApiResponse.success(service.usage(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/api/v1/admin/warehousing/packages")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<ApiResponse<List<WhsePackageService.PackageDef>>> packages() {
        return ResponseEntity.ok(ApiResponse.success(service.packages()));
    }

    @GetMapping("/api/v1/admin/warehousing/tenants/{tenantId}/package")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<ApiResponse<WhsePackageService.Usage>> tenantUsage(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(ApiResponse.success(service.usage(TenantId.of(tenantId))));
    }

    /** packageCode null/blank clears the package (no limits). */
    @PutMapping("/api/v1/admin/warehousing/tenants/{tenantId}/package")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<ApiResponse<WhsePackageService.Usage>> assign(@PathVariable UUID tenantId, @RequestBody AssignRequest r) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        UUID actor = auth == null ? null : UUID.fromString(auth.getPrincipal().toString());
        return ResponseEntity.ok(ApiResponse.success("Saved", service.assign(TenantId.of(tenantId), r.packageCode(), r.enforcement(), r.effectiveFrom(), r.expiresOn(), r.note(), actor)));
    }

    /** What each tenant's tier says against what the platform bills for Warehousing. The platform bills one flat catalogue price until tiered billing exists. */
    @GetMapping("/api/v1/admin/warehousing/billing-check")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<ApiResponse<List<WhsePackageService.BillingRow>>> billingCheck() {
        return ResponseEntity.ok(ApiResponse.success(service.billingCheck()));
    }

    private static UUID actor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : UUID.fromString(auth.getPrincipal().toString());
    }

    @GetMapping("/api/v1/admin/warehousing/tenants/{tenantId}/overrides")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<ApiResponse<List<WhsePackageService.Override>>> overrides(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(ApiResponse.success(service.overrides(TenantId.of(tenantId))));
    }

    /** A temporary extra allowance on one limit, with a reason. Audited. */
    @PostMapping("/api/v1/admin/warehousing/tenants/{tenantId}/overrides")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<ApiResponse<UUID>> addOverride(@PathVariable UUID tenantId, @RequestBody OverrideRequest r) {
        return ResponseEntity.ok(ApiResponse.success("Saved", service.addOverride(TenantId.of(tenantId), r.limit(), r.extra(), r.validUntil(), r.reason(), actor())));
    }

    @DeleteMapping("/api/v1/admin/warehousing/tenants/{tenantId}/overrides/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<ApiResponse<Void>> revokeOverride(@PathVariable UUID tenantId, @PathVariable UUID id) {
        service.revokeOverride(TenantId.of(tenantId), id, actor());
        return ResponseEntity.ok(ApiResponse.success("Revoked", null));
    }
}
