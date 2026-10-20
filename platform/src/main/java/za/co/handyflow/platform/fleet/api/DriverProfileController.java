package za.co.handyflow.platform.fleet.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fleet.application.internal.DriverScoreService;
import za.co.handyflow.platform.fleet.dto.DriverProfileResponse;
import za.co.handyflow.platform.fleet.dto.DriverScoreSettingsResponse;
import za.co.handyflow.platform.fleet.dto.DriverScoreSummaryResponse;
import za.co.handyflow.platform.fleet.dto.UpdateDriverScoreSettingsRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Driver 360 profile, the driver leaderboard, and the score configuration.
 * FLEET_READ to view; FLEET_ADMIN to change the weights — they decide how every driver
 * is assessed, so changing them is an administrative act, not routine management.
 */
@RestController
@RequestMapping("/api/v1/fleet")
@RequiredArgsConstructor
@Tag(name = "Fleet - Driver Performance", description = "Driver 360 profile and Safety & Performance Score")
public class DriverProfileController {

    private final DriverScoreService scoreService;
    private final FeatureGuard featureGuard;

    @GetMapping("/drivers/{id}/profile")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "Driver 360: document compliance, Safety & Performance Score with its components, activity, "
            + "accidents and damage cost. Defaults to the configured lookback ending today")
    public ResponseEntity<ApiResponse<DriverProfileResponse>> getProfile(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(
                scoreService.getProfile(TenantContext.getTenantIdAsObject(), id, from, to)));
    }

    @GetMapping("/driver-scores")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "Leaderboard of every active driver by score, best first (drivers with no scoreable data last)")
    public ResponseEntity<ApiResponse<List<DriverScoreSummaryResponse>>> getScores(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(
                scoreService.getScores(TenantContext.getTenantIdAsObject(), from, to)));
    }

    @GetMapping("/driver-score-settings")
    @PreAuthorize("hasAuthority('FLEET_READ')")
    @Operation(summary = "The tenant's score weights, lookback and band thresholds (built-in defaults until saved)")
    public ResponseEntity<ApiResponse<DriverScoreSettingsResponse>> getSettings() {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success(
                scoreService.getSettings(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/driver-score-settings")
    @PreAuthorize("hasAuthority('FLEET_ADMIN')")
    @Operation(summary = "Configure the score: weights must total 100; thresholds must descend excellent > good > fair")
    public ResponseEntity<ApiResponse<DriverScoreSettingsResponse>> updateSettings(
            @Valid @RequestBody UpdateDriverScoreSettingsRequest request) {
        featureGuard.requireModule("fleet");
        return ResponseEntity.ok(ApiResponse.success("Score settings saved",
                scoreService.updateSettings(TenantContext.getTenantIdAsObject(),
                        UserContext.getCurrentUserId(), request)));
    }
}