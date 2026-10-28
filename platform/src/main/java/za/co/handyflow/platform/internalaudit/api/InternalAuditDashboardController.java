package za.co.handyflow.platform.internalaudit.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.internalaudit.application.internal.InternalAuditDashboardBuilder.Dashboard;
import za.co.handyflow.platform.internalaudit.application.internal.AuditActivityService;
import za.co.handyflow.platform.internalaudit.application.internal.DashboardTrends.Trends;
import za.co.handyflow.platform.internalaudit.application.internal.InternalAuditDashboardService;
import za.co.handyflow.platform.internalaudit.application.internal.InternalAuditTrendService;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/internal-audit/dashboard")
@RequiredArgsConstructor
@Tag(name = "Internal Audit Dashboard", description = "Plan progress, coverage, findings by severity, overdue actions, repeat findings")
public class InternalAuditDashboardController {
    private final InternalAuditDashboardService service;
    private final FeatureGuard featureGuard;
    private final InternalAuditTrendService trendService;
    private final AuditActivityService activityService;

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    @Operation(summary = "Internal audit dashboard figures")
    public ResponseEntity<ApiResponse<Dashboard>> get() {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(service.build(TenantContext.getTenantIdAsObject(), LocalDate.now())));
    }

    @GetMapping("/trends")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    @Operation(summary = "How the headline figures have moved since a saved day about a quarter ago (and saves today's figures)")
    public ResponseEntity<ApiResponse<Trends>> trends() {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(trendService.trends(TenantContext.getTenantIdAsObject(), LocalDate.now(java.time.ZoneId.of("Africa/Johannesburg")))));
    }

    @GetMapping("/activity")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    @Operation(summary = "The newest internal audit actions, from the audit trail; at most 100")
    public ResponseEntity<ApiResponse<List<AuditActivityService.Item>>> activity(@RequestParam(defaultValue = "8") int limit) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(activityService.recent(TenantContext.getTenantIdAsObject(), limit)));
    }
}
