package za.co.handyflow.platform.admin.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.admin.application.internal.AdminReportingService;
import za.co.handyflow.platform.admin.application.internal.AdminService;
import za.co.handyflow.platform.admin.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERADMIN')")
@Tag(name = "Admin Portal", description = "HandyFlow superadmin operations — staff only")
public class AdminController {

    private final AdminService adminService;
    private final AdminReportingService adminReportingService;

    // ── Dashboard ─────────────────────────────────────────────────────────────

    @GetMapping("/dashboard")
    @Operation(summary = "Platform health snapshot — MRR, tenant counts, pilot expiry, top tenants")
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getDashboard()));
    }

    // ── Tenants ───────────────────────────────────────────────────────────────

    @GetMapping("/tenants")
    @Operation(summary = "List all tenants — search, filter by status, sort by MRR or signup date")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTenants(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "created_at") String sortBy,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                adminService.getTenants(search, status, sortBy, page, size)));
    }

    @GetMapping("/tenants/{slugOrId}")
    @Operation(summary = "Tenant detail — modules, users, MRR, recent activity")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTenantDetail(
            @PathVariable String slugOrId) {
        return ResponseEntity.ok(ApiResponse.success(
                adminService.getTenantDetail(slugOrId)));
    }

    @PostMapping("/tenants/extend-pilot")
    @Operation(summary = "Extend pilot period for a tenant by N days")
    public ResponseEntity<ApiResponse<Void>> extendPilot(
            @Valid @RequestBody ExtendPilotRequest req,
            HttpServletRequest http) {
        adminService.extendPilot(getAdminId(), getAdminEmail(), req.tenantSlug(),
                req.days(), getIp(http));
        return ResponseEntity.ok(ApiResponse.success(
                "Pilot extended by " + req.days() + " days for " + req.tenantSlug(), null));
    }

    @PostMapping("/tenants/suspend")
    @Operation(summary = "Suspend a tenant account")
    public ResponseEntity<ApiResponse<Void>> suspendTenant(
            @Valid @RequestBody SuspendTenantRequest req,
            HttpServletRequest http) {
        adminService.suspendTenant(getAdminId(), getAdminEmail(),
                req.tenantSlug(), req.reason(), getIp(http));
        return ResponseEntity.ok(ApiResponse.success(
                "Tenant " + req.tenantSlug() + " suspended", null));
    }

    @PostMapping("/tenants/{slug}/reactivate")
    @Operation(summary = "Reactivate a suspended tenant")
    public ResponseEntity<ApiResponse<Void>> reactivateTenant(
            @PathVariable String slug,
            HttpServletRequest http) {
        adminService.reactivateTenant(getAdminId(), getAdminEmail(), slug, getIp(http));
        return ResponseEntity.ok(ApiResponse.success("Tenant " + slug + " reactivated", null));
    }

    @PostMapping("/tenants/{slug}/modules/{moduleKey}/activate")
    @Operation(summary = "Force-activate a module for a tenant")
    public ResponseEntity<ApiResponse<Void>> forceActivateModule(
            @PathVariable String slug,
            @PathVariable String moduleKey,
            HttpServletRequest http) {
        adminService.forceActivateModule(getAdminId(), getAdminEmail(),
                slug, moduleKey, getIp(http));
        return ResponseEntity.ok(ApiResponse.success(
                "Module " + moduleKey + " activated for " + slug, null));
    }

    @PostMapping("/tenants/{slug}/modules/{moduleKey}/deactivate")
    @Operation(summary = "Force-deactivate a module for a tenant")
    public ResponseEntity<ApiResponse<Void>> forceDeactivateModule(
            @PathVariable String slug,
            @PathVariable String moduleKey,
            HttpServletRequest http) {
        adminService.forceDeactivateModule(getAdminId(), getAdminEmail(),
                slug, moduleKey, getIp(http));
        return ResponseEntity.ok(ApiResponse.success(
                "Module " + moduleKey + " deactivated for " + slug, null));
    }

    // ── Pilot management ──────────────────────────────────────────────────────

    @GetMapping("/pilots/expiring")
    @Operation(summary = "Tenants with trials expiring within N days (default 7)")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getExpiringPilots(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(ApiResponse.success(
                adminService.getExpiringPilots(days)));
    }

    // ── Billing ───────────────────────────────────────────────────────────────

    @GetMapping("/billing/mrr")
    @Operation(summary = "MRR breakdown by module — active count, trial count, revenue per module")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getMrrBreakdown() {
        return ResponseEntity.ok(ApiResponse.success(adminReportingService.getModuleMetrics()));
    }

    @GetMapping("/billing/overdue")
    @Operation(summary = "Overdue accounts — days past due, amount owed, grace period status")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getOverdueAccounts() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getOverdueAccounts()));
    }

    @PutMapping("/billing/modules/pricing")
    @Operation(summary = "Update monthly price for a module — applies to new activations only")
    public ResponseEntity<ApiResponse<Void>> updateModulePrice(
            @Valid @RequestBody UpdateModulePriceRequest req,
            HttpServletRequest http) {
        adminService.updateModulePrice(getAdminId(), getAdminEmail(),
                req.moduleKey(), req.newPrice(), getIp(http));
        return ResponseEntity.ok(ApiResponse.success(
                "Price updated for " + req.moduleKey() + " to R" + req.newPrice(), null));
    }

    // ── Incidents ─────────────────────────────────────────────────────────────

    @GetMapping("/incidents")
    @Operation(summary = "Incident inbox — INTERNAL Desk tickets from all tenants")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getIncidents(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getIncidents(status)));
    }

    @GetMapping("/incidents/{id}")
    @Operation(summary = "Incident detail — full ticket with all messages")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getIncidentDetail(
            @PathVariable java.util.UUID id) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getIncidentDetail(id)));
    }

    @PostMapping("/incidents/{id}/reply")
    @Operation(summary = "Admin replies to a tenant's desk ticket")
    public ResponseEntity<ApiResponse<Void>> replyToIncident(
            @PathVariable java.util.UUID id,
            @RequestBody java.util.Map<String, String> req) {
        adminService.replyToIncident(id, getAdminId(), getAdminEmail(),
                getAdminFullName(), req.get("message"));
        return ResponseEntity.ok(ApiResponse.success("Reply sent", null));
    }

    @PostMapping("/incidents/{id}/resolve")
    @Operation(summary = "Mark incident as resolved — closes the ticket and notifies tenant")
    public ResponseEntity<ApiResponse<Void>> resolveIncident(
            @PathVariable java.util.UUID id) {
        adminService.resolveIncident(id, getAdminId(), getAdminEmail());
        return ResponseEntity.ok(ApiResponse.success("Incident resolved", null));
    }

    @PostMapping("/incidents/{id}/assign")
    @Operation(summary = "Assign incident to an admin staff member")
    public ResponseEntity<ApiResponse<Void>> assignIncident(
            @PathVariable java.util.UUID id,
            @RequestBody java.util.Map<String, String> req) {
        adminService.assignIncident(id, java.util.UUID.fromString(req.get("assignToAdminId")),
                getAdminId(), getAdminEmail());
        return ResponseEntity.ok(ApiResponse.success("Incident assigned", null));
    }

    @GetMapping("/admin-staff")
    @Operation(summary = "List all admin users — used by incident assignment dropdown")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAdminStaff() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getAdminStaff()));
    }

    // ── Reports ───────────────────────────────────────────────────────────────

    @GetMapping("/reports/module-adoption")
    @Operation(summary = "Module adoption report — active, trial, cancelled, conversion rate per module")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getModuleAdoption() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getModuleAdoptionReport()));
    }

    // ── Audit log ─────────────────────────────────────────────────────────────

    @GetMapping("/audit-log")
    @Operation(summary = "Superadmin audit trail — every admin action logged with who/what/when")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAuditLog(
            @RequestParam(required = false) String targetId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                adminService.getAuditLog(targetId, page, size)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID getAdminId() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() != null)
            return UUID.fromString(auth.getPrincipal().toString());
        return UUID.fromString("00000000-0000-0000-0000-000000000001");
    }

    @SuppressWarnings("unchecked")
    private String getAdminEmail() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof java.util.Map) {
            var d = (java.util.Map<String, String>) auth.getDetails();
            String e = d.get("email");
            if (e != null && !e.isBlank()) return e;
        }
        return "unknown-admin";
    }

    @SuppressWarnings("unchecked")
    private String getAdminFullName() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof java.util.Map) {
            var d = (java.util.Map<String, String>) auth.getDetails();
            String n = d.get("fullName");
            if (n != null && !n.isBlank()) return n;
        }
        return "HandyFlow Support";
    }

    private String getIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        return forwarded != null ? forwarded.split(",")[0].trim() : req.getRemoteAddr();
    }
}
