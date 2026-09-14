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

    // ── Phase 2: Planning detail + Materiality ──────────────────────────────────

    @PutMapping("/engagements/{id}/planning")
    @PreAuthorize("hasAuthority('AUDIT_MANAGE')")
    @Operation(summary = "Set engagement planning detail and the engagement-level materiality thresholds")
    public ResponseEntity<ApiResponse<EngagementResponse>> updatePlanning(
            @PathVariable UUID id, @RequestBody UpdatePlanningRequest req) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(
                auditService.updatePlanning(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/engagements/{id}/specific-materiality")
    @PreAuthorize("hasAuthority('AUDIT_MANAGE')")
    @Operation(summary = "Add an optional account/GL-segment-specific materiality threshold",
            description = "Only created when the auditor decides a specific account genuinely needs its own threshold — not a required per-account configuration step.")
    public ResponseEntity<ApiResponse<SpecificMaterialityResponse>> addSpecificMateriality(
            @PathVariable UUID id, @Valid @RequestBody AddSpecificMaterialityRequest req) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                auditService.addSpecificMateriality(TenantContext.getTenantIdAsObject(), id, req)));
    }

    // ── Phase 2: Workpapers ──────────────────────────────────────────────────────
    // Direct structural mirror of AccWorkpaperController — see
    // InternalAuditService's own comment on this section for the fuller
    // reasoning.

    @PostMapping("/engagements/{id}/workpaper-folders")
    @PreAuthorize("hasAuthority('AUDIT_MANAGE')")
    public ResponseEntity<ApiResponse<WorkpaperFolderResponse>> createWorkpaperFolder(
            @PathVariable UUID id, @Valid @RequestBody CreateWorkpaperFolderRequest req) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                auditService.createWorkpaperFolder(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @GetMapping("/engagements/{id}/workpaper-folders")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public ResponseEntity<ApiResponse<List<WorkpaperFolderResponse>>> getWorkpaperFolders(@PathVariable UUID id) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(
                auditService.getWorkpaperFolders(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/engagements/{id}/workpaper-files")
    @PreAuthorize("hasAuthority('AUDIT_MANAGE')")
    public ResponseEntity<ApiResponse<WorkpaperFileResponse>> uploadWorkpaperFile(
            @PathVariable UUID id, @Valid @RequestBody UploadWorkpaperFileRequest req) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                auditService.uploadWorkpaperFile(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @GetMapping("/engagements/{id}/workpaper-folders/{folderId}/files")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public ResponseEntity<ApiResponse<List<WorkpaperFileResponse>>> getWorkpaperFiles(
            @PathVariable UUID id, @PathVariable UUID folderId) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(
                auditService.getWorkpaperFiles(TenantContext.getTenantIdAsObject(), folderId)));
    }

    @GetMapping("/engagements/{id}/workpaper-folders/{folderId}/files/deleted")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public ResponseEntity<ApiResponse<List<WorkpaperFileResponse>>> getDeletedWorkpaperFiles(
            @PathVariable UUID id, @PathVariable UUID folderId) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(
                auditService.getDeletedWorkpaperFiles(TenantContext.getTenantIdAsObject(), folderId)));
    }

    @PostMapping("/workpaper-files/{fileId}/status")
    @PreAuthorize("hasAuthority('AUDIT_MANAGE')")
    @Operation(summary = "Advance or reopen a workpaper file's review status",
            description = "action is one of PREPARE | REVIEW | SIGN_OFF | REOPEN — maps directly onto the file's own state machine.")
    public ResponseEntity<ApiResponse<WorkpaperFileResponse>> updateWorkpaperFileStatus(
            @PathVariable UUID fileId, @Valid @RequestBody UpdateWorkpaperStatusRequest req) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(
                auditService.updateWorkpaperFileStatus(TenantContext.getTenantIdAsObject(), fileId, req,
                        TenantContext.getCurrentUserId())));
    }

    @DeleteMapping("/workpaper-files/{fileId}")
    @PreAuthorize("hasAuthority('AUDIT_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deleteWorkpaperFile(@PathVariable UUID fileId) {
        featureGuard.requireModule("internal-audit");
        auditService.deleteWorkpaperFile(TenantContext.getTenantIdAsObject(), fileId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/workpaper-files/{fileId}/restore")
    @PreAuthorize("hasAuthority('AUDIT_MANAGE')")
    public ResponseEntity<ApiResponse<WorkpaperFileResponse>> restoreWorkpaperFile(@PathVariable UUID fileId) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(
                auditService.restoreWorkpaperFile(TenantContext.getTenantIdAsObject(), fileId)));
    }

    // ── Phase 3: GL Sampling & Testing ──────────────────────────────────────────

    @PostMapping("/engagements/{id}/sampling-plans")
    @PreAuthorize("hasAuthority('AUDIT_MANAGE')")
    @Operation(summary = "Create a sampling plan and draw the sample",
            description = "Population is calculated server-side from posted journal entries in the given period. The sample is drawn immediately using random selection.")
    public ResponseEntity<ApiResponse<SamplingPlanResponse>> createSamplingPlan(
            @PathVariable UUID id, @Valid @RequestBody CreateSamplingPlanRequest req) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                auditService.createSamplingPlan(TenantContext.getTenantIdAsObject(), id, req,
                        TenantContext.getCurrentUserId())));
    }

    @GetMapping("/engagements/{id}/sampling-plans")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public ResponseEntity<ApiResponse<List<SamplingPlanResponse>>> getSamplingPlans(@PathVariable UUID id) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(
                auditService.getSamplingPlans(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/sampling-plans/{id}/review")
    @PreAuthorize("hasAuthority('AUDIT_MANAGE')")
    @Operation(summary = "Review and finalize a sampling plan")
    public ResponseEntity<ApiResponse<SamplingPlanResponse>> reviewSamplingPlan(@PathVariable UUID id) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(
                auditService.reviewSamplingPlan(TenantContext.getTenantIdAsObject(), id,
                        TenantContext.getCurrentUserId())));
    }

    @PostMapping("/sample-items/{id}/tests")
    @PreAuthorize("hasAuthority('AUDIT_MANAGE')")
    public ResponseEntity<ApiResponse<AuditTestResponse>> createTest(
            @PathVariable UUID id, @Valid @RequestBody CreateAuditTestRequest req) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                auditService.createTest(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/tests/{id}/result")
    @PreAuthorize("hasAuthority('AUDIT_MANAGE')")
    public ResponseEntity<ApiResponse<AuditTestResponse>> recordTestResult(
            @PathVariable UUID id, @Valid @RequestBody RecordTestResultRequest req) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(
                auditService.recordTestResult(TenantContext.getTenantIdAsObject(), id, req,
                        TenantContext.getCurrentUserId())));
    }

    @PostMapping("/tests/{id}/exceptions")
    @PreAuthorize("hasAuthority('AUDIT_MANAGE')")
    @Operation(summary = "Raise an exception against a test",
            description = "severity is independent of the engagement/sampling plan's own risk level — never derived from it, per the agreed hard constraint.")
    public ResponseEntity<ApiResponse<AuditExceptionResponse>> raiseException(
            @PathVariable UUID id, @Valid @RequestBody RaiseExceptionRequest req) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                auditService.raiseException(TenantContext.getTenantIdAsObject(), id, req,
                        TenantContext.getCurrentUserId())));
    }

    @PostMapping("/exceptions/{id}/dismiss")
    @PreAuthorize("hasAuthority('AUDIT_MANAGE')")
    public ResponseEntity<ApiResponse<AuditExceptionResponse>> dismissException(
            @PathVariable UUID id, @Valid @RequestBody DismissExceptionRequest req) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(
                auditService.dismissException(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @GetMapping("/engagements/{id}/exceptions")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    @Operation(summary = "All exceptions across every sampling plan for this engagement")
    public ResponseEntity<ApiResponse<List<AuditExceptionResponse>>> getExceptions(@PathVariable UUID id) {
        featureGuard.requireModule("internal-audit");
        return ResponseEntity.ok(ApiResponse.success(
                auditService.getExceptionsForEngagement(TenantContext.getTenantIdAsObject(), id)));
    }
}
