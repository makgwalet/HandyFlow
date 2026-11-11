package za.co.handyflow.platform.events.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.events.application.internal.EventDashboardService;
import za.co.handyflow.platform.events.application.internal.EventReportService;
import za.co.handyflow.platform.events.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/**
 * Dashboards and downloadable reports. Dashboards return JSON; reports return
 * files. All three event controllers share the /api/v1/events base path.
 * <p>
 * WHICH PERMISSION GUARDS WHAT, and why:
 * operational dashboards show only counts, so EVENTS_READ suffices; anything
 * with money needs the budget-read permission; anything that carries a
 * guest's personal details needs EVENTS_EXPORT, which is audited.
 */
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Tag(name = "Event dashboards & reports", description = "Overview, registration, attendance and close-out dashboards; CSV and PDF reports")
public class EventReportController {

    private final EventDashboardService dashboards;
    private final EventReportService    reports;

    // ── Dashboards ────────────────────────────────────────────────────────────

    @GetMapping("/dashboard/overview")
    @PreAuthorize(EventsPermissions.REPORTS_READ)
    @Operation(summary = "All events by status, what's coming up and live, and what needs attention")
    public ResponseEntity<ApiResponse<EventsOverviewResponse>> overview() {
        return ResponseEntity.ok(ApiResponse.success("Success",
                dashboards.getOverview(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/{id}/dashboard/registration")
    @PreAuthorize(EventsPermissions.REPORTS_READ)
    @Operation(summary = "Registrations, capacity, tiers, sources, waitlist and registrations per day")
    public ResponseEntity<ApiResponse<RegistrationDashboardResponse>> registration(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                dashboards.getRegistration(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/{id}/dashboard/attendance")
    @PreAuthorize(EventsPermissions.REPORTS_READ)
    @Operation(summary = "Live door numbers: checked in, remaining, scan outcomes, arrivals per 15 minutes. Cheap to poll.")
    public ResponseEntity<ApiResponse<AttendanceDashboardResponse>> attendance(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                dashboards.getAttendance(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/{id}/dashboard/close-out")
    @PreAuthorize(EventsPermissions.BUDGET_READ)
    @Operation(summary = "Attendance reconciliation, final cost position and the close-out checklist (includes money)")
    public ResponseEntity<ApiResponse<CloseOutResponse>> closeOut(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                dashboards.getCloseOut(TenantContext.getTenantIdAsObject(), id)));
    }

    // ── Reports with personal information (audited) ───────────────────────────

    @GetMapping("/{id}/reports/guests.csv")
    @PreAuthorize(EventsPermissions.EXPORT_PII)
    @Operation(summary = "Guest register CSV — every registration record. Contains personal information; audited.")
    public ResponseEntity<byte[]> guestRegister(@PathVariable UUID id) {
        return file(reports.guestRegister(TenantContext.getTenantIdAsObject(), id));
    }

    @GetMapping("/{id}/reports/no-shows.csv")
    @PreAuthorize(EventsPermissions.EXPORT_PII)
    @Operation(summary = "No-show CSV — registered but never checked in. Only once the event is LIVE or COMPLETED; audited.")
    public ResponseEntity<byte[]> noShows(@PathVariable UUID id) {
        return file(reports.noShows(TenantContext.getTenantIdAsObject(), id));
    }

    @GetMapping("/{id}/reports/check-ins.csv")
    @PreAuthorize(EventsPermissions.EXPORT_PII)
    @Operation(summary = "Check-in log CSV — every scan, oldest first, including failed and repeated scans; audited.")
    public ResponseEntity<byte[]> checkInLog(@PathVariable UUID id) {
        return file(reports.checkInLog(TenantContext.getTenantIdAsObject(), id));
    }

    // ── Financial reports ─────────────────────────────────────────────────────

    @GetMapping("/{id}/reports/budget.csv")
    @PreAuthorize(EventsPermissions.BUDGET_READ)
    @Operation(summary = "Budget vs actual CSV, one row per line plus a total; audited")
    public ResponseEntity<byte[]> budget(@PathVariable UUID id) {
        return file(reports.budgetVsActual(TenantContext.getTenantIdAsObject(), id));
    }

    @GetMapping("/{id}/reports/cost-entries.csv")
    @PreAuthorize(EventsPermissions.BUDGET_READ)
    @Operation(summary = "Every invoice and payment CSV, voided entries included and marked; audited")
    public ResponseEntity<byte[]> costEntries(@PathVariable UUID id) {
        return file(reports.costEntries(TenantContext.getTenantIdAsObject(), id));
    }

    @GetMapping("/{id}/reports/close-out.pdf")
    @PreAuthorize(EventsPermissions.BUDGET_READ)
    @Operation(summary = "Close-out pack PDF — attendance, tiers, budget vs actual, supplier balances, checklist. "
            + "Contains no guest personal information; audited.")
    public ResponseEntity<byte[]> closeOutPack(@PathVariable UUID id) {
        return file(reports.closeOutPack(TenantContext.getTenantIdAsObject(), id));
    }

    /**
     * no-store: a downloaded report must not linger in a browser or proxy cache,
     * where it could be read by the next person to use that machine.
     */
    private ResponseEntity<byte[]> file(EventReportService.Export export) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + export.filename() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType(export.contentType()))
                .body(export.content());
    }
}