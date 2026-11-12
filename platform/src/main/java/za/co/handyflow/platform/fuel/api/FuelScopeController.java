package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelScopeService;
import za.co.handyflow.platform.fuel.dto.ScopeDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Phase 8c (G01): limits a user to some divisions and depots. Managing limits needs the configuration permission.
 * These routes are shared foundation, so a user limited to other divisions can still reach them to be corrected.
 */
@RestController
@RequestMapping("/api/v1/fuel/scopes")
@RequiredArgsConstructor
@Tag(name = "Fuel Access Limits", description = "Limit a user to some divisions and depots")
public class FuelScopeController {

    private final FuelScopeService scopes;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('FUEL_CONFIGURATION_MANAGE')")
    public ResponseEntity<ApiResponse<List<UserScope>>> list() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(scopes.list(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<MyScope>> mine() {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(scopes.mine(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId())));
    }

    @PutMapping("/users/{userId}")
    @PreAuthorize("hasAuthority('FUEL_CONFIGURATION_MANAGE')")
    public ResponseEntity<ApiResponse<UserScope>> replace(@PathVariable UUID userId, @RequestBody SaveScopeRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Access limits saved",
                scopes.replace(TenantContext.getTenantIdAsObject(), userId, req, TenantContext.getCurrentUserId())));
    }
}
