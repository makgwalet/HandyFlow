package za.co.handyflow.platform.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.identity.application.internal.UiPreferencesService;
import za.co.handyflow.platform.identity.dto.request.UpdateTenantUiPreferencesRequest;
import za.co.handyflow.platform.identity.dto.request.UpdateUserUiPreferencesRequest;
import za.co.handyflow.platform.identity.dto.response.UiPreferencesResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/identity/ui-preferences")
@RequiredArgsConstructor
@Tag(name = "UI Preferences", description = "Theme, layout and brand colour for the app shell")
public class UiPreferencesController {

    static final String SETTINGS_MANAGE = "SETTINGS_MANAGE";

    private final UiPreferencesService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Effective appearance for the current user, plus tenant defaults and user overrides")
    public ResponseEntity<ApiResponse<UiPreferencesResponse>> get() {
        return ResponseEntity.ok(ApiResponse.success("Success",
                service.get(tenantId(), currentUserIdOrNull(), canEditTenant())));
    }

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Replace the current user's personal overrides (null = inherit tenant default)")
    public ResponseEntity<ApiResponse<UiPreferencesResponse>> updateMine(
            @Valid @RequestBody UpdateUserUiPreferencesRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Preferences saved",
                service.updateUser(tenantId(), currentUserIdOrNull(), canEditTenant(), req)));
    }

    @PutMapping("/tenant")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    @Operation(summary = "Replace tenant-wide appearance defaults and brand colour")
    public ResponseEntity<ApiResponse<UiPreferencesResponse>> updateTenant(
            @Valid @RequestBody UpdateTenantUiPreferencesRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Organisation appearance saved",
                service.updateTenant(tenantId(), currentUserIdOrNull(), req)));
    }

    private static UUID tenantId() {
        return TenantContext.getTenantIdAsObject().getValue();
    }

    /** Read-only support sessions have no real user; they get tenant defaults. */
    private static UUID currentUserIdOrNull() {
        return TenantContext.isImpersonation() ? null : TenantContext.getCurrentUserId();
    }

    private static boolean canEditTenant() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> SETTINGS_MANAGE.equals(a.getAuthority()));
    }
}
