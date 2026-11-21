// security/api/GuardCpController.java

package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.security.application.internal.CloseProtectionService;
import za.co.handyflow.platform.security.dto.GuardCpProfileResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

/**
 * GuardCpController — guard-facing CP profile endpoint for the Shield app.
 *
 * Intentionally NOT gated with VIP_DETAIL_ACCESS — this endpoint exists so
 * any guard who is on a CP assignment can fetch their own status on
 * startup without requiring the full VIP clearance authority.
 *
 * SECURITY FIX (audit 1.4): it used to live at /api/v1/security/guard-cp/profile with a free
 * {@code guardId} parameter and no authority check, so any signed-in staff member of the tenant could read any guard's principal
 * codename, threat level and the next itinerary stops (addresses, coordinates, times). It now lives on the guard path, needs SECURITY_GUARD,
 * and takes the guard from the session token: a guard can only ever ask about themselves.
 *
 * Why a separate controller from CloseProtectionController?
 * CloseProtectionController has VIP_DETAIL_ACCESS at the class level, which
 * applies to every method in the class. A guard without that authority
 * (the common case — most guards will never hold VIP_DETAIL_ACCESS)
 * needs this one endpoint to work.  Class-level gates can't be selectively
 * overridden in Spring Security — the only clean solution is a separate
 * controller with no class-level authority requirement.
 */
@Tag(name = "Security - CP Guard Profile")
@RestController
@RequestMapping("/api/v1/guard/cp")
@RequiredArgsConstructor
public class GuardCpController {

    private final CloseProtectionService cpService;

    @GetMapping("/profile")
    @PreAuthorize("hasAuthority('SECURITY_GUARD')")
    @Operation(
            summary = "Guard's current CP assignment status",
            description = """
            Called by the Shield app on startup/login for the signed-in guard. Returns the guard's
            active CP detail (if any), their role on it, the principal's
            codename (NEVER real name) and threat level, and the next 3
            upcoming itinerary stops. Used to render the Shield app's CP
            home screen and arm the duress button.

            Returns onActiveDetail=false with empty fields if the guard
            has no current CP assignment — the app shows the standard
            patrol UI instead of the CP UI in that case.

            No VIP_DETAIL_ACCESS required; the guard is taken from the session token.
            """)
    public ResponseEntity<ApiResponse<GuardCpProfileResponse>> getGuardCpProfile() {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                cpService.getGuardCpProfile(tenantId, TenantContext.getCurrentUserId())));
    }
}
