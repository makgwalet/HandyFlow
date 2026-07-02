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
import za.co.handyflow.platform.projects.application.internal.ProjectService;
import za.co.handyflow.platform.projects.domain.model.Project;
import za.co.handyflow.platform.projects.domain.repository.projections.ProjectStatsSummary;
import za.co.handyflow.platform.projects.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Project lifecycle, KPI summary, milestones and time-entry approvals.
 *
 * CHANGES FROM ORIGINAL
 * ─────────────────────
 * 1. N+1 QUERY FIXED in getProjects():
 *    Original: for each of 20 projects → getTasks() + getRisks() = 40 extra DB calls.
 *    Fixed:    one call to getProjectStats(ids) fetches all counts in one SQL.
 *
 * 2. @Validated on class + @Valid on every @RequestBody:
 *    Without these annotations, Bean Validation constraints on DTO fields are
 *    completely ignored.  ConstraintViolationException is handled by the
 *    global exception handler → HTTP 400 with field error details.
 *
 * 3. updateProject() now returns REAL task/risk counts:
 *    Original returned ProjectResponse.of(p, 0, 0, 0) after every update — the
 *    frontend would show "0 tasks, 0 risks" immediately after saving.
 *
 * 4. Removed unnecessary instanceof pattern-match with fully-qualified class names.
 *    getTasks() already returns List<ProjectTask> — casting was redundant.
 */
@Validated                // enables Bean Validation on method parameters
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Projects", description = "Project register, lifecycle and team management")
public class ProjectController {

    private final ProjectService projectService;

