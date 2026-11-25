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
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvReportService;
import za.co.handyflow.platform.trainingprovider.dto.ReportDtos.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/training-provider")
@RequiredArgsConstructor
@Tag(name = "Training Provider - Regulatory reporting", description = "Report obligations and reporting periods set up by the provider")
public class TrainProvReportController {

    private static final String READ = "hasAnyAuthority('TRAININGPROVIDER_READ','TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final String MANAGE = "hasAnyAuthority('TRAININGPROVIDER_MANAGE','TRAININGPROVIDER_ADMIN')";
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final TrainProvReportService service;
    private final FeatureGuard featureGuard;

    private static LocalDate today() {
        return LocalDate.now(SAST);
    }

    @GetMapping("/report-obligations")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<ObligationView>>> obligations(@RequestParam(required = false) UUID authorityId, @RequestParam(required = false) String status) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.listObligations(TenantContext.getTenantIdAsObject(), authorityId, status)));
    }

    @PostMapping("/report-obligations")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ObligationView>> createObligation(@Valid @RequestBody ObligationRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Obligation added", service.createObligation(TenantContext.getTenantIdAsObject(), r, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/report-obligations/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ObligationView>> updateObligation(@PathVariable UUID id, @Valid @RequestBody ObligationUpdate r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Obligation updated", service.updateObligation(TenantContext.getTenantIdAsObject(), id, r)));
    }

    @PostMapping("/report-obligations/{id}/retire")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<ObligationView>> retireObligation(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Obligation retired", service.retireObligation(TenantContext.getTenantIdAsObject(), id, r.reason(), TenantContext.getCurrentUserId())));
    }

    @GetMapping("/report-periods")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<PeriodView>>> periods(@RequestParam(required = false) UUID obligationId, @RequestParam(required = false) String status,
                                                                 @RequestParam(required = false) String state, @RequestParam(required = false) Integer soonDays) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success(service.listPeriods(TenantContext.getTenantIdAsObject(), obligationId, status, state, today(), soonDays)));
    }

    @PostMapping("/report-periods")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<PeriodView>> createPeriod(@Valid @RequestBody PeriodRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Period added", service.createPeriod(TenantContext.getTenantIdAsObject(), r, TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/report-periods/{id}/submit")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<PeriodView>> submit(@PathVariable UUID id, @Valid @RequestBody SubmitRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Recorded as submitted", service.submit(TenantContext.getTenantIdAsObject(), id, r, TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/report-periods/{id}/acknowledge")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<PeriodView>> acknowledge(@PathVariable UUID id, @Valid @RequestBody AcknowledgeRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Acknowledgement recorded", service.acknowledge(TenantContext.getTenantIdAsObject(), id, r, today())));
    }

    @PostMapping("/report-periods/{id}/waive")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<PeriodView>> waive(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Period waived", service.waive(TenantContext.getTenantIdAsObject(), id, r.reason(), TenantContext.getCurrentUserId(), today())));
    }

    @PostMapping("/report-periods/{id}/void")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<PeriodView>> voidPeriod(@PathVariable UUID id, @Valid @RequestBody ReasonRequest r) {
        featureGuard.requireModule("trainingprovider");
        return ResponseEntity.ok(ApiResponse.success("Period voided", service.voidPeriod(TenantContext.getTenantIdAsObject(), id, r.reason(), TenantContext.getCurrentUserId(), today())));
    }
}
