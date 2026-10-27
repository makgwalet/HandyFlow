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
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmReportingService;
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmSlaReportService;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmAssetReliabilityResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmClientProfitabilityResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmDashboardResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmSlaPerformanceResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmSlaPortfolioResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmVendorPerformanceResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Management reports. Profitability exposes internal costs, so it needs an explicit authority
 * (COST_READ, BILLING_MANAGE, BILLING_APPROVE or ADMIN — plain READ is not enough). SLA performance is
 * operational and open to READ/MANAGE/ADMIN, SLA_MANAGE and CONTRACT_MANAGE. The dashboard is also open to field
 * technicians (their own jobs only); vendor, asset and portfolio-SLA reports are management views. Internal cost and
 * finance figures inside them appear only for callers with a cost/billing authority — see {@code FmReportingService}.
 */
@RestController
@RequestMapping("/api/v1/facilitiesmanagement/reports")
@RequiredArgsConstructor
@Tag(name = "Facilities Management - Reports", description = "Dashboard, profitability, SLA, vendor and asset reports")
public class FmReportController {

    private final FmProfitabilityService profitabilityService;
    private final FmSlaReportService slaReportService;
    private final FmReportingService reportingService;
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

    private static final String CAN_VIEW_DASHBOARD = "hasAnyAuthority('FACILITIESMANAGEMENT_READ','FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN','FACILITIESMANAGEMENT_TECHNICIAN','FACILITIESMANAGEMENT_BILLING_MANAGE','FACILITIESMANAGEMENT_BILLING_APPROVE')";
    private static final String CAN_VIEW_REPORTS = "hasAnyAuthority('FACILITIESMANAGEMENT_READ','FACILITIESMANAGEMENT_MANAGE','FACILITIESMANAGEMENT_ADMIN','FACILITIESMANAGEMENT_SLA_MANAGE','FACILITIESMANAGEMENT_CONTRACT_MANAGE')";

    @GetMapping("/dashboard")
    @PreAuthorize(CAN_VIEW_DASHBOARD)
    @Operation(summary = "Live operational KPIs (and finance KPIs for callers with billing/cost authority)")
    public ResponseEntity<ApiResponse<FmDashboardResponse>> dashboard() {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(reportingService.dashboard(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/vendor-performance")
    @PreAuthorize(CAN_VIEW_REPORTS)
    @Operation(summary = "Subcontractor performance: completion time, rework, SLA compliance and cost, for work created in a period")
    public ResponseEntity<ApiResponse<FmVendorPerformanceResponse>> vendorPerformance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(
                reportingService.vendorPerformance(TenantContext.getTenantIdAsObject(), from, to)));
    }

    @GetMapping("/asset-reliability")
    @PreAuthorize(CAN_VIEW_REPORTS)
    @Operation(summary = "Worst assets by failures: MTBF, MTTR, downtime, repeat failures and top cause (limit 1-100, default 20)")
    public ResponseEntity<ApiResponse<FmAssetReliabilityResponse>> assetReliability(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer limit) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(
                reportingService.assetReliability(TenantContext.getTenantIdAsObject(), from, to, limit)));
    }

    @GetMapping("/sla-portfolio")
    @PreAuthorize(CAN_VIEW_REPORTS)
    @Operation(summary = "SLA compliance across every client the caller may see, worst first")
    public ResponseEntity<ApiResponse<FmSlaPortfolioResponse>> slaPortfolio(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("facilitiesmanagement");
        return ResponseEntity.ok(ApiResponse.success(
                reportingService.slaPortfolio(TenantContext.getTenantIdAsObject(), from, to)));
    }
}
