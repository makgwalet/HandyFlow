package za.co.handyflow.platform.trainingprovider.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvProgrammeHoursService;
import za.co.handyflow.platform.trainingprovider.dto.ProgrammeHoursDtos.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training-provider/catalogue/programme-versions/{versionId}/workplace-hours")
@RequiredArgsConstructor
@Tag(name = "Training Provider - Programme workplace hours", description = "Optional workplace hours on a programme version, with their source")
public class TrainProvProgrammeHoursController {

    private static final String READ = "hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final TrainProvProgrammeHoursService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<VersionHours>> get(@PathVariable UUID versionId) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.get(TenantContext.getTenantIdAsObject(), versionId)));
    }

    @PutMapping
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<VersionHours>> record(@PathVariable UUID versionId, @Valid @RequestBody HoursRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Workplace hours recorded", service.record(TenantContext.getTenantIdAsObject(), versionId, r, TenantContext.getCurrentUserId(), LocalDate.now(SAST))));
    }

    @PostMapping("/withdraw")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<VersionHours>> withdraw(@PathVariable UUID versionId, @Valid @RequestBody WithdrawRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Workplace hours withdrawn", service.withdraw(TenantContext.getTenantIdAsObject(), versionId, r.reason(), TenantContext.getCurrentUserId())));
    }
}
