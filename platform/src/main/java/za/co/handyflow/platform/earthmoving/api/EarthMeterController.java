package za.co.handyflow.platform.earthmoving.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.earthmoving.application.internal.EarthMeterService;
import za.co.handyflow.platform.earthmoving.dto.ChangeMeterRequest;
import za.co.handyflow.platform.earthmoving.dto.CorrectReadingRequest;
import za.co.handyflow.platform.earthmoving.dto.DisputeReadingRequest;
import za.co.handyflow.platform.earthmoving.dto.ResolveDisputeRequest;
import za.co.handyflow.platform.earthmoving.dto.MeterReadingResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/earthmoving/assets/{id}/meter")
@RequiredArgsConstructor
@Tag(name = "Earthmoving meters")
public class EarthMeterController {

    private final EarthMeterService meters;
    private final FeatureGuard featureGuard;

    @GetMapping("/readings")
    @PreAuthorize("hasAuthority('EARTHMOVING_READ')")
    @Operation(summary = "A machine's meter history, newest first")
    public ResponseEntity<ApiResponse<List<MeterReadingResponse>>> history(@PathVariable UUID id) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Meter readings", meters.history(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/correction")
    @PreAuthorize("hasAuthority('EARTHMOVING_MAINTAIN')")
    @Operation(summary = "Correct a reading that was entered wrongly (reason required)")
    public ResponseEntity<ApiResponse<MeterReadingResponse>> correct(@PathVariable UUID id, @Valid @RequestBody CorrectReadingRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Reading corrected",
                meters.correct(TenantContext.getTenantIdAsObject(), id, request, UserContext.getCurrentUserId())));
    }

    @PostMapping("/readings/{readingId}/dispute")
    @PreAuthorize("hasAnyAuthority('EARTHMOVING_MAINTAIN','EARTHMOVING_ADMIN')")
    @Operation(summary = "Dispute a kilometre reading; charges built on it are held until it is confirmed or corrected")
    public ResponseEntity<ApiResponse<MeterReadingResponse>> dispute(@PathVariable UUID id, @PathVariable UUID readingId,
                                                                      @Valid @RequestBody DisputeReadingRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Reading disputed",
                meters.dispute(TenantContext.getTenantIdAsObject(), id, readingId, request, UserContext.getCurrentUserId())));
    }

    @PatchMapping("/disputes/{disputeId}/confirm")
    @PreAuthorize("hasAuthority('EARTHMOVING_MAINTAIN')")
    @Operation(summary = "Confirm a disputed reading stands (note required)")
    public ResponseEntity<ApiResponse<MeterReadingResponse>> confirm(@PathVariable UUID id, @PathVariable UUID disputeId,
                                                                      @Valid @RequestBody ResolveDisputeRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Dispute closed",
                meters.confirm(TenantContext.getTenantIdAsObject(), id, disputeId, request, UserContext.getCurrentUserId())));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('EARTHMOVING_ADMIN')")
    @Operation(summary = "Change a machine's meter between hours and kilometres (reason required)")
    public ResponseEntity<ApiResponse<MeterReadingResponse>> change(@PathVariable UUID id, @Valid @RequestBody ChangeMeterRequest request) {
        featureGuard.requireModule("earthmoving");
        return ResponseEntity.ok(ApiResponse.success("Meter changed",
                meters.change(TenantContext.getTenantIdAsObject(), id, request, UserContext.getCurrentUserId())));
    }
}
