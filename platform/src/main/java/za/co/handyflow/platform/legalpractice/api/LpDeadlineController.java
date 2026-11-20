package za.co.handyflow.platform.legalpractice.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.legalpractice.application.internal.LpDeadlineService;
import za.co.handyflow.platform.legalpractice.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Deadline rules, the firm's court calendar, and deadlines worked out from a rule and a trigger date. */
@RestController
@RequestMapping("/api/v1/legal-practice/deadlines")
@RequiredArgsConstructor
@Tag(name = "Legal Practice - Deadlines", description = "Rule-based deadline calculation")
public class LpDeadlineController {

    private static final String READ = "hasAnyAuthority('LEGALPRACTICE_READ','LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')";
    private static final String COMPUTE = "hasAnyAuthority('LEGALPRACTICE_DEADLINE_COMPUTE','LEGALPRACTICE_MANAGE','LEGALPRACTICE_ADMIN')";
    private static final String RULES = "hasAnyAuthority('LEGALPRACTICE_DEADLINE_RULES','LEGALPRACTICE_ADMIN')";

    private final LpDeadlineService service;
    private final FeatureGuard featureGuard;

    // ── rules ───────────────────────────────────────────────────────────────

    @GetMapping("/rules")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<DeadlineRuleResponse>>> rules(@RequestParam(defaultValue = "false") boolean includeInactive) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(service.listRules(TenantContext.getTenantIdAsObject(), includeInactive)));
    }

    @PostMapping("/rules")
    @PreAuthorize(RULES)
    public ResponseEntity<ApiResponse<DeadlineRuleResponse>> createRule(@Valid @RequestBody DeadlineRuleRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Rule created", service.createRule(TenantContext.getTenantIdAsObject(), req)));
    }

    @PutMapping("/rules/{id}")
    @PreAuthorize(RULES)
    public ResponseEntity<ApiResponse<DeadlineRuleResponse>> updateRule(@PathVariable UUID id, @Valid @RequestBody DeadlineRuleRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Rule updated", service.updateRule(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/rules/{id}/activate")
    @PreAuthorize(RULES)
    public ResponseEntity<ApiResponse<DeadlineRuleResponse>> activate(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Rule switched on", service.setRuleActive(TenantContext.getTenantIdAsObject(), id, true)));
    }

    @PostMapping("/rules/{id}/deactivate")
    @PreAuthorize(RULES)
    public ResponseEntity<ApiResponse<DeadlineRuleResponse>> deactivate(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Rule switched off", service.setRuleActive(TenantContext.getTenantIdAsObject(), id, false)));
    }

    @PostMapping("/rules/install-templates")
    @PreAuthorize(RULES)
    @Operation(summary = "Add the template rules the firm does not have yet. Starting points only: a lawyer must confirm each against the current rules")
    public ResponseEntity<ApiResponse<InstallTemplatesResponse>> installTemplates() {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Templates installed", service.installTemplates(TenantContext.getTenantIdAsObject())));
    }

    // ── court calendar ──────────────────────────────────────────────────────

    @GetMapping("/calendar")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<CalendarDayResponse>>> calendar(@RequestParam(required = false) Integer year) {
        featureGuard.requireModule("legalpractice");
        int y = year == null ? LocalDate.now().getYear() : year;
        return ResponseEntity.ok(ApiResponse.success(service.calendar(TenantContext.getTenantIdAsObject(), y)));
    }

    @PostMapping("/calendar")
    @PreAuthorize(RULES)
    public ResponseEntity<ApiResponse<CalendarDayResponse>> addCalendarEntry(@Valid @RequestBody CalendarEntryRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Day added", service.addCalendarEntry(
                TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @DeleteMapping("/calendar/{id}")
    @PreAuthorize(RULES)
    public ResponseEntity<ApiResponse<Void>> removeCalendarEntry(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        service.removeCalendarEntry(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Day removed", null));
    }

    // ── computing ───────────────────────────────────────────────────────────

    @PostMapping("/preview")
    @PreAuthorize(COMPUTE)
    @Operation(summary = "Work out a date without saving anything")
    public ResponseEntity<ApiResponse<DeadlinePreviewResponse>> preview(@Valid @RequestBody DeadlinePreviewRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(service.preview(TenantContext.getTenantIdAsObject(), req)));
    }

    @PostMapping("/matters/{matterId}/compute")
    @PreAuthorize(COMPUTE)
    @Operation(summary = "Work out a deadline for a matter and record it as a key date")
    public ResponseEntity<ApiResponse<DeadlineComputationResponse>> compute(@PathVariable UUID matterId, @Valid @RequestBody ComputeDeadlineRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Deadline computed", service.compute(
                TenantContext.getTenantIdAsObject(), matterId, req, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping("/matters/{matterId}")
    @PreAuthorize(READ)
    public ResponseEntity<ApiResponse<List<DeadlineComputationResponse>>> forMatter(@PathVariable UUID matterId) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(service.listForMatter(TenantContext.getTenantIdAsObject(), matterId)));
    }

    @PostMapping("/computations/{id}/recompute")
    @PreAuthorize(COMPUTE)
    @Operation(summary = "Correct a computed deadline (for example a wrong trigger date). Needs a reason; the old computation is kept")
    public ResponseEntity<ApiResponse<DeadlineComputationResponse>> recompute(@PathVariable UUID id, @Valid @RequestBody RecomputeDeadlineRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Deadline corrected", service.recompute(
                TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }
}
