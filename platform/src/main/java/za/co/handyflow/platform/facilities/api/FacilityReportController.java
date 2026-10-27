package za.co.handyflow.platform.facilities.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.facilities.application.internal.FacilityExportService;
import za.co.handyflow.platform.facilities.application.internal.FacilityReportService;
import za.co.handyflow.platform.facilities.application.internal.FacilityStockValuationService;
import za.co.handyflow.platform.facilities.dto.StockValuationResponse;
import za.co.handyflow.platform.facilities.dto.FacilityAssetReliabilityResponse;
import za.co.handyflow.platform.facilities.dto.FacilityDashboardResponse;
import za.co.handyflow.platform.facilities.dto.FacilityTrendResponse;
import za.co.handyflow.platform.facilities.dto.FacilityVendorPerformanceResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Management reporting for the in-house facilities function. The dashboard is open to anyone who can see work orders
 * (figures are limited to what the caller may see: a technician's own jobs, a requester's own requests); the contractor
 * and asset reports are planner/manager views.
 */
@RestController
@RequestMapping("/api/v1/facilities/reports")
@RequiredArgsConstructor
@Tag(name = "Facilities - Reports", description = "Dashboard, contractor performance and asset reliability")
public class FacilityReportController {

    private static final String CAN_VIEW_DASHBOARD = "hasAnyAuthority('FACILITIES_READ','FACILITIES_MANAGE','FACILITIES_ADMIN'," +
            "'FACILITIES_TECHNICIAN','FACILITIES_WORKORDER_REQUEST','FACILITIES_WORKORDER_CREATE'," +
            "'FACILITIES_WORKORDER_ASSIGN','FACILITIES_WORKORDER_APPROVE','FACILITIES_WORKORDER_VERIFY')";
    private static final String CAN_VIEW_REPORTS = "hasAnyAuthority('FACILITIES_READ','FACILITIES_MANAGE','FACILITIES_ADMIN')";

    private final FacilityReportService reportService;
    private final FacilityExportService exportService;
    private final FacilityStockValuationService valuationService;
    private final FeatureGuard featureGuard;

    @GetMapping("/dashboard")
    @PreAuthorize(CAN_VIEW_DASHBOARD)
    @Operation(summary = "Live KPIs: open work, overdue, approvals, 30-day throughput, PPM and certificate status")
    public ResponseEntity<ApiResponse<FacilityDashboardResponse>> dashboard() {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(reportService.dashboard(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/vendor-performance")
    @PreAuthorize(CAN_VIEW_REPORTS)
    @Operation(summary = "Contractor performance: completion time, rework and cost, for work created in a period")
    public ResponseEntity<ApiResponse<FacilityVendorPerformanceResponse>> vendorPerformance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(
                reportService.vendorPerformance(TenantContext.getTenantIdAsObject(), from, to)));
    }

    @GetMapping("/asset-reliability")
    @PreAuthorize(CAN_VIEW_REPORTS)
    @Operation(summary = "Worst assets by failures: MTBF, MTTR, downtime, repeat failures and top cause (limit 1-100, default 20)")
    public ResponseEntity<ApiResponse<FacilityAssetReliabilityResponse>> assetReliability(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer limit) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(
                reportService.assetReliability(TenantContext.getTenantIdAsObject(), from, to, limit)));
    }

    @GetMapping("/trends")
    @PreAuthorize(CAN_VIEW_REPORTS)
    @Operation(summary = "Work raised and completed per WEEK (default) or MONTH over a period: volume, completion time, rework, downtime, cost")
    public ResponseEntity<ApiResponse<FacilityTrendResponse>> trends(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String bucket) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(
                reportService.trends(TenantContext.getTenantIdAsObject(), from, to, bucket)));
    }

    @GetMapping("/stock-valuation")
    @PreAuthorize(CAN_VIEW_REPORTS)
    @Operation(summary = "Stock on hand valued at average cost, per store and in total (as at now). Optionally one store")
    public ResponseEntity<ApiResponse<StockValuationResponse>> stockValuation(@RequestParam(required = false) UUID storeId) {
        featureGuard.requireModule("facilities");
        return ResponseEntity.ok(ApiResponse.success(valuationService.valuation(TenantContext.getTenantIdAsObject(), storeId)));
    }

    // ── CSV exports ──────────────────────────────────────────────────────────

    @GetMapping(value = "/export/work-orders.csv", produces = "text/csv")
    @PreAuthorize(CAN_VIEW_REPORTS)
    @Operation(summary = "Work orders created in the period, as a spreadsheet (at most 5000 rows)")
    public ResponseEntity<byte[]> exportWorkOrders(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("facilities");
        return csv(exportService.workOrders(TenantContext.getTenantIdAsObject(), from, to));
    }

    @GetMapping(value = "/export/vendor-performance.csv", produces = "text/csv")
    @PreAuthorize(CAN_VIEW_REPORTS)
    public ResponseEntity<byte[]> exportVendorPerformance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("facilities");
        return csv(exportService.vendorPerformance(TenantContext.getTenantIdAsObject(), from, to));
    }

    @GetMapping(value = "/export/asset-reliability.csv", produces = "text/csv")
    @PreAuthorize(CAN_VIEW_REPORTS)
    public ResponseEntity<byte[]> exportAssetReliability(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("facilities");
        return csv(exportService.assetReliability(TenantContext.getTenantIdAsObject(), from, to));
    }

    @GetMapping(value = "/export/certificates.csv", produces = "text/csv")
    @PreAuthorize(CAN_VIEW_REPORTS)
    @Operation(summary = "Compliance certificate register with days to expiry")
    public ResponseEntity<byte[]> exportCertificates() {
        featureGuard.requireModule("facilities");
        return csv(exportService.certificates(TenantContext.getTenantIdAsObject()));
    }

    @GetMapping(value = "/export/corrective-actions.csv", produces = "text/csv")
    @PreAuthorize(CAN_VIEW_REPORTS)
    public ResponseEntity<byte[]> exportCorrectiveActions(@RequestParam(required = false) String status) {
        featureGuard.requireModule("facilities");
        return csv(exportService.correctiveActions(TenantContext.getTenantIdAsObject(), status));
    }

    @GetMapping(value = "/export/stock-valuation.csv", produces = "text/csv")
    @PreAuthorize(CAN_VIEW_REPORTS)
    public ResponseEntity<byte[]> exportStockValuation(@RequestParam(required = false) UUID storeId) {
        featureGuard.requireModule("facilities");
        return csv(exportService.stockValuation(TenantContext.getTenantIdAsObject(), storeId));
    }

    @GetMapping(value = "/export/technician-licences.csv", produces = "text/csv")
    @PreAuthorize(CAN_VIEW_REPORTS)
    public ResponseEntity<byte[]> exportTechnicianLicences() {
        featureGuard.requireModule("facilities");
        return csv(exportService.technicianLicences(TenantContext.getTenantIdAsObject()));
    }

    private static ResponseEntity<byte[]> csv(FacilityExportService.Export export) {
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv; charset=UTF-8")
                .header("Content-Disposition", "attachment; filename=\"" + export.filename() + "\"")
                .body(export.csv().getBytes(StandardCharsets.UTF_8));
    }
}
