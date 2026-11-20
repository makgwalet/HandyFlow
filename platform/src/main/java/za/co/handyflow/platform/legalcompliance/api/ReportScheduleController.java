package za.co.handyflow.platform.legalcompliance.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalcompliance.application.internal.ReportScheduleService;
import za.co.handyflow.platform.legalcompliance.dto.ReportScheduleDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/** Scheduled delivery of standard reports. Same permission as exporting a report. Providers never come through here. */
@RestController("legalComplianceReportScheduleController")
@RequestMapping("/api/v1/legalcompliance/report-schedules")
@RequiredArgsConstructor
@Tag(name = "Legal/Compliance - Report schedules", description = "Tell chosen users, on a schedule, that a report is ready")
public class ReportScheduleController {

    private static final String CAN = "hasAnyAuthority('LEGALCOMPLIANCE_REPORT_EXPORT','LEGALCOMPLIANCE_ADMIN')";

    private final ReportScheduleService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(CAN)
    public ResponseEntity<ApiResponse<List<ScheduleResponse>>> list() {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(service.list(tenant(), LCReportController.audience())));
    }

    @PostMapping
    @PreAuthorize(CAN)
    public ResponseEntity<ApiResponse<ScheduleResponse>> create(@Valid @RequestBody CreateRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Schedule created", service.create(tenant(), req, LCReportController.audience(), uid(), uname())));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN)
    public ResponseEntity<ApiResponse<ScheduleResponse>> update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest req) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success("Schedule updated", service.update(tenant(), id, req, LCReportController.audience(), uid(), uname())));
    }

    @PostMapping("/{id}/run-now")
    @PreAuthorize(CAN)
    public ResponseEntity<ApiResponse<ScheduleResponse>> runNow(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        return ResponseEntity.ok(ApiResponse.success(service.runNow(tenant(), id, LCReportController.audience(), uid(), uname())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(CAN)
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        featureGuard.requireModule("legalcompliance");
        service.delete(tenant(), id, LCReportController.audience(), uid(), uname());
        return ResponseEntity.ok(ApiResponse.success("Schedule deleted", null));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
}
