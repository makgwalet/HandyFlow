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
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvCompletionService;
import za.co.handyflow.platform.trainingprovider.dto.CompletionDtos.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training-provider")
@RequiredArgsConstructor
@Tag(name = "Training Provider - Completion", description = "Readiness and completion records")
public class TrainProvCompletionController {

    private static final String READ = "hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final TrainProvCompletionService service;
    private final FeatureGuard featureGuard;

    @GetMapping("/learner-enrolments/{enrolmentId}/readiness")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<Readiness>> readiness(@PathVariable UUID enrolmentId) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.readiness(TenantContext.getTenantIdAsObject(), enrolmentId)));
    }

    @GetMapping("/completions")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<CompletionView>>> list(@RequestParam(required = false) UUID enrolmentId, @RequestParam(required = false) String outcome,
                                                                  @RequestParam(required = false) String status) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), enrolmentId, outcome, status)));
    }

    @PostMapping("/completions")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<CompletionView>> record(@Valid @RequestBody RecordCompletion r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Outcome recorded",
                service.record(TenantContext.getTenantIdAsObject(), r, TenantContext.getCurrentUserId(), LocalDate.now(SAST))));
    }

    @PostMapping("/completions/{id}/withdraw")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<CompletionView>> withdraw(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Outcome withdrawn", service.withdraw(TenantContext.getTenantIdAsObject(), id, r.reason(), TenantContext.getCurrentUserId())));
    }
}
