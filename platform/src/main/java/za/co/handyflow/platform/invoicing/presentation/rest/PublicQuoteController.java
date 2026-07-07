package za.co.handyflow.platform.invoicing.presentation.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.invoicing.application.internal.QuotePublicAccessService;
import za.co.handyflow.platform.shared.ApiResponse;

import java.util.UUID;

/**
 * PUBLIC, UNAUTHENTICATED routes — reachable by anyone holding a valid
 * quote token (sent only via email to the client).
 *
 * WHY /api/v1/portal/quotes and not a new prefix?
 * SecurityConfig already permitAll's "/api/v1/portal/**" (used elsewhere
 * for other client-facing, no-login flows). Nesting here means this
 * feature needs ZERO changes to SecurityConfig — it inherits a path
 * already proven to pass through the custom JWT filter chain correctly,
 * rather than introducing a new permitAll rule that would need separate
 * verification against those filters.
 *
 * Do NOT add @PreAuthorize here or route through tenant-scoped
 * infrastructure (TenantContext etc.) — there is no authenticated
 * principal on these requests. The token itself is the only credential.
 */
@RestController
@RequestMapping("/api/v1/portal/quotes")
@RequiredArgsConstructor
public class PublicQuoteController {

    private final QuotePublicAccessService publicAccessService;

    @GetMapping("/{token}")
    public ResponseEntity<ApiResponse<?>> view(@PathVariable UUID token) {
        return ResponseEntity.ok(ApiResponse.success("Quote", publicAccessService.getByToken(token)));
    }

    @PostMapping("/{token}/accept")
    public ResponseEntity<ApiResponse<?>> accept(@PathVariable UUID token) {
        return ResponseEntity.ok(ApiResponse.success("Quote accepted", publicAccessService.acceptByToken(token)));
    }

    @PostMapping("/{token}/reject")
    public ResponseEntity<ApiResponse<?>> reject(@PathVariable UUID token) {
        return ResponseEntity.ok(ApiResponse.success("Quote rejected", publicAccessService.rejectByToken(token)));
    }
}