    // ── Dashboard ─────────────────────────────────────────────────────────────

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Dashboard KPIs — active count, health breakdown, pending approvals")
    public ResponseEntity<ApiResponse<ProjectSummaryResponse>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success("Success",
                projectService.getSummary(TenantContext.getTenantIdAsObject())));
    }

    // ── Project list ──────────────────────────────────────────────────────────

    /**
     * Lists projects with their task/risk counts resolved in ONE extra DB call
     * (instead of 2 × N calls in the original).
     *
     * HOW IT WORKS:
     * 1. Fetch page of Projects (1 query)
     * 2. Collect all IDs → call getProjectStats(ids) (1 native aggregate query)
     * 3. Map each Project to ProjectResponse, looking up stats by ID in O(1)
     *
     * Total: 2 queries regardless of page size.
     * Original: 2N + 1 queries (e.g. 41 for page size 20).
     */
    @GetMapping
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "List projects — optionally filter by status")
    public ResponseEntity<ApiResponse<Page<ProjectResponse>>> getProjects(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {

        Page<Project> page = projectService.getProjects(
                TenantContext.getTenantIdAsObject(), status, pageable);

        // --- N+1 FIX: single batch stats query for all projects in the page ---
        List<UUID> ids = page.getContent().stream().map(Project::getId).toList();
        Map<UUID, ProjectStatsSummary> statsMap = projectService.getProjectStats(ids);
        // ---------------------------------------------------------------------

        Page<ProjectResponse> response = page.map(p -> {
            ProjectStatsSummary s = statsMap.getOrDefault(p.getId(), emptyStats());
            return ProjectResponse.of(p,
                    (int) s.getTaskCount(),
                    (int) s.getCompletedTaskCount(),
                    (int) s.getOpenRiskCount());
        });
        return ResponseEntity.ok(ApiResponse.success("Success", response));
    }

    // ── Single project ────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Get a single project with task and risk counts")
    public ResponseEntity<ApiResponse<ProjectResponse>> getProject(@PathVariable UUID id) {
        Project p = projectService.getProject(TenantContext.getTenantIdAsObject(), id);
        ProjectStatsSummary s = projectService.getProjectStats(id);
        return ResponseEntity.ok(ApiResponse.success("Success",
                ProjectResponse.of(p,
                        (int) s.getTaskCount(),
                        (int) s.getCompletedTaskCount(),
                        (int) s.getOpenRiskCount())));
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Create a project — auto-assigns PRJ number, generates portal token")
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
            @Valid @RequestBody CreateProjectRequest req) {    // @Valid added
        UUID userId = TenantContext.getCurrentUserId();
        Project p = projectService.createProject(
                TenantContext.getTenantIdAsObject(), req, userId);
        // New project — counts are all zero by definition
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Project created",
                        ProjectResponse.of(p, 0, 0, 0)));
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Update project details")
    public ResponseEntity<ApiResponse<ProjectResponse>> updateProject(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProjectRequest req) {    // @Valid added
        Project p = projectService.updateProject(
                TenantContext.getTenantIdAsObject(), id, req);

        /*
         * FIX: Original returned ProjectResponse.of(p, 0, 0, 0) — always zeros.
         * The frontend would show "0 tasks, 0 risks" after every save.
         * Now we fetch the real counts with one extra query.
         */
        ProjectStatsSummary s = projectService.getProjectStats(id);
        return ResponseEntity.ok(ApiResponse.success("Project updated",
                ProjectResponse.of(p,
                        (int) s.getTaskCount(),
                        (int) s.getCompletedTaskCount(),
                        (int) s.getOpenRiskCount())));
    }

    // ── Lifecycle actions ─────────────────────────────────────────────────────

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Activate project (PLANNING or ON_HOLD → ACTIVE)")
    public ResponseEntity<ApiResponse<ProjectResponse>> activate(@PathVariable UUID id) {
        Project p = projectService.updateProjectStatus(
                TenantContext.getTenantIdAsObject(), id, "ACTIVATE", null);
        return ok("Project activated", p, id);
    }

    @PostMapping("/{id}/hold")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Put project on hold (ACTIVE → ON_HOLD)")
    public ResponseEntity<ApiResponse<ProjectResponse>> hold(@PathVariable UUID id) {
        Project p = projectService.updateProjectStatus(
                TenantContext.getTenantIdAsObject(), id, "HOLD", null);
        return ok("Project on hold", p, id);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "Complete project (ACTIVE → COMPLETED)")
    public ResponseEntity<ApiResponse<ProjectResponse>> complete(@PathVariable UUID id) {
        Project p = projectService.updateProjectStatus(
                TenantContext.getTenantIdAsObject(), id, "COMPLETE", null);
        return ok("Project completed", p, id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "Cancel project — provide reason in body { \"reason\": \"...\" }")
    public ResponseEntity<ApiResponse<ProjectResponse>> cancel(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        Project p = projectService.updateProjectStatus(
                TenantContext.getTenantIdAsObject(), id, "CANCEL", body.get("reason"));
        return ok("Project cancelled", p, id);
    }

    // ── Milestones ────────────────────────────────────────────────────────────

    @GetMapping("/{id}/milestones")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Get milestone tasks — used by client portal and Gantt")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getMilestones(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                projectService.getMilestones(TenantContext.getTenantIdAsObject(), id)
                        .stream().map(TaskResponse::of).toList()));
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
    public ResponseEntity<ApiResponse<TimeEntryResponse>> approveTime(
            @PathVariable UUID entryId) {
        UUID approverId = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("Approved",
                TimeEntryResponse.of(projectService.approveTime(
                        TenantContext.getTenantIdAsObject(), entryId, approverId, true))));
    }

    @PostMapping("/time-entries/{entryId}/reject")
    @PreAuthorize("hasAuthority('PM_APPROVE')")
    @Operation(summary = "Reject a submitted time entry")
    public ResponseEntity<ApiResponse<TimeEntryResponse>> rejectTime(
            @PathVariable UUID entryId) {
        return ResponseEntity.ok(ApiResponse.success("Rejected",
                TimeEntryResponse.of(projectService.approveTime(
                        TenantContext.getTenantIdAsObject(), entryId, null, false))));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Convenience: build an OK response with real task/risk counts.
     * Used by all lifecycle-action endpoints to avoid duplicating the stats lookup.
     */
    private ResponseEntity<ApiResponse<ProjectResponse>> ok(
            String message, Project p, UUID projectId) {
        ProjectStatsSummary s = projectService.getProjectStats(projectId);
        return ResponseEntity.ok(ApiResponse.success(message,
                ProjectResponse.of(p,
                        (int) s.getTaskCount(),
                        (int) s.getCompletedTaskCount(),
                        (int) s.getOpenRiskCount())));
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
