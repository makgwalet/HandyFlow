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
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvRplService;
import za.co.handyflow.platform.trainingprovider.dto.RplDtos.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training-provider")
@RequiredArgsConstructor
@Tag(name = "Training Provider - RPL", description = "Recognition of prior learning applications, evidence and decisions")
public class TrainProvRplController {

    private static final String READ = "hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final TrainProvRplService service;
    private final FeatureGuard featureGuard;

    private static LocalDate today() {
        return LocalDate.now(SAST);
    }

    @GetMapping("/rpl-applications")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<ApplicationView>>> list(@RequestParam(required = false) UUID enrolmentId, @RequestParam(required = false) String status) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), enrolmentId, status)));
    }

    @GetMapping("/rpl-applications/{id}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<ApplicationDetail>> get(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/rpl-applications")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ApplicationView>> create(@Valid @RequestBody ApplicationRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("RPL application added", service.create(TenantContext.getTenantIdAsObject(), r, TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/rpl-applications/{id}/evidence")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<EvidenceView>> addEvidence(@PathVariable UUID id, @Valid @RequestBody EvidenceRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Evidence added", service.addEvidence(TenantContext.getTenantIdAsObject(), id, r, TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/rpl-evidence/{id}/review")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<EvidenceView>> review(@PathVariable UUID id, @Valid @RequestBody ReviewRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Evidence reviewed", service.review(TenantContext.getTenantIdAsObject(), id, r, TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/rpl-evidence/{id}/withdraw")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<EvidenceView>> withdrawEvidence(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Evidence withdrawn", service.withdrawEvidence(TenantContext.getTenantIdAsObject(), id, r.reason(), TenantContext.getCurrentUserId())));
    }

    @GetMapping("/rpl-applications/{id}/assessor-snapshot")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<za.co.handyflow.platform.trainingprovider.dto.AttemptDtos.AppointmentSnapshotView>>> assessorSnapshot(@PathVariable UUID id) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.assessorSnapshot(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/rpl-applications/{id}/decide")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ApplicationView>> decide(@PathVariable UUID id, @Valid @RequestBody DecisionRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Decision recorded", service.decide(TenantContext.getTenantIdAsObject(), id, r, TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/rpl-applications/{id}/confirm")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ApplicationView>> confirm(@PathVariable UUID id, @Valid @RequestBody ConfirmRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Decision confirmed", service.confirm(TenantContext.getTenantIdAsObject(), id, r.confirmedOn(), TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/rpl-applications/{id}/withdraw")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ApplicationView>> withdraw(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Application withdrawn", service.withdraw(TenantContext.getTenantIdAsObject(), id, r.reason(), TenantContext.getCurrentUserId())));
    }
}
