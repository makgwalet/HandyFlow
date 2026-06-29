// security/api/ClientPortalController.java

package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.security.application.internal.ClientPortalService;
import za.co.handyflow.platform.security.dto.ClientPortalResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.Map;
import java.util.UUID;

/**
 * ClientPortalController — two groups of endpoints:
 *
 * GROUP 1: Public (no auth) — /api/v1/portal/{token}
 *   The client accesses this URL directly from their browser.  The token IS the
 *   authentication.  This endpoint must be whitelisted in your SecurityConfig:
 *
 *     .requestMatchers("/api/v1/portal/**").permitAll()
 *
 *   WHY permitAll and not a custom token filter?
 *   The token is already cryptographically strong (UUID = 2^122 entropy).
 *   A custom Spring Security filter adds complexity with no security benefit
 *   at this scale.  The tenant can regenerate the token instantly if it's
 *   compromised.  If the tenant grows to where they need fine-grained portal
 *   access control (per-user, per-site), that's Phase 3 scope.
 *
 * GROUP 2: Authenticated (tenant only) — /api/v1/security/sites/{id}/portal
 *   Generates and disables portal tokens.  Requires USER_UPDATE authority.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Security - Client Portal", description = "Read-only site dashboard for clients")
public class SecurityClientPortalController {

    private final ClientPortalService portalService;

    // ── Public portal endpoint ─────────────────────────────────────────────────

    @GetMapping("/api/v1/portal/{token}")
    @Operation(
            summary = "Client portal — read-only site dashboard",
            description = "No authentication required. The token in the URL IS the credential. " +
                    "Returns current guards on duty, recent shifts, open incidents, " +
                    "and checkpoint scan count for the current week."
    )
    public ResponseEntity<ApiResponse<ClientPortalResponse>> getPortal(
            @PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                portalService.getPortalData(token)));
    }

    // ── Authenticated management endpoints ────────────────────────────────────

    @PostMapping("/api/v1/security/sites/{id}/portal/generate")
    @Operation(
            summary = "Generate (or regenerate) a client portal token for this site",
            description = "Replaces any existing token — the old portal URL immediately stops working. " +
                    "Returns the new token. Share the portal URL: /portal/{token}"
    )
    public ResponseEntity<ApiResponse<Map<String, String>>> generateToken(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body) {
        String label = body != null ? body.get("label") : null;
        String token = portalService.generatePortalToken(
                id, label, TenantContext.getTenantIdAsObject());
        return ResponseEntity.ok(ApiResponse.success("Portal token generated",
                Map.of("token", token, "url", "/portal/" + token)));
    }

    @DeleteMapping("/api/v1/security/sites/{id}/portal")
    @Operation(summary = "Disable the client portal for this site — clears the token")
    public ResponseEntity<ApiResponse<Void>> disablePortal(@PathVariable UUID id) {
        portalService.disablePortal(id, TenantContext.getTenantIdAsObject());
        return ResponseEntity.ok(ApiResponse.success("Portal disabled", null));
    }
}
