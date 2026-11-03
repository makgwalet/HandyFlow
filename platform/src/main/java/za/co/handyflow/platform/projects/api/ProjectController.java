package za.co.handyflow.platform.projects.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.projects.application.internal.ProjectAccess;
import za.co.handyflow.platform.projects.application.internal.ProjectAccessGuard;
import za.co.handyflow.platform.projects.application.internal.ProjectLifecycleService;
import za.co.handyflow.platform.projects.application.internal.ProjectPortfolioService;
import za.co.handyflow.platform.projects.application.internal.ProjectService;
import za.co.handyflow.platform.projects.domain.enums.ProjectAction;
import za.co.handyflow.platform.projects.domain.model.Project;
import za.co.handyflow.platform.projects.domain.repository.projections.ProjectStatsSummary;
import za.co.handyflow.platform.projects.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Project CRUD, lifecycle, KPI summary, milestones and time-entry approvals.
 *
 * CHUNK 1 CHANGES
 * ───────────────
 * - Every lifecycle endpoint delegates to ProjectLifecycleService.transition(),
 *   which validates, audits and saves in one transaction.
 * - New endpoints: /propose, /approve, /return, /reopen, /close,
 *   /closure-readiness, /status-history.
 * - /cancel now takes a typed body (same JSON: { "reason": "..." }) and the
 *   reason is mandatory.
 * - Every ProjectResponse carries availableActions so the UI can render the
 *   right buttons without duplicating the state machine.
 *
 * PERMISSIONS (existing authorities, no new ones needed yet):
 *   propose / activate / hold ........ PM_WRITE
 *   approve / return ................. PM_APPROVE
 *   complete / reopen / close / cancel PM_ADMIN
 */
@Validated
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Projects", description = "Project register, lifecycle and team management")
public class ProjectController {

    private final ProjectService          projectService;
    private final ProjectLifecycleService lifecycleService;
    private final ProjectAccessGuard      accessGuard;
    private final ProjectPortfolioService portfolioService;

    // ── Dashboard ─────────────────────────────────────────────────────────────

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Dashboard KPIs — active count, health breakdown, pending approvals")
    public ResponseEntity<ApiResponse<ProjectSummaryResponse>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success("Success",
                projectService.getSummary(TenantContext.getTenantIdAsObject())));
    }

    // ── Project list ──────────────────────────────────────────────────────────

    @GetMapping("/portfolio")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Dashboard portfolio: counts, overdue, budget and spend (cost-visible projects), last month's figures, trend, upcoming milestones")
    public ResponseEntity<ApiResponse<PortfolioResponse>> portfolio() {
        return ResponseEntity.ok(ApiResponse.success("Success", portfolioService.portfolio(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "List projects — optionally filter by status")
    public ResponseEntity<ApiResponse<Page<ProjectResponse>>> getProjects(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {

        Page<Project> page = projectService.getProjects(
                TenantContext.getTenantIdAsObject(), status, pageable);

        List<UUID> ids = page.getContent().stream().map(Project::getId).toList();
        Map<UUID, ProjectStatsSummary> statsMap = projectService.getProjectStats(ids);

        // CHUNK 2b: one membership query for the whole page; cost fields masked per project.
        Map<UUID, ProjectAccess> access = accessGuard.accessFor(
                TenantContext.getTenantIdAsObject().getValue(), ids);

        Page<ProjectResponse> response = page.map(p ->
                toResponse(p, statsMap.getOrDefault(p.getId(), emptyStats()),
                        access.getOrDefault(p.getId(), ProjectAccess.NONE).cost()));
        return ResponseEntity.ok(ApiResponse.success("Success", response));
    }

    // ── Single project ────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Get a single project with task and risk counts")
    public ResponseEntity<ApiResponse<ProjectResponse>> getProject(@PathVariable UUID id) {
        Project p = projectService.getProject(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Success",
                toResponse(p, projectService.getProjectStats(id))));
    }

    // ── Create / update ───────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Create a project — auto-assigns PRJ number, generates portal token")
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
            @Valid @RequestBody CreateProjectRequest req) {
        UUID userId = TenantContext.getCurrentUserId();
        Project p = projectService.createProject(
                TenantContext.getTenantIdAsObject(), req, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Project created", toResponse(p, emptyStats())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Update project details (blocked once COMPLETED / CLOSED / CANCELLED)")
    public ResponseEntity<ApiResponse<ProjectResponse>> updateProject(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProjectRequest req) {
        Project p = projectService.updateProject(
                TenantContext.getTenantIdAsObject(), id, req);
        return ResponseEntity.ok(ApiResponse.success("Project updated",
                toResponse(p, projectService.getProjectStats(id))));
    }

    // ── Lifecycle actions ─────────────────────────────────────────────────────

    @PostMapping("/{id}/propose")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Submit project for approval (PLANNING → PROPOSED). Requires start and end dates.")
    public ResponseEntity<ApiResponse<ProjectResponse>> propose(
            @PathVariable UUID id,
            @RequestBody(required = false) @Valid ProjectTransitionRequest req) {
        return act(id, ProjectAction.PROPOSE, "Project proposed for approval", req);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('PM_APPROVE')")
    @Operation(summary = "Approve project (PROPOSED → APPROVED) — locks the schedule baseline")
    public ResponseEntity<ApiResponse<ProjectResponse>> approve(
            @PathVariable UUID id,
            @RequestBody(required = false) @Valid ProjectTransitionRequest req) {
        return act(id, ProjectAction.APPROVE, "Project approved", req);
    }

    @PostMapping("/{id}/return")
    @PreAuthorize("hasAuthority('PM_APPROVE')")
    @Operation(summary = "Send a proposed project back to planning — reason required")
    public ResponseEntity<ApiResponse<ProjectResponse>> returnToPlanning(
            @PathVariable UUID id,
            @RequestBody(required = false) @Valid ProjectTransitionRequest req) {
        return act(id, ProjectAction.RETURN, "Project returned to planning", req);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Activate project (APPROVED or ON_HOLD → ACTIVE; PLANNING → ACTIVE unless approval gate is on)")
    public ResponseEntity<ApiResponse<ProjectResponse>> activate(
            @PathVariable UUID id,
            @RequestBody(required = false) @Valid ProjectTransitionRequest req) {
        return act(id, ProjectAction.ACTIVATE, "Project activated", req);
    }

    @PostMapping("/{id}/hold")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Put project on hold (ACTIVE → ON_HOLD) — optional reason is recorded")
    public ResponseEntity<ApiResponse<ProjectResponse>> hold(
            @PathVariable UUID id,
            @RequestBody(required = false) @Valid ProjectTransitionRequest req) {
        return act(id, ProjectAction.HOLD, "Project on hold", req);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "Complete project (ACTIVE → COMPLETED)")
    public ResponseEntity<ApiResponse<ProjectResponse>> complete(
            @PathVariable UUID id,
            @RequestBody(required = false) @Valid ProjectTransitionRequest req) {
        return act(id, ProjectAction.COMPLETE, "Project completed", req);
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "Reopen a completed project (COMPLETED → ACTIVE) — reason required")
    public ResponseEntity<ApiResponse<ProjectResponse>> reopen(
            @PathVariable UUID id,
            @RequestBody(required = false) @Valid ProjectTransitionRequest req) {
        return act(id, ProjectAction.REOPEN, "Project reopened", req);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "Formally close a completed project — lessons learned required; blocked while closure checks fail")
    public ResponseEntity<ApiResponse<ProjectResponse>> close(
            @PathVariable UUID id,
            @RequestBody @Valid ProjectTransitionRequest req) {
        return act(id, ProjectAction.CLOSE, "Project closed", req);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "Cancel project — body { \"reason\": \"...\" } is required")
    public ResponseEntity<ApiResponse<ProjectResponse>> cancel(
            @PathVariable UUID id,
            @RequestBody @Valid ProjectTransitionRequest req) {
        return act(id, ProjectAction.CANCEL, "Project cancelled", req);
    }

    // ── Governance read endpoints ─────────────────────────────────────────────

    @GetMapping("/{id}/closure-readiness")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Closure checklist — what still blocks (or warns about) closing this project")
    public ResponseEntity<ApiResponse<ClosureReadinessResponse>> closureReadiness(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                lifecycleService.getClosureReadiness(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/{id}/status-history")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Audit trail of every status change — newest first")
    public ResponseEntity<ApiResponse<List<StatusHistoryResponse>>> statusHistory(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                lifecycleService.getHistory(TenantContext.getTenantIdAsObject(), id)
                        .stream().map(StatusHistoryResponse::of).toList()));
    }

    // ── Milestones ────────────────────────────────────────────────────────────

    @GetMapping("/{id}/milestones")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Get milestone tasks — used by client portal and Gantt")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getMilestones(@PathVariable UUID id) {
        boolean cost = accessGuard.accessFor(TenantContext.getTenantIdAsObject().getValue(), id).cost();
        return ResponseEntity.ok(ApiResponse.success("Success",
                projectService.getMilestones(TenantContext.getTenantIdAsObject(), id)
                        .stream()
                        .map(t -> cost ? TaskResponse.of(t) : TaskResponse.of(t).withoutCost())
                        .toList()));
    }

    // ── Time entry approvals ──────────────────────────────────────────────────

    @GetMapping("/time-approvals")
    @PreAuthorize("hasAuthority('PM_APPROVE')")
    @Operation(summary = "All pending time entries awaiting approval across all projects")
    public ResponseEntity<ApiResponse<List<TimeEntryResponse>>> getPendingApprovals() {
        return ResponseEntity.ok(ApiResponse.success("Success",
                projectService.getPendingTimeApprovals(TenantContext.getTenantIdAsObject())
                        .stream().map(TimeEntryResponse::of).toList()));
    }

    @PostMapping("/time-entries/{entryId}/approve")
    @PreAuthorize("hasAuthority('PM_APPROVE')")
    @Operation(summary = "Approve a submitted time entry")
    public ResponseEntity<ApiResponse<TimeEntryResponse>> approveTime(@PathVariable UUID entryId) {
        UUID approverId = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("Approved",
                TimeEntryResponse.of(projectService.approveTime(
                        TenantContext.getTenantIdAsObject(), entryId, approverId, true))));
    }

    @PostMapping("/time-entries/{entryId}/reject")
    @PreAuthorize("hasAuthority('PM_APPROVE')")
    @Operation(summary = "Reject a submitted time entry")
    public ResponseEntity<ApiResponse<TimeEntryResponse>> rejectTime(@PathVariable UUID entryId) {
        return ResponseEntity.ok(ApiResponse.success("Rejected",
                TimeEntryResponse.of(projectService.approveTime(
                        TenantContext.getTenantIdAsObject(), entryId, TenantContext.getCurrentUserId(), false))));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ResponseEntity<ApiResponse<ProjectResponse>> act(
            UUID id, ProjectAction action, String message, ProjectTransitionRequest req) {
        Project p = lifecycleService.transition(
                TenantContext.getTenantIdAsObject(), id, action, req,
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.ok(ApiResponse.success(message,
                toResponse(p, projectService.getProjectStats(id))));
    }

    /** Single-project variant: looks up the caller's access itself. */
    private ProjectResponse toResponse(Project p, ProjectStatsSummary s) {
        return toResponse(p, s, accessGuard.accessFor(p.getTenantId(), p.getId()).cost());
    }

    private ProjectResponse toResponse(Project p, ProjectStatsSummary s, boolean canViewCost) {
        ProjectResponse r = ProjectResponse.of(p,
                (int) s.getTaskCount(),
                (int) s.getCompletedTaskCount(),
                (int) s.getOpenRiskCount(),
                lifecycleService.availableActions(p));
        return canViewCost ? r : r.withoutSensitive();
    }

    private ProjectStatsSummary emptyStats() {
        return new ProjectStatsSummary() {
            @Override public UUID getProjectId()          { return null; }
            @Override public long getTaskCount()          { return 0; }
            @Override public long getCompletedTaskCount() { return 0; }
            @Override public long getOpenRiskCount()      { return 0; }
        };
    }
}