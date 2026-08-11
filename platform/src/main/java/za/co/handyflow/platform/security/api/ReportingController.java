// security/api/ReportingController.java

package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.security.application.internal.PdfReportService;
import za.co.handyflow.platform.security.application.internal.ReportingService;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.time.YearMonth;
import java.util.UUID;

/**
 * ReportingController — three monthly security reports, each available as
 * JSON (for the frontend dashboard) and PDF (for download/email to clients).
 *
 * CHANGE (V212): the three *Pdf() call sites now pass tenantId through to
 * PdfReportService, which uses it to fetch TenantDetails and brand the PDF
 * with the tenant's logo/company name instead of a hardcoded "HandyFlow
 * Security" header. No endpoint signatures changed — tenantId was already
 * being resolved in every method here, it just wasn't being forwarded.
 *
 * month parameter format: YYYY-MM (e.g. 2026-06)
 */
@Tag(name = "Security - Reports")
@RestController
@RequestMapping("/api/v1/security/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('USER_UPDATE')")
public class ReportingController {

    private final ReportingService  reportingService;
    private final PdfReportService  pdfReportService;

    // ── 1. Site Coverage ───────────────────────────────────────────────────────

    @GetMapping("/site-coverage")
    @Operation(
            summary = "Site coverage report (JSON)",
            description = "Shifts scheduled vs completed, patrol rounds, checkpoint scans, " +
                    "and incidents for a site over a calendar month. " +
                    "month format: YYYY-MM")
    public ResponseEntity<ApiResponse<SiteCoverageReport>> getSiteCoverage(
            @RequestParam UUID siteId,
            @RequestParam String month) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                reportingService.getSiteCoverageReport(tenantId, siteId, parseMonth(month))));
    }

    @GetMapping("/site-coverage/pdf")
    @Operation(
            summary = "Site coverage report (PDF download)",
            description = "Same data as /site-coverage but rendered as a branded PDF " +
                    "(tenant logo + company name). Suitable for emailing to clients as a " +
                    "monthly SLA report.")
    public ResponseEntity<byte[]> getSiteCoveragePdf(
            @RequestParam UUID siteId,
            @RequestParam String month) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        SiteCoverageReport report = reportingService.getSiteCoverageReport(
                tenantId, siteId, parseMonth(month));
        byte[] pdf = pdfReportService.siteCoveragePdf(report, tenantId);
        return pdfResponse(pdf,
                "site-coverage-" + report.siteName().replaceAll("[^a-zA-Z0-9]", "-")
                        + "-" + month + ".pdf");
    }

    // ── 2. Guard Attendance ────────────────────────────────────────────────────

    @GetMapping("/guard-attendance")
    @Operation(
            summary = "Guard attendance report (JSON)",
            description = "Shifts attended vs missed, hours worked, checkpoint scans, " +
                    "and incidents logged for a guard over a calendar month.")
    public ResponseEntity<ApiResponse<GuardAttendanceReport>> getGuardAttendance(
            @RequestParam UUID guardId,
            @RequestParam String month) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                reportingService.getGuardAttendanceReport(tenantId, guardId, parseMonth(month))));
    }

    @GetMapping("/guard-attendance/pdf")
    @Operation(
            summary = "Guard attendance report (PDF download)",
            description = "PDF version of the guard attendance report, branded with the " +
                    "tenant's logo/company name. Used for HR reviews and as an input to the " +
                    "payroll export (Phase 4).")
    public ResponseEntity<byte[]> getGuardAttendancePdf(
            @RequestParam UUID guardId,
            @RequestParam String month) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        GuardAttendanceReport report = reportingService.getGuardAttendanceReport(
                tenantId, guardId, parseMonth(month));
        byte[] pdf = pdfReportService.guardAttendancePdf(report, tenantId);
        return pdfResponse(pdf,
                "guard-attendance-" + report.guardName().replaceAll("[^a-zA-Z0-9]", "-")
                        + "-" + month + ".pdf");
    }

    // ── 3. Monthly Summary ─────────────────────────────────────────────────────

    @GetMapping("/monthly-summary")
    @Operation(
            summary = "Company-wide monthly summary (JSON)",
            description = "Tenant-wide rollup: total shifts/hours, all-site coverage rates, " +
                    "incident heat map by severity, active guard count. The executive view.")
    public ResponseEntity<ApiResponse<MonthlySummaryReport>> getMonthlySummary(
            @RequestParam String month) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                reportingService.getMonthlySummaryReport(tenantId, parseMonth(month))));
    }

    @GetMapping("/monthly-summary/pdf")
    @Operation(
            summary = "Company-wide monthly summary (PDF download)",
            description = "PDF version of the monthly summary, branded with the tenant's " +
                    "logo/company name. Suitable for board/management reporting.")
    public ResponseEntity<byte[]> getMonthlySummaryPdf(@RequestParam String month) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        MonthlySummaryReport report = reportingService.getMonthlySummaryReport(
                tenantId, parseMonth(month));
        byte[] pdf = pdfReportService.monthlySummaryPdf(report, tenantId);
        return pdfResponse(pdf, "monthly-summary-" + month + ".pdf");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (Exception e) {
            throw new za.co.handyflow.platform.shared.HandyFlowException(
                    "Invalid month format '" + month + "' — expected YYYY-MM (e.g. 2026-06)",
                    org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_MONTH");
        }
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] pdf, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(filename).build());
        headers.setContentLength(pdf.length);
        return ResponseEntity.ok().headers(headers).body(pdf);
    }
}