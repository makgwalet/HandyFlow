package za.co.handyflow.platform.internalaudit.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.internalaudit.application.internal.InternalAuditService;
import za.co.handyflow.platform.internalaudit.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Internal Audit Phase 1, per the design agreed with the product owner
 * across several rounds of scoping: Audit Universe, hybrid risk scoring
 * (system-calculated + auditor override, with a required justification
 * whenever they diverge), Annual Audit Plan, and Engagement shell with
 * engagement-scoped role assignments (deliberately separate from the
 * AUDIT_READ/AUDIT_MANAGE/AUDIT_ADMIN system permissions below — see
 * EngagementAssignment's own class comment).
 * <p>
 * Deliberately NOT in this controller (later phases, per the agreed
 * phased build): planning-detail fields on the engagement (objectives/
 * scope/audit criteria), the full materiality model, workpapers
 * (Phase 2); GL sampling against AccJournalEntry, sampling plans,
 * testing, exceptions (Phase 3); findings, remediation tracking, and
 * report sign-off via ApprovalFacade (Phase 4).
 */
@RestController
@RequestMapping("/api/v1/internal-audit")
@RequiredArgsConstructor
@Tag(name = "Internal Audit", description = "Risk-based internal audit planning — Phase 1")
public class InternalAuditController {

    private final InternalAuditService auditService;
    private final FeatureGuard featureGuard;

    // ── Universe ─────────────────────────────────────────────────────────────

    @GetMapping("/universe")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public ResponseEntity<ApiResponse<List<UniverseEntryResponse>>> getUniverse() {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(auditService.getUniverse(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/universe")
    @PreAuthorize("hasAuthority('AUDIT_MANAGE')")
    public ResponseEntity<ApiResponse<UniverseEntryResponse>> createUniverseEntry(
            @Valid @RequestBody CreateUniverseEntryRequest req) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                auditService.createUniverseEntry(TenantContext.getTenantIdAsObject(), req,
                        TenantContext.getCurrentUserId())));
    }

    @PutMapping("/universe/{id}")
    @PreAuthorize("hasAuthority('AUDIT_MANAGE')")
    public ResponseEntity<ApiResponse<UniverseEntryResponse>> updateUniverseEntry(
            @PathVariable UUID id, @Valid @RequestBody CreateUniverseEntryRequest req) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(
                auditService.updateUniverseEntry(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/universe/{id}/deactivate")
    @PreAuthorize("hasAuthority('AUDIT_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deactivateUniverseEntry(@PathVariable UUID id) {
        featureGuard.requireModule("internal-audit");
        auditService.deactivateUniverseEntry(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ── Risk Assessment ──────────────────────────────────────────────────────

    @GetMapping("/universe/{id}/risk-assessments")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public ResponseEntity<ApiResponse<List<RiskAssessmentResponse>>> getRiskHistory(@PathVariable UUID id) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(
                auditService.getRiskHistory(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/universe/{id}/risk-assessments")
    @PreAuthorize("hasAuthority('AUDIT_MANAGE')")
    @Operation(summary = "Record a new risk assessment",
            description = "timeSinceLastAuditScore is calculated server-side from the universe entry's own lastAuditDate, " +
                    "not supplied by the caller — the one V1 input that's genuinely derivable from data.")
    public ResponseEntity<ApiResponse<RiskAssessmentResponse>> createRiskAssessment(
            @PathVariable UUID id, @Valid @RequestBody CreateRiskAssessmentRequest req) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                auditService.createRiskAssessment(TenantContext.getTenantIdAsObject(), id, req,
                        TenantContext.getCurrentUserId())));
    }

    @PostMapping("/risk-assessments/{id}/override")
    @PreAuthorize("hasAuthority('AUDIT_MANAGE')")
    @Operation(summary = "Set the final audit risk, overriding the system-calculated value",
            description = "A reason is required whenever finalAuditRisk genuinely differs from the system-calculated risk — enforced in the domain model, not just this layer.")
    public ResponseEntity<ApiResponse<RiskAssessmentResponse>> overrideRisk(
            @PathVariable UUID id, @Valid @RequestBody OverrideRiskRequest req) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(
                auditService.overrideRisk(TenantContext.getTenantIdAsObject(), id, req,
                        TenantContext.getCurrentUserId())));
    }

    // ── Annual Plan ──────────────────────────────────────────────────────────

    @GetMapping("/plans")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public ResponseEntity<ApiResponse<List<AnnualPlanResponse>>> getAnnualPlans() {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(auditService.getAnnualPlans(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/plans/{id}")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public ResponseEntity<ApiResponse<AnnualPlanResponse>> getAnnualPlan(@PathVariable UUID id) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(
                auditService.getAnnualPlan(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/plans")
    @PreAuthorize("hasAuthority('AUDIT_MANAGE')")
    public ResponseEntity<ApiResponse<AnnualPlanResponse>> createAnnualPlan(
            @Valid @RequestBody CreateAnnualPlanRequest req) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                auditService.createAnnualPlan(TenantContext.getTenantIdAsObject(), req,
                        TenantContext.getCurrentUserId())));
    }

    @PostMapping("/plans/{id}/approve")
    @PreAuthorize("hasAuthority('AUDIT_ADMIN')")
    @Operation(summary = "Approve the annual plan", description = "A Head of Internal Audit action, per the agreed role model — gated on AUDIT_ADMIN specifically, not AUDIT_MANAGE.")
    public ResponseEntity<ApiResponse<AnnualPlanResponse>> approveAnnualPlan(@PathVariable UUID id) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(
                auditService.approveAnnualPlan(TenantContext.getTenantIdAsObject(), id,
                        TenantContext.getCurrentUserId())));
    }

    @PostMapping("/plans/{id}/entries")
    @PreAuthorize("hasAuthority('AUDIT_MANAGE')")
    public ResponseEntity<ApiResponse<PlanEntryResponse>> addPlanEntry(
            @PathVariable UUID id, @Valid @RequestBody AddPlanEntryRequest req) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                auditService.addPlanEntry(TenantContext.getTenantIdAsObject(), id, req)));
    }

    // ── Engagement ───────────────────────────────────────────────────────────

    @GetMapping("/engagements")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public ResponseEntity<ApiResponse<List<EngagementResponse>>> getEngagements() {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(auditService.getEngagements(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/engagements")
    @PreAuthorize("hasAuthority('AUDIT_MANAGE')")
    public ResponseEntity<ApiResponse<EngagementResponse>> createEngagement(
            @Valid @RequestBody CreateEngagementRequest req) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                auditService.createEngagement(TenantContext.getTenantIdAsObject(), req,
                        TenantContext.getCurrentUserId())));
    }

    @PostMapping("/engagements/{id}/assignments")
    @PreAuthorize("hasAuthority('AUDIT_MANAGE')")
    @Operation(summary = "Assign a user an engagement-scoped audit role",
            description = "Deliberately separate from system permissions — the same user can hold a different role on a different engagement. See EngagementAssignment's own class comment.")
    public ResponseEntity<ApiResponse<EngagementAssignmentResponse>> assignRole(
            @PathVariable UUID id, @Valid @RequestBody AssignEngagementRoleRequest req) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                auditService.assignRole(TenantContext.getTenantIdAsObject(), id, req,
                        TenantContext.getCurrentUserId())));
    }
}
