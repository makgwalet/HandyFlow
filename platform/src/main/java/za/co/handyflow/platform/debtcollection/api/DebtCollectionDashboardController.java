package za.co.handyflow.platform.debtcollection.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.debtcollection.application.internal.CsvSupport;
import za.co.handyflow.platform.debtcollection.application.internal.DebtCollectionDashboardService;
import za.co.handyflow.platform.debtcollection.application.internal.DebtCollectionDashboardService.AgingReport;
import za.co.handyflow.platform.debtcollection.application.internal.DebtCollectionDashboardService.CollectorRow;
import za.co.handyflow.platform.debtcollection.application.internal.DebtCollectionDashboardService.Summary;
import za.co.handyflow.platform.debtcollection.application.internal.DebtCollectionDashboardService.TopDebtor;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;

/**
 * Collections dashboard and reports. Summary/aging/top-debtors are open to anyone with module access;
 * per-collector performance is supervisor-only (DEBTCOLLECTION_ADMIN) because it evaluates individual staff.
 * Date params are ISO (yyyy-MM-dd); the default period is month-to-date.
 */
@RestController
@RequestMapping("/api/v1/debtcollection/dashboard")
@RequiredArgsConstructor
@Tag(name = "Debt Collection - Dashboard", description = "KPIs, aging, top debtors and collector performance")
public class DebtCollectionDashboardController {

    private final DebtCollectionDashboardService dashboardService;
    private final FeatureGuard featureGuard;

    @GetMapping("/summary")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Headline KPIs for a period (default month-to-date) with previous-period comparison")
    public ResponseEntity<ApiResponse<Summary>> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.summary(TenantContext.getTenantIdAsObject(), from, to)));
    }

    @GetMapping("/aging")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Aging of outstanding invoices linked to active cases")
    public ResponseEntity<ApiResponse<AgingReport>> aging() {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success(dashboardService.aging(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/aging/export.csv")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<byte[]> agingCsv() {
        featureGuard.requireModule("debtcollection");
        AgingReport r = dashboardService.aging(TenantContext.getTenantIdAsObject());
        CsvSupport csv = new CsvSupport().row("Age bucket (days overdue)", "Amount", "Invoices", "Cases", "Share");
        r.buckets().forEach(b -> csv.row(b.label(), b.amount(), b.invoiceCount(), b.caseCount(), b.share()));
        csv.row("Total", r.totalOutstanding(), "", "", "");
        return csv(csv, "debt-collection-aging-" + r.asOf() + ".csv");
    }

    @GetMapping("/top-debtors")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<List<TopDebtor>>> topDebtors(@RequestParam(defaultValue = "10") int limit) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.topDebtors(TenantContext.getTenantIdAsObject(), limit)));
    }

    @GetMapping("/collectors")
    @PreAuthorize("hasAuthority('DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Collector performance (supervisor): workload, contacts and PTP outcomes for the period")
    public ResponseEntity<ApiResponse<List<CollectorRow>>> collectors(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.collectors(TenantContext.getTenantIdAsObject(), from, to)));
    }

    @GetMapping("/collectors/export.csv")
    @PreAuthorize("hasAuthority('DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<byte[]> collectorsCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("debtcollection");
        CsvSupport csv = new CsvSupport().row("Collector", "Open cases", "Outstanding", "Open work items",
                "Overdue work items", "Contacts in period", "PTP kept", "PTP partial", "PTP broken",
                "PTP promised value", "PTP received value", "PTP kept rate");
        dashboardService.collectors(TenantContext.getTenantIdAsObject(), from, to).forEach(c -> csv.row(
                c.userName(), c.openCases(), c.outstandingValue(), c.openWorkItems(), c.overdueWorkItems(),
                c.contactsInPeriod(), c.ptpKept(), c.ptpPartial(), c.ptpBroken(), c.ptpPromisedValue(),
                c.ptpReceivedValue(), c.ptpKeptRate()));
        return csv(csv, "debt-collection-collector-performance-" + LocalDate.now() + ".csv");
    }

    private ResponseEntity<byte[]> csv(CsvSupport csv, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv.toBytes());
    }
}
