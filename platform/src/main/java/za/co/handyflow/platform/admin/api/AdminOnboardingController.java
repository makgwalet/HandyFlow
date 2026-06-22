package za.co.handyflow.platform.admin.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.admin.application.internal.AdminOnboardingService;
import za.co.handyflow.platform.shared.ApiResponse;

import java.util.*;

@RestController
@RequestMapping("/api/v1/admin/onboarding")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERADMIN')")
@Tag(name = "Admin Onboarding", description = "Guided tenant onboarding — seed data, import users, enable modules")
public class AdminOnboardingController {

    private final AdminOnboardingService onboardingService;

    @GetMapping
    @Operation(summary = "List onboarding sessions — filter by status: IN_PROGRESS|COMPLETED|ABANDONED")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getSessions(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(ApiResponse.success(onboardingService.getSessions(status, limit)));
    }

    @PostMapping("/start")
    @Operation(summary = "Start an onboarding session for a tenant")
    public ResponseEntity<ApiResponse<Map<String, Object>>> startSession(
            @RequestParam String tenantSlug) {
        return ResponseEntity.status(201).body(ApiResponse.success("Onboarding session started",
                onboardingService.startSession(tenantSlug, getAdminId(), getAdminEmail())));
    }

    @GetMapping("/{sessionId}")
    @Operation(summary = "Get onboarding session detail and checklist progress")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSession(
            @PathVariable UUID sessionId) {
        return ResponseEntity.ok(ApiResponse.success(onboardingService.getSession(sessionId)));
    }

    @PostMapping("/{sessionId}/seed-company")
    @Operation(summary = "Seed company profile on behalf of tenant")
    public ResponseEntity<ApiResponse<Void>> seedCompany(
            @PathVariable UUID sessionId,
            @RequestBody Map<String, String> req) {
        onboardingService.seedCompanyProfile(
                sessionId,
                req.get("registrationNumber"),
                req.get("vatNumber"),
                req.get("phone"),
                req.get("address"),
                req.get("city"),
                req.get("postalCode"),
                req.getOrDefault("country", "South Africa"),
                req.get("industry"),
                req.get("website"),
                getAdminId(), getAdminEmail());
        return ResponseEntity.ok(ApiResponse.success("Company profile seeded", null));
    }

    @PostMapping("/{sessionId}/import-users")
    @Operation(summary = "Bulk import users from CSV rows — parsed on frontend")
    public ResponseEntity<ApiResponse<Map<String, Object>>> importUsers(
            @PathVariable UUID sessionId,
            @RequestBody Map<String, Object> req) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> rows = (List<Map<String, String>>) req.get("rows");
        return ResponseEntity.ok(ApiResponse.success("Import complete",
                onboardingService.importUsers(sessionId, rows, getAdminId(), getAdminEmail())));
    }

    @PostMapping("/{sessionId}/parse-csv")
    @Operation(summary = "Parse a raw CSV string into rows — use before /import-users for preview")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> parseCsv(
            @PathVariable UUID sessionId,
            @RequestBody Map<String, String> req) {
        List<Map<String, String>> rows = onboardingService.parseCsv(req.get("csv"));
        return ResponseEntity.ok(ApiResponse.success(rows));
    }

    @PostMapping("/{sessionId}/enable-modules")
    @Operation(summary = "Force-activate a list of modules for the tenant")
    public ResponseEntity<ApiResponse<Map<String, Object>>> enableModules(
            @PathVariable UUID sessionId,
            @RequestBody Map<String, Object> req) {
        @SuppressWarnings("unchecked")
        List<String> modules = (List<String>) req.get("moduleKeys");
        return ResponseEntity.ok(ApiResponse.success("Modules enabled",
                onboardingService.enableModules(sessionId, modules, getAdminId(), getAdminEmail())));
    }

    @PostMapping("/{sessionId}/welcome-sent")
    @Operation(summary = "Mark welcome email as sent")
    public ResponseEntity<ApiResponse<Void>> markWelcomeSent(@PathVariable UUID sessionId) {
        onboardingService.markWelcomeSent(sessionId, getAdminId(), getAdminEmail());
        return ResponseEntity.ok(ApiResponse.success("Welcome email marked as sent", null));
    }

    @PutMapping("/{sessionId}/notes")
    @Operation(summary = "Update onboarding session notes")
    public ResponseEntity<ApiResponse<Void>> updateNotes(
            @PathVariable UUID sessionId,
            @RequestBody Map<String, String> req) {
        onboardingService.updateNotes(sessionId, req.get("notes"));
        return ResponseEntity.ok(ApiResponse.success("Notes updated", null));
    }

    @PostMapping("/{sessionId}/complete")
    @Operation(summary = "Mark onboarding session as completed")
    public ResponseEntity<ApiResponse<Void>> complete(@PathVariable UUID sessionId) {
        onboardingService.completeSession(sessionId, getAdminId(), getAdminEmail());
        return ResponseEntity.ok(ApiResponse.success("Onboarding completed", null));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID getAdminId() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        return UUID.fromString(auth.getPrincipal().toString());
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
}
