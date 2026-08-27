// security/api/GateAccessController.java

package za.co.handyflow.platform.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.security.application.internal.GateAccessService;
import za.co.handyflow.platform.security.application.internal.ReportingService;
import za.co.handyflow.platform.security.application.internal.SiteAccessPdfService;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * FIX: mandatory correction from the mobile addendum — AccessPoint CRUD
 * uses SECURITY_MANAGE, never USER_UPDATE. The original plan's own §8
 * sketch cited PatrolRouteController as precedent for USER_UPDATE —
 * that controller is itself a confirmed, still-open bug from an earlier
 * permission-fix pass across this module and must not be propagated
 * into a new feature. Not fixed here — out of this feature's scope per
 * the addendum's own instruction; flagging again for whenever it's
 * picked up separately.
 */
@RestController
@RequestMapping("/api/v1/security")
@RequiredArgsConstructor
@Tag(name = "Security - Gate Access", description = "Supervisor-facing access point management and gate log")
public class GateAccessController {

    private final GateAccessService     gateAccessService;
    private final ReportingService      reportingService;
    private final SiteAccessPdfService  siteAccessPdfService;
    private final FeatureGuard          featureGuard;

    // ── Access Point CRUD ─────────────────────────────────────────────────────

    @PostMapping("/access-points")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    @Operation(summary = "Register a new gate/access point at a site")
    public ResponseEntity<ApiResponse<AccessPointResponse>> createAccessPoint(
            @Valid @RequestBody CreateAccessPointRequest request) {
        featureGuard.requireModule("security");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Access point created",
                gateAccessService.createAccessPoint(TenantContext.getTenantIdAsObject(), request)));
    }

    @GetMapping("/sites/{siteId}/access-points")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    public ResponseEntity<ApiResponse<List<AccessPointResponse>>> getAccessPoints(@PathVariable UUID siteId) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(
                gateAccessService.getAccessPoints(TenantContext.getTenantIdAsObject(), siteId)));
    }

    @PutMapping("/access-points/{id}")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    public ResponseEntity<ApiResponse<AccessPointResponse>> updateAccessPoint(
            @PathVariable UUID id, @Valid @RequestBody UpdateAccessPointRequest request) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("Access point updated",
                gateAccessService.updateAccessPoint(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @PostMapping("/access-points/{id}/deactivate")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    public ResponseEntity<ApiResponse<AccessPointResponse>> deactivateAccessPoint(@PathVariable UUID id) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("Access point deactivated",
                gateAccessService.deactivateAccessPoint(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/access-points/{id}/reactivate")
    @PreAuthorize("hasAuthority('SECURITY_MANAGE')")
    public ResponseEntity<ApiResponse<AccessPointResponse>> reactivateAccessPoint(@PathVariable UUID id) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success("Access point reactivated",
                gateAccessService.reactivateAccessPoint(TenantContext.getTenantIdAsObject(), id)));
    }

    // ── On-site list + gate log ──────────────────────────────────────────────

    @GetMapping("/sites/{siteId}/on-site")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Everyone currently on site — visitors, contractors, deliveries, vehicles",
            description = "Same read model the client portal's currentlyOnSite extension reuses.")
    public ResponseEntity<ApiResponse<List<GateRegisterEntryResponse>>> getOnSite(@PathVariable UUID siteId) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(
                gateAccessService.getOnSite(TenantContext.getTenantIdAsObject(), siteId)));
    }

    @GetMapping("/gate-entries/{entryId}/attachments")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Review evidence attached to a gate entry — ID scans, license disc photos, general photos")
    public ResponseEntity<ApiResponse<List<za.co.handyflow.platform.evidence.dto.EvidenceResponse>>> getAttachments(
            @PathVariable UUID entryId) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(
                gateAccessService.getAttachments(TenantContext.getTenantIdAsObject(), entryId)));
    }

    @GetMapping("/sites/{siteId}/gate-log")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Gate entry/exit history for a site, paginated")
    public ResponseEntity<ApiResponse<Page<GateRegisterEntryResponse>>> getGateLog(
            @PathVariable UUID siteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(
                gateAccessService.getGateLog(TenantContext.getTenantIdAsObject(), siteId, from, to, pageable)));
    }

    // ── Site Access / Visitor Report (fourth security report) ───────────────

    @GetMapping("/reports/site-access")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(
            summary = "Site access / visitor report (JSON)",
            description = "Entry/exit counts by type and status for a site over a calendar " +
                    "month. month format: YYYY-MM. Overrides this controller's own class-level " +
                    "gate (SECURITY_READ) — same authority regardless, this is stated explicitly " +
                    "since ReportingController, the OTHER report controller in this module, " +
                    "still has a confirmed, out-of-scope USER_UPDATE bug on its own class-level " +
                    "gate — flagging so this one is never mistaken for having the same issue.")
    public ResponseEntity<ApiResponse<za.co.handyflow.platform.security.dto.SiteAccessReport>> getSiteAccessReport(
            @RequestParam UUID siteId,
            @RequestParam String month) {
        featureGuard.requireModule("security");
        return ResponseEntity.ok(ApiResponse.success(
                reportingService.getSiteAccessReport(TenantContext.getTenantIdAsObject(), siteId,
                        java.time.YearMonth.parse(month))));
    }

    @GetMapping("/reports/site-access/pdf")
    @PreAuthorize("hasAuthority('SECURITY_READ')")
    @Operation(summary = "Site access / visitor report (PDF download)")
    public ResponseEntity<byte[]> getSiteAccessReportPdf(
            @RequestParam UUID siteId,
            @RequestParam String month) {
        featureGuard.requireModule("security");
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        var report = reportingService.getSiteAccessReport(tenantId, siteId, java.time.YearMonth.parse(month));
        byte[] pdf = siteAccessPdfService.siteAccessPdf(report, tenantId);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"site-access-" + report.siteName().replaceAll("[^a-zA-Z0-9]", "-")
                                + "-" + month + ".pdf\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}