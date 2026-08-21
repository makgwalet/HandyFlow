package za.co.handyflow.platform.crm.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.crm.application.internal.CrmReportingService;
import za.co.handyflow.platform.crm.dto.FunnelReportResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

/**
 * FIX: backlog 4.3 — "no conversion-rate/funnel reporting." Its own
 * dedicated controller, not folded into CustomerController — same
 * separation-of-concerns precedent PopiaExportController already
 * established for a distinct, non-CRUD CRM capability.
 */
@RestController
@RequestMapping("/api/v1/crm/reports")
@RequiredArgsConstructor
@Tag(name = "CRM Reporting", description = "Lead funnel and conversion-rate reporting")
public class CrmReportingController {

    private final CrmReportingService reportingService;

    @GetMapping("/funnel")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "Lead funnel report — stage reach counts, stage-to-stage conversion rates, and average time-in-stage")
    public ResponseEntity<ApiResponse<FunnelReportResponse>> getFunnelReport() {
        return ResponseEntity.ok(ApiResponse.success(
                reportingService.getFunnelReport(TenantContext.getTenantIdAsObject())));
    }
}