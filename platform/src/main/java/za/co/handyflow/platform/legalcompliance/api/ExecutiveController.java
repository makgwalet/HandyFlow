package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.ExecutiveForecastService;
import za.co.handyflow.platform.legalcompliance.application.internal.ExecutiveService;
import za.co.handyflow.platform.legalcompliance.domain.model.SnapshotSource;
import za.co.handyflow.platform.legalcompliance.dto.ExecDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;

/** Executive GRC overview and posture snapshots */
@RestController
@RequestMapping("/api/v1/legalcompliance/executive")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - Executive overview", description = "Roll-up of the compliance registers, with dated posture snapshots")
public class ExecutiveController {

    private static final String CAN_READ = "hasAnyAuthority('LEGALCOMPLIANCE_READ','LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')";
    private static final String CAN_SNAPSHOT = "hasAnyAuthority('LEGALCOMPLIANCE_MANAGE','LEGALCOMPLIANCE_ADMIN')";

    private final ExecutiveService executive;
    private final ExecutiveForecastService forecasts;
    private final FeatureGuard featureGuard;

    @GetMapping("")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<Overview>> overview() {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(executive.overview(tenant())));
    }

    @GetMapping("/history")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<SnapshotPoint>>> history(@RequestParam(defaultValue = "12") int months) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(executive.history(tenant(), months)));
    }

    /** Indicative straight-line trends per indicator from the stored snapshots. Not a prediction; see ForecastRules. */
    @GetMapping("/forecast")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<Forecast>>> forecast(@RequestParam(defaultValue = "12") int months) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(forecasts.forecast(tenant(), months)));
    }

    @PostMapping("/snapshots")
    @PreAuthorize(CAN_SNAPSHOT)
    public ResponseEntity<ApiResponse<SnapshotPoint>> snapshot() {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Snapshot taken", executive.takeSnapshot(tenant(), SnapshotSource.MANUAL, TenantContext.getCurrentUserName(), TenantContext.getCurrentUserId())));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
}
