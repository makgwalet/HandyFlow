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
import za.co.handyflow.platform.internalaudit.application.internal.AuditRemediationService;
import za.co.handyflow.platform.internalaudit.application.internal.RemediationBoard.Board;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/v1/internal-audit/remediation")
@RequiredArgsConstructor
@Tag(name = "Internal Audit - Findings and Remediation", description = "Findings across all engagements, with how far management has got in putting them right")
public class AuditRemediationController {
    private final AuditRemediationService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    @Operation(summary = "Every finding and every action plan, worst first, with totals; limited to one engagement when engagementId is given")
    public ResponseEntity<ApiResponse<Board>> board(@RequestParam(required = false) java.util.UUID engagementId) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(service.board(TenantContext.getTenantIdAsObject(), LocalDate.now(ZoneId.of("Africa/Johannesburg")), engagementId)));
    }
}
