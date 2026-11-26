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
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvAttemptService;
import za.co.handyflow.platform.trainingprovider.dto.AttemptDtos.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training-provider/assessment-attempts")
@RequiredArgsConstructor
@Tag(name = "Training Provider - Assessment attempts", description = "Attempts, marks and competency decisions")
public class TrainProvAttemptController {

    private static final String READ = "hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final TrainProvAttemptService service;
    private final FeatureGuard featureGuard;

    private static LocalDate today() {
        return LocalDate.now(SAST);
    }

    @GetMapping
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<AttemptView>>> list(@RequestParam(required = false) UUID cohortId, @RequestParam(required = false) UUID instrumentId,
                                                               @RequestParam(required = false) UUID enrolmentId) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), cohortId, instrumentId, enrolmentId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<AttemptView>> get(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/{id}/assessor-snapshots")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<AppointmentSnapshotView>>> assessorSnapshots(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.assessorSnapshots(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/by-assessor/{practitionerId}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<AttemptView>>> byAssessor(@PathVariable UUID practitionerId) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.forAssessor(TenantContext.getTenantIdAsObject(), practitionerId)));
    }

    @PostMapping
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AttemptView>> record(@Valid @RequestBody RecordAttempt r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Attempt recorded", service.record(TenantContext.getTenantIdAsObject(), r, TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/{id}/correct-mark")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AttemptView>> correct(@PathVariable UUID id, @Valid @RequestBody CorrectMark r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Mark corrected", service.correctMark(TenantContext.getTenantIdAsObject(), id, r, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/decision")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AttemptView>> decide(@PathVariable UUID id, @Valid @RequestBody DecisionRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Decision recorded", service.decide(TenantContext.getTenantIdAsObject(), id, r, TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<AttemptView>> voidIt(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Attempt voided", service.voidAttempt(TenantContext.getTenantIdAsObject(), id, r.reason(), TenantContext.getCurrentUserId())));
    }
}
