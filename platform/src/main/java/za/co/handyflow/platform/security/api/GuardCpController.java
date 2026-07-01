// security/api/GuardCpController.java

package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.security.application.internal.CloseProtectionService;
import za.co.handyflow.platform.security.dto.GuardCpProfileResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * GuardCpController — guard-facing CP profile endpoint for the Shield app.
 *
 * Intentionally NOT gated with VIP_DETAIL_ACCESS — this endpoint exists so
 * any guard who is on a CP assignment can fetch their own status on
 * startup without requiring the full VIP clearance authority. The response
 * returns codename-only (never real name), so there is no Part 9.3
 * confidentiality leak even without the authority gate.
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
@RequestMapping("/api/v1/security/guard-cp")
@RequiredArgsConstructor
public class GuardCpController {

    private final CloseProtectionService cpService;

    @GetMapping("/profile")
    @Operation(
            summary = "Guard's current CP assignment status",
            description = """
            Called by the Shield app on startup/login. Returns the guard's
            active CP detail (if any), their role on it, the principal's
            codename (NEVER real name) and threat level, and the next 3
            upcoming itinerary stops. Used to render the Shield app's CP
            home screen and arm the duress button.

            Returns onActiveDetail=false with empty fields if the guard
            has no current CP assignment — the app shows the standard
            patrol UI instead of the CP UI in that case.

            No VIP_DETAIL_ACCESS required — codename-only response,
            safe for any authenticated guard.
            """)
    public ResponseEntity<ApiResponse<GuardCpProfileResponse>> getGuardCpProfile(
            @RequestParam UUID guardId) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                cpService.getGuardCpProfile(tenantId, guardId)));
    }
}
