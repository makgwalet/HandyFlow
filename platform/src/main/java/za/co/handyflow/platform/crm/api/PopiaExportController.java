package za.co.handyflow.platform.crm.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.crm.application.internal.PopiaExportPdfService;
import za.co.handyflow.platform.crm.application.internal.PopiaExportService;
import za.co.handyflow.platform.shared.TenantContext;

import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;

/**
 * PopiaExportController — POPIA data subject access request endpoint.
 *
 * FIX: backlog 4.4 — "POPIA_EXPORT piggybacks on CUSTOMER_DELETE
 * permission." This class's own prior comment flagged it directly:
 * "Consider a dedicated POPIA_EXPORT authority in production." Anyone
 * with delete rights could also export the full data-subject history —
 * which reveals internal user IDs of who accessed/modified the record —
 * an over-grant unrelated to what delete authority is actually meant to
 * protect. Now a dedicated authority, seeded the same way the module
 * system auto-generates permissions (see the V245 migration, which
 * mirrors AdminLookupService.createModule()'s own INSERT+grant pattern)
 * and granted to every role that already held CUSTOMER_DELETE, so no
 * existing access is silently revoked by this narrowing.
 *
 * WHY GET and not POST?
 * The export is idempotent — calling it twice produces the same data
 * (plus a second audit log entry).  GET is correct for read operations
 * that return a downloadable file.
 */
@RestController
@RequestMapping("/api/v1/crm/customers")
@RequiredArgsConstructor
@Tag(name = "CRM - POPIA", description = "POPIA data subject access requests")
public class PopiaExportController {

    private final PopiaExportService    popiaExportService;
    private final PopiaExportPdfService popiaExportPdfService;
    private final FeatureGuard          featureGuard;

    @ModelAttribute
    public void requireCrmModule() {
        featureGuard.requireModule("crm");
    }

    /**
     * Generate and download a POPIA-compliant JSON export for a single customer.
     *
     * Response: downloadable .json file containing:
     *   - All personal information held on the customer record
     *   - Complete activity / processing history (chronological)
     *   - Export metadata: who generated it and when
     *
     * The export is also recorded as a NOTE on the customer's activity
     * timeline so there is a permanent, queryable record of the export.
     */
    @GetMapping("/{id}/popia-export")
    @PreAuthorize("hasAuthority('POPIA_EXPORT')")
    @Operation(summary = "Generate POPIA data subject export — full personal data + processing history")
    public void exportPopia(
            @PathVariable UUID id,
            HttpServletResponse response
    ) throws IOException {
        var tenantId    = TenantContext.getTenantIdAsObject();
        var requestedBy = currentUserId();
        var filename    = "popia-export-" + id + "-" + LocalDate.now() + ".json";

        response.setContentType("application/json; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("X-POPIA-Export", "true");

        popiaExportService.exportCustomerJson(tenantId, id, requestedBy, response.getWriter());
    }

    /**
     * FIX: "no POPIA export as PDF" gap — same request, human-readable
     * document instead of raw JSON. Deliberately calls PopiaExportPdfService
     * directly rather than through PopiaExportService — see that service's
     * own doc comment for why it doesn't share PopiaExportDto with the
     * JSON export.
     */
    @GetMapping("/{id}/popia-export.pdf")
    @PreAuthorize("hasAuthority('POPIA_EXPORT')")
    @Operation(summary = "Generate POPIA data subject export as a human-readable PDF")
    public void exportPopiaPdf(
            @PathVariable UUID id,
            HttpServletResponse response
    ) throws IOException {
        var tenantId    = TenantContext.getTenantIdAsObject();
        var requestedBy = currentUserId();
        var filename    = "popia-export-" + id + "-" + LocalDate.now() + ".pdf";

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("X-POPIA-Export", "true");

        popiaExportPdfService.generatePdf(tenantId, id, requestedBy, response.getOutputStream());
    }

    private UUID currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        try { return UUID.fromString(auth.getName()); }
        catch (Exception e) { return null; }
    }
}