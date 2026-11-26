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
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvProgrammeNeedsService;
import za.co.handyflow.platform.trainingprovider.dto.ProgrammeNeedsDtos.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training-provider")
@RequiredArgsConstructor
@Tag(name = "Training Provider - Programme resource needs", description = "Resources a programme version lists as needed, and how a cohort's site compares")
public class TrainProvProgrammeNeedsController {

    private static final String READ = "hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";

    private final TrainProvProgrammeNeedsService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/catalogue/programme-versions/{versionId}/resource-needs")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<VersionNeeds>> get(@PathVariable UUID versionId) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.get(TenantContext.getTenantIdAsObject(), versionId)));
    }

    @PostMapping("/catalogue/programme-versions/{versionId}/resource-needs")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<VersionNeeds>> add(@PathVariable UUID versionId, @Valid @RequestBody NeedRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Need added", service.add(TenantContext.getTenantIdAsObject(), versionId, r, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/catalogue/resource-needs/{needId}/withdraw")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<VersionNeeds>> withdraw(@PathVariable UUID needId, @Valid @RequestBody WithdrawRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Need withdrawn", service.withdraw(TenantContext.getTenantIdAsObject(), needId, r.reason(), TenantContext.getCurrentUserId())));
    }

    @GetMapping("/cohorts/{cohortId}/resource-readiness")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<CohortReadiness>> readiness(@PathVariable UUID cohortId) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.readiness(TenantContext.getTenantIdAsObject(), cohortId)));
    }
}
