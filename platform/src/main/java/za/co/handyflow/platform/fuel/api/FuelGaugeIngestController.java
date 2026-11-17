package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.fuel.application.internal.FuelGaugeImportService;
import za.co.handyflow.platform.fuel.dto.GaugeImportDtos.IncomingGauge;
import za.co.handyflow.platform.fuel.dto.PosImportDtos.IngestResult;
import za.co.handyflow.platform.shared.ApiResponse;

/**
 * The sender-facing tank-gauge endpoint. It is called by a gauge or tank-monitoring system, not by a logged-in user, so it has no
 * {@code @PreAuthorize} and does not use TenantContext: the company comes from the API key in the {@code X-Fuel-Import-Key} header. An unknown,
 * missing or revoked key gets 401. This exact path is permitted in SecurityConfig and rate limited in RateLimitFilter. It only stages a reading for review.
 */
@RestController
@RequestMapping("/api/v1/fuel/import")
@RequiredArgsConstructor
@Tag(name = "Fuel Gauge Import", description = "Inbound tank-gauge readings, authenticated by API key, held for review")
public class FuelGaugeIngestController {

    private final FuelGaugeImportService gauges;

    @PostMapping("/tank-gauges")
    @Operation(summary = "Send one tank level. Header X-Fuel-Import-Key is the company's key. Safe to repeat: the same source and externalId is staged once. "
            + "The reading is held for a person to accept; nothing changes stock or dip records until then")
    public ResponseEntity<ApiResponse<IngestResult>> receive(
            @RequestHeader(value = "X-Fuel-Import-Key", required = false) String apiKey,
            @Valid @RequestBody IncomingGauge reading) {
        return ResponseEntity.ok(ApiResponse.success(gauges.ingest(apiKey, reading)));
    }
}
