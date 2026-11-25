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
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvModerationService;
import za.co.handyflow.platform.trainingprovider.dto.ModerationDtos.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training-provider")
@RequiredArgsConstructor
@Tag(name = "Training Provider - Moderation", description = "Moderation of assessment decisions")
public class TrainProvModerationController {

    private static final String READ = "hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final TrainProvModerationService service;
    private final FeatureGuard featureGuard;

    private static LocalDate today() {
        return LocalDate.now(SAST);
    }

    @GetMapping("/assessment-moderations/pending")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<PendingRow>>> pending() {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.pending(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/assessment-moderations")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<ModerationView>>> list(@RequestParam(required = false) String outcome, @RequestParam(defaultValue = "false") boolean openQueries) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), outcome, openQueries)));
    }

    @GetMapping("/assessment-attempts/{attemptId}/moderation")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<ModerationView>> forAttempt(@PathVariable UUID attemptId) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.forAttempt(TenantContext.getTenantIdAsObject(), attemptId).orElse(null)));
    }

    @PostMapping("/assessment-attempts/{attemptId}/moderation")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ModerationView>> record(@PathVariable UUID attemptId, @Valid @RequestBody RecordModeration r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Moderation recorded", service.record(TenantContext.getTenantIdAsObject(), attemptId, r, TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/assessment-moderations/{id}/resolve")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ModerationView>> resolve(@PathVariable UUID id, @Valid @RequestBody ResolveRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Query resolved", service.resolve(TenantContext.getTenantIdAsObject(), id, r.note(), TenantContext.getCurrentUserId())));
    }
}
