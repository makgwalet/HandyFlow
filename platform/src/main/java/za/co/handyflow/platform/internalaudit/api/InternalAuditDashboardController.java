package za.co.handyflow.platform.internalaudit.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.internalaudit.application.internal.InternalAuditDashboardBuilder.Dashboard;
import za.co.handyflow.platform.internalaudit.application.internal.InternalAuditDashboardService;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/internal-audit/dashboard")
@RequiredArgsConstructor
@Tag(name = "Internal Audit Dashboard", description = "Plan progress, coverage, findings by severity, overdue actions, repeat findings")
public class InternalAuditDashboardController {
    private final InternalAuditDashboardService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    @Operation(summary = "Internal audit dashboard figures")
    public ResponseEntity<ApiResponse<Dashboard>> get() {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(service.build(TenantContext.getTenantIdAsObject(), LocalDate.now())));
    }
}
