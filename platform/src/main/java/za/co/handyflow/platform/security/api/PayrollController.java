// security/api/PayrollController.java
package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.security.application.internal.SecurityGuardPayStatementPdfService;
import za.co.handyflow.platform.security.application.internal.SecurityPayrollService;
import za.co.handyflow.platform.security.domain.model.GradeRate;
import za.co.handyflow.platform.security.domain.model.PayrollLineItem;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * PayrollController — Phase 4 payroll export.
 *
 * CHANGE: added GET /periods/{id}/guards/{guardId}/pdf -- a per-guard gross
 * pay statement PDF (audit gap: "no payslip PDF for security payroll").
 * DELIBERATELY not called a payslip and does NOT reuse HR's
 * PayslipPdfGenerator -- see SecurityGuardPayStatementPdfService's class
 * javadoc for why that reuse isn't safe (this module has no PAYE/UIF/tax
 * computation, unlike HR's payslip model).
 *
 * All endpoints require USER_UPDATE — payroll is a financial operation that
 * should not be accessible to read-only supervisors.
 *
 * Export flow for a typical pay run:
 *   1. POST /payroll/periods — create a DRAFT period for the pay window
 *   2. POST /payroll/periods/{id}/approve — compute and freeze line items
 *   3. GET /payroll/periods/{id}/export/csv — download for Sage/VIP Payroll
 *      OR GET /payroll/periods/{id}/export/json — for BI tools / API clients
 *      OR GET /payroll/periods/{id}/guards/{guardId}/pdf — per-guard gross
 *      pay statement, for handing to the guard directly
 *   4. POST /payroll/periods/{id}/mark-paid — record payment confirmation
 */
@Tag(name = "Security - Payroll (Phase 4)")
@RestController
@RequestMapping("/api/v1/security/payroll")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('USER_UPDATE')")
public class PayrollController {

    private final SecurityPayrollService              payrollService;
    private final SecurityGuardPayStatementPdfService  payStatementPdfService;

    // ── Periods ────────────────────────────────────────────────────────────────

    @GetMapping("/periods")
    @Operation(summary = "List all payroll periods")
    public ResponseEntity<ApiResponse<Page<PayrollPeriodResponse>>> listPeriods(Pageable pageable) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(payrollService.listPeriods(tenantId, pageable)));
    }

    @GetMapping("/periods/{id}")
    @Operation(summary = "Get a single payroll period")
    public ResponseEntity<ApiResponse<PayrollPeriodResponse>> getPeriod(@PathVariable UUID id) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(payrollService.getPeriod(tenantId, id)));
    }

    @PostMapping("/periods")
    @Operation(
            summary = "Create a payroll period",
            description = "Starts as DRAFT. No line items are computed until the period is approved.")
    public ResponseEntity<ApiResponse<PayrollPeriodResponse>> createPeriod(
            @Valid @RequestBody CreatePayrollPeriodRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        UUID actorId = TenantContext.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(payrollService.createPeriod(tenantId, req, actorId)));
    }

    @PostMapping("/periods/{id}/approve")
    @Operation(
            summary = "Approve a payroll period — computes and freezes line items",
            description = "Finds all COMPLETED shifts in the period window, resolves each guard's " +
                    "rate (explicit override or grade-based), computes regular + overtime line " +
                    "items, and freezes totals. Throws if any guard has no rate configured.")
    public ResponseEntity<ApiResponse<PayrollPeriodResponse>> approvePeriod(@PathVariable UUID id) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        UUID actorId = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                payrollService.approvePeriod(tenantId, id, actorId)));
    }

    @PostMapping("/periods/{id}/mark-paid")
    @Operation(summary = "Mark an EXPORTED period as PAID")
    public ResponseEntity<ApiResponse<PayrollPeriodResponse>> markPaid(@PathVariable UUID id) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(payrollService.markPaid(tenantId, id)));
    }

    // ── Line items ─────────────────────────────────────────────────────────────

    @GetMapping("/periods/{id}/lines")
    @Operation(summary = "List all line items for a period")
    public ResponseEntity<ApiResponse<List<PayrollLineItem>>> getLineItems(@PathVariable UUID id) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(payrollService.getLineItems(tenantId, id)));
    }

    // ── Export ─────────────────────────────────────────────────────────────────

    @GetMapping("/periods/{id}/export/csv")
    @Operation(
            summary = "Export period as CSV (Sage/VIP Payroll compatible)",
            description = "One row per line item. Marks the period as EXPORTED. " +
                    "Includes guard name, grade, PSiRA number, shift date, hours, rate, and gross ZAR.")
    public ResponseEntity<byte[]> exportCsv(@PathVariable UUID id) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        byte[] csv = payrollService.exportCsv(tenantId, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"payroll-export-" + id + ".csv\"")
                .body(csv);
    }

    @GetMapping("/periods/{id}/export/json")
    @Operation(
            summary = "Export period as JSON (BI tools / public API clients)",
            description = "Returns a structured summary grouped by guard. Same data as CSV " +
                    "but in a format suitable for machine consumption. Marks the period EXPORTED.")
    public ResponseEntity<ApiResponse<PayrollExportJsonResponse>> exportJson(@PathVariable UUID id) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(payrollService.exportJson(tenantId, id)));
    }

    // ── Guard pay statement PDF ────────────────────────────────────────────────

    @GetMapping("/periods/{id}/guards/{guardId}/pdf")
    @Operation(
            summary = "Guard gross pay statement (PDF)",
            description = "Per-guard PDF for one payroll period — shift-by-shift regular/overtime " +
                    "hours, rate, and gross pay, branded with the tenant's logo/company name. " +
                    "NOT a statutory payslip: no PAYE/UIF/tax is computed by this module (that " +
                    "happens in your external payroll system after CSV export) — the PDF says so " +
                    "explicitly. Works on periods that have been approved (have line items), even " +
                    "if not yet exported/paid.")
    public ResponseEntity<byte[]> getGuardPayStatementPdf(
            @PathVariable UUID id, @PathVariable UUID guardId) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        byte[] pdf = payStatementPdfService.payStatementPdf(tenantId, id, guardId);
        return pdfResponse(pdf, "pay-statement-" + guardId.toString().substring(0, 8) + "-" + id + ".pdf");
    }

    // ── Grade rates ────────────────────────────────────────────────────────────

    @GetMapping("/grade-rates")
    @Operation(summary = "List all configured grade rates for this tenant")
    public ResponseEntity<ApiResponse<List<GradeRate>>> getGradeRates() {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(payrollService.getGradeRates(tenantId)));
    }

    @PostMapping("/grade-rates")
    @Operation(
            summary = "Set a default hourly rate for a PSiRA grade",
            description = "grade: A | B | C | D | E. hourlyRateCents: ZAR in cents (e.g. 3500 = R35.00). " +
                    "effectiveFrom: the date from which this rate applies (affects shifts on/after this date).")
    public ResponseEntity<ApiResponse<GradeRate>> setGradeRate(
            @Valid @RequestBody SetGradeRateRequest req) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        UUID actorId = TenantContext.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(payrollService.setGradeRate(tenantId, req, actorId)));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private ResponseEntity<byte[]> pdfResponse(byte[] pdf, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(pdf.length);
        return ResponseEntity.ok().headers(headers).body(pdf);
    }
}