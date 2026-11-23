// security/api/SiteScopeController.java
package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.security.application.internal.SiteScopeService;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Security - Site scope")
public class SiteScopeController {

    public record ScopeRequest(List<UUID> clientIds, List<UUID> siteIds) {}

    private final SiteScopeService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/api/v1/security/my-site-scope")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Which sites the signed-in user can see (restricted=false means the whole company)")
    public ResponseEntity<ApiResponse<SiteScopeService.View>> mine() {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("OK", service.view(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId())));
    }

    @GetMapping("/api/v1/security/users/{userId}/site-scope")
    @PreAuthorize("hasAuthority('SECURITY_ADMIN')")
    @Operation(summary = "The sites, clients and branches a user is limited to; no entries means the whole company")
    public ResponseEntity<ApiResponse<SiteScopeService.View>> get(@PathVariable UUID userId) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("OK", service.view(TenantContext.getTenantIdAsObject(), userId)));
    }

    @PutMapping("/api/v1/security/users/{userId}/site-scope")
    @PreAuthorize("hasAuthority('SECURITY_ADMIN')")
    @Operation(summary = "Limit a user to some clients and sites (replaces the previous list; empty lists remove this part)",
            description = "Branch scope is managed with the branch assignments and is not changed here. You cannot change your own access, and only a user with access to the whole company can change anyone's. Audited.")
    public ResponseEntity<ApiResponse<SiteScopeService.View>> set(@PathVariable UUID userId, @Valid @RequestBody ScopeRequest r) {
        featureGuard.requireModule("security");
        // Only someone with the whole company may change anyone's scope. A limited administrator could otherwise widen
        // another user's access beyond their own, and (because nobody can change their own scope) the last unrestricted
        // person can then never be limited: no one is left to do it.
        SiteScopeService.checkUnrestricted(service, TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("OK", service.replace(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), userId, r.clientIds(), r.siteIds())));
    }
}
