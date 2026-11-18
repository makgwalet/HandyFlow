package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.fuel.application.internal.FuelPosImportService;
import za.co.handyflow.platform.fuel.dto.PosImportDtos.IncomingShift;
import za.co.handyflow.platform.fuel.dto.PosImportDtos.IngestResult;
import za.co.handyflow.platform.shared.ApiResponse;

/**
 * Phase 8e (D03): the sender-facing import endpoint. It is called by a pump or POS system, not by a logged-in user, so it has no
 * a method-security annotation and does not use the request's tenant context: the company comes from the API key in the {@code X-Fuel-Import-Key} header
 * (created under {@code POST /api/v1/fuel/forecourt/imports/keys}). An unknown, missing or revoked key gets 401.
 * This exact path is permitted in SecurityConfig and rate limited in RateLimitFilter. It only stages a shift for review.
 */
@RestController
@RequestMapping("/api/v1/fuel/import")
@RequiredArgsConstructor
@Tag(name = "Fuel Import", description = "Inbound pump/POS shift import, authenticated by API key, held for review")
public class FuelImportIngestController {

    private final FuelPosImportService imports;
    private final za.co.handyflow.platform.fuel.application.internal.FuelImportAutoAcceptService autoAccept;

    @PostMapping("/pos-shifts")
    @Operation(summary = "Send one finished shift. Header X-Fuel-Import-Key is the company's key. Safe to repeat: the same source and externalId is staged once. "
            + "The shift is held for a supervisor to accept; nothing changes stock until then")
    public ResponseEntity<ApiResponse<IngestResult>> receive(
            @RequestHeader(value = "X-Fuel-Import-Key", required = false) String apiKey,
            @Valid @RequestBody IncomingShift shift) {
        return ResponseEntity.ok(ApiResponse.success(autoAccept.afterShift(imports.ingest(apiKey, shift))));
    }
}
