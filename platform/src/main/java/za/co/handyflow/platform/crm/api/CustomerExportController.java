package za.co.handyflow.platform.crm.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.crm.application.internal.CustomerExportService;
import za.co.handyflow.platform.shared.TenantContext;

import java.io.IOException;
import java.time.LocalDate;

/**
 * CustomerExportController — CSV download endpoints.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * WHY a separate controller instead of adding to CustomerController?
 *
 * Export endpoints are fundamentally different from CRUD:
 *   - They return a file (text/csv), not JSON (application/json)
 *   - They write directly to HttpServletResponse instead of returning
 *     a ResponseEntity body
 *   - They don't use our standard ApiResponse wrapper
 *   - They're long-running operations (streaming)
 *
 * Mixing streaming file downloads with JSON REST endpoints in one
 * controller class creates confusion.  Separate controller = separate
 * responsibility = easier to find and reason about.
 *
 * WHY void return type and direct response writing?
 * Spring MVC's message converters don't know how to turn a streaming
 * Writer into a downloadable CSV file — they'd try to serialize it as
 * JSON.  Writing directly to response.getWriter() bypasses the
 * converter pipeline and gives us full control over headers and content.
 *
 * WHY Content-Disposition: attachment?
 * The browser will prompt "Save As" instead of rendering the CSV inline.
 * This is the correct behaviour for a download — no one wants raw CSV
 * displayed in their browser tab.
 * ═══════════════════════════════════════════════════════════════════════
 */
@RestController
@RequestMapping("/api/v1/crm/customers/export")
@RequiredArgsConstructor
@Tag(name = "CRM - Export", description = "Customer data export")
public class CustomerExportController {

    private final CustomerExportService exportService;
    private final FeatureGuard          featureGuard;

    @ModelAttribute
    public void requireCrmModule() {
        featureGuard.requireModule("crm");
    }

    /**
     * Export active customers as CSV.
     *
     * GET /api/v1/crm/customers/export/csv
     *
     * Response: attachment download named "customers-YYYY-MM-DD.csv"
     * Encoding:  UTF-8 with BOM so Excel opens it correctly
     *
     * WHY UTF-8 BOM?
     * Excel (especially on Windows) defaults to the system locale when
     * opening a CSV file.  Without a BOM, special characters (é, ü, ñ,
     * or Zulu/Xhosa names) appear as garbage.  The BOM byte sequence
     * (EF BB BF) tells Excel "this is UTF-8" and it renders correctly.
     * LibreOffice and Google Sheets ignore the BOM gracefully.
     */
    @GetMapping("/csv")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "Export active customers as CSV (UTF-8 with BOM for Excel compatibility)")
    public void exportActiveCsv(HttpServletResponse response) throws IOException {
        var tenantId  = TenantContext.getTenantIdAsObject();
        var filename  = "customers-" + LocalDate.now() + ".csv";

        setDownloadHeaders(response, filename);

        // Write UTF-8 BOM so Excel opens SA names (ë, ü, Zulu characters) correctly
        response.getOutputStream().write(new byte[]{ (byte) 0xEF, (byte) 0xBB, (byte) 0xBF });

        exportService.exportActiveToCsv(tenantId, response.getWriter());
    }

    /**
     * Export ALL customers including deleted — for POPIA data requests
     * or full data migration.  Restricted to CUSTOMER_DELETE authority
     * (admin-level) because it exposes deleted records.
     */
    @GetMapping("/csv/all")
    @PreAuthorize("hasAuthority('CUSTOMER_DELETE')")
    @Operation(summary = "Export all customers including deleted (admin / POPIA use)")
    public void exportAllCsv(HttpServletResponse response) throws IOException {
        var tenantId  = TenantContext.getTenantIdAsObject();
        var filename  = "customers-full-" + LocalDate.now() + ".csv";

        setDownloadHeaders(response, filename);
        response.getOutputStream().write(new byte[]{ (byte) 0xEF, (byte) 0xBB, (byte) 0xBF });

        exportService.exportAllToCsv(tenantId, response.getWriter());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static void setDownloadHeaders(HttpServletResponse response, String filename) {
        response.setContentType("text/csv; charset=UTF-8");
        // Content-Disposition: attachment triggers browser "Save As" dialog
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        // Prevent caching — export data must always be fresh
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
    }
}
