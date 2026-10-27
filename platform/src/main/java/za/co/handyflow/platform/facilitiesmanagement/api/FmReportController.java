package za.co.handyflow.platform.facilitiesmanagement.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmProfitabilityService;
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmSlaReportService;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmClientProfitabilityResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmSlaPerformanceResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Management reports. Profitability exposes internal costs, so it needs an explicit authority
 * (COST_READ, BILLING_MANAGE, BILLING_APPROVE or ADMIN — plain READ is not enough). SLA performance is
 * operational and open to READ/MANAGE/ADMIN, SLA_MANAGE and CONTRACT_MANAGE.
 */
@RestController
@RequestMapping("/api/v1/facilitiesmanagement/reports")
@RequiredArgsConstructor
@Tag(name = "Facilities Management - Reports", description = "Profitability and SLA performance reports")
public class FmReportController {

    private final FmProfitabilityService profitabilityService;
    private final FmSlaReportService slaReportService;
    private final FeatureGuard featureGuard;

    @GetMapping("/client-profitability")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_ADMIN','FACILITIESMANAGEMENT_COST_READ','FACILITIESMANAGEMENT_BILLING_MANAGE','FACILITIESMANAGEMENT_BILLING_APPROVE')")
    @Operation(summary = "Revenue, internal cost, gross margin and unbilled work for one client over a period")
    public ResponseEntity<ApiResponse<FmClientProfitabilityResponse>> clientProfitability(
            @RequestParam UUID clientId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(
                profitabilityService.clientProfitability(TenantContext.getTenantIdAsObject(), clientId, from, to)));
    }

    @GetMapping("/sla-performance")
    @PreAuthorize("hasAnyAuthority('FACILITIESMANAGEMENT_READ','FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN','FACILITIESMANAGEMENT_SLA_MANAGE','FACILITIESMANAGEMENT_CONTRACT_MANAGE')")
    @Operation(summary = "Response and resolution SLA performance for one client over work orders created in a period")
    public ResponseEntity<ApiResponse<FmSlaPerformanceResponse>> slaPerformance(
            @RequestParam UUID clientId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(
                slaReportService.clientPerformance(TenantContext.getTenantIdAsObject(), clientId, from, to)));
    }
}