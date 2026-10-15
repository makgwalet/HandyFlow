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
import za.co.handyflow.platform.internalaudit.application.internal.AuditCommitteeReportBuilder.Report;
import za.co.handyflow.platform.internalaudit.application.internal.AuditCommitteeReportService;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;

/** Read-only summary for the audit committee. AUDIT_COMMITTEE_READ alone is enough, so a committee member needs no other audit access. */
@RestController
@RequestMapping("/api/v1/internal-audit/committee-report")
@RequiredArgsConstructor
@Tag(name = "Audit Committee Report", description = "Plan delivery, approved reports, overdue high-risk actions, risk and control position")
public class AuditCommitteeReportController {
    private final AuditCommitteeReportService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('AUDIT_COMMITTEE_READ','AUDIT_READ')")
    @Operation(summary = "Audit committee report as of today")
    public ResponseEntity<ApiResponse<Report>> get() {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(service.build(TenantContext.getTenantIdAsObject(), LocalDate.now())));
    }
}
