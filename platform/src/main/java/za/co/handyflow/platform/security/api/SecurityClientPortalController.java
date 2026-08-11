// security/api/ClientPortalController.java

package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.security.application.internal.ClientPortalService;
import za.co.handyflow.platform.security.dto.ClientPortalResponse;
import za.co.handyflow.platform.security.dto.SendPortalLinkRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.Map;
import java.util.UUID;

/**
 * ClientPortalController — two groups of endpoints, plus (CHANGE) a third:
 *
 * GROUP 1: Public (no auth) — /api/v1/portal/{token}
 * GROUP 2: Authenticated (tenant only) — /api/v1/security/sites/{id}/portal/*
 *   Generates and disables portal tokens. Requires USER_UPDATE authority.
 * GROUP 3 (NEW): POST /api/v1/security/sites/{id}/portal/send -- emails the
 *   existing portal link to an arbitrary recipient (audit gap: "how do we
 *   send it to the client?"). Sits here, next to generate/disable, rather
 *   than on SiteController, since that's where the rest of the portal
 *   lifecycle already lives.
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

    // ── Send portal link (new) ──────────────────────────────────────────────────

    @PostMapping("/api/v1/security/sites/{id}/portal/send")
    @Operation(
            summary = "Email the client portal link to a recipient",
            description = """
            Sends the site's existing portal link by email — requires the
            portal to already be enabled (POST .../portal/generate first;
            this endpoint does not implicitly create one). recipientEmail is
            supplied per-send, not stored on the site (Site has no
            contactEmail field). This endpoint only sends the portal link
            itself; it does not attach a monthly report or invoice (those
            live in the Reporting/Invoicing modules respectively) -- pairing
            "send portal link" with "email this report" is a natural
            follow-up once someone owns that cross-module flow.
            """)
    public ResponseEntity<ApiResponse<Void>> sendPortalLink(
            @PathVariable UUID id,
            @Valid @RequestBody SendPortalLinkRequest req) {
        portalService.sendPortalLink(
                id, TenantContext.getTenantIdAsObject(), req.recipientEmail(), req.customMessage());
        return ResponseEntity.ok(ApiResponse.success("Portal link sent", null));
    }
}