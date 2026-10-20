package za.co.handyflow.platform.fleet.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.fleet.application.internal.TelematicsIngestService;
import za.co.handyflow.platform.fleet.dto.TelematicsIngestRequest;
import za.co.handyflow.platform.fleet.dto.TelematicsIngestResponse;
import za.co.handyflow.platform.shared.ApiResponse;

/**
 * The provider-facing ingest endpoint. It is called by a telematics provider's system, not by a
 * logged-in user, so it has no {@code @PreAuthorize} and does not use TenantContext — the tenant is
 * taken from the API key in the {@code X-Telematics-Key} header (created under
 * {@code POST /api/v1/fleet/telematics/ingest-keys}).
 * <p>
 * *** ACTION NEEDED IN YOUR SECURITY CONFIG *** The request carries no JWT, so this exact path must
 * be permitted through the authentication filter (permitAll for
 * {@code POST /api/v1/fleet/telematics/ingest}) and excluded from any filter that insists on a tenant
 * context. Authentication is then the key itself: unknown, missing or revoked keys get a 401.
 * Consider rate-limiting this path at the gateway.
 */
@RestController
@RequestMapping("/api/v1/fleet/telematics")
@RequiredArgsConstructor
@Tag(name = "Fleet - Telematics Ingest", description = "Provider-neutral GPS and device-event ingest, authenticated by API key")
public class TelematicsIngestController {

    private final TelematicsIngestService ingestService;

    @PostMapping("/ingest")
    @Operation(summary = "Send GPS positions and/or device events (max 1,000 items). Header X-Telematics-Key is the tenant's API key. "
            + "Bad items are reported in the response and never fail the batch")
    public ResponseEntity<ApiResponse<TelematicsIngestResponse>> ingest(
            @RequestHeader(value = "X-Telematics-Key", required = false) String apiKey,
            @Valid @RequestBody TelematicsIngestRequest request) {
        return ResponseEntity.ok(ApiResponse.success(ingestService.ingest(apiKey, request)));
    }
}