package za.co.handyflow.platform.projects.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.projects.application.internal.ProjectService;
import za.co.handyflow.platform.projects.domain.model.Project;
import za.co.handyflow.platform.projects.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Projects", description = "Project register, lifecycle and team management")
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Dashboard KPIs — active count, health breakdown, pending approvals")
    public ResponseEntity<ApiResponse<ProjectSummaryResponse>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success("Success",
                projectService.getSummary(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "List projects — optionally filter by status (PLANNING/ACTIVE/ON_HOLD/COMPLETED/CANCELLED)")
    public ResponseEntity<ApiResponse<Page<ProjectResponse>>> getProjects(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<Project> page = projectService.getProjects(TenantContext.getTenantIdAsObject(), status, pageable);
        Page<ProjectResponse> response = page.map(p -> {
            List<?> tasks = projectService.getTasks(TenantContext.getTenantIdAsObject(), p.getId());
            long completed = tasks.stream()
                    .filter(t -> t instanceof za.co.handyflow.platform.projects.domain.model.ProjectTask pt
                            && "COMPLETED".equals(pt.getStatus()))
                    .count();
            int risks = projectService.getRisks(TenantContext.getTenantIdAsObject(), p.getId()).stream()
                    .filter(r -> "OPEN".equals(r.getStatus())).toList().size();
            return ProjectResponse.of(p, tasks.size(), (int) completed, risks);
        });
        return ResponseEntity.ok(ApiResponse.success("Success", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PM_READ')")
    public ResponseEntity<ApiResponse<ProjectResponse>> getProject(@PathVariable UUID id) {
        Project p = projectService.getProject(TenantContext.getTenantIdAsObject(), id);
        List<?> tasks = projectService.getTasks(TenantContext.getTenantIdAsObject(), id);
        long completed = tasks.stream()
                .filter(t -> t instanceof za.co.handyflow.platform.projects.domain.model.ProjectTask pt
                        && "COMPLETED".equals(pt.getStatus()))
                .count();
        int risks = projectService.getRisks(TenantContext.getTenantIdAsObject(), id).stream()
                .filter(r -> "OPEN".equals(r.getStatus())).toList().size();
        return ResponseEntity.ok(ApiResponse.success("Success",
                ProjectResponse.of(p, tasks.size(), (int) completed, risks)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Create a project — auto-assigns PRJ number, generates portal token")
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
            @RequestBody CreateProjectRequest req) {
        UUID userId = TenantContext.getCurrentUserId();
        Project p = projectService.createProject(TenantContext.getTenantIdAsObject(), req, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Project created",
                        ProjectResponse.of(p, 0, 0, 0)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    public ResponseEntity<ApiResponse<ProjectResponse>> updateProject(
            @PathVariable UUID id, @RequestBody UpdateProjectRequest req) {
        Project p = projectService.updateProject(TenantContext.getTenantIdAsObject(), id, req);
        return ResponseEntity.ok(ApiResponse.success("Project updated",
                ProjectResponse.of(p, 0, 0, 0)));
    }

    // ── Lifecycle actions ─────────────────────────────────────────────────────

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Activate project (PLANNING or ON_HOLD → ACTIVE)")
    public ResponseEntity<ApiResponse<ProjectResponse>> activate(@PathVariable UUID id) {
        Project p = projectService.updateProjectStatus(TenantContext.getTenantIdAsObject(), id, "ACTIVATE", null);
        return ResponseEntity.ok(ApiResponse.success("Project activated", ProjectResponse.of(p, 0, 0, 0)));
    }

    @PostMapping("/{id}/hold")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Put project on hold (ACTIVE → ON_HOLD)")
    public ResponseEntity<ApiResponse<ProjectResponse>> hold(@PathVariable UUID id) {
        Project p = projectService.updateProjectStatus(TenantContext.getTenantIdAsObject(), id, "HOLD", null);
        return ResponseEntity.ok(ApiResponse.success("Project on hold", ProjectResponse.of(p, 0, 0, 0)));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "Complete project (ACTIVE → COMPLETED)")
    public ResponseEntity<ApiResponse<ProjectResponse>> complete(@PathVariable UUID id) {
        Project p = projectService.updateProjectStatus(TenantContext.getTenantIdAsObject(), id, "COMPLETE", null);
        return ResponseEntity.ok(ApiResponse.success("Project completed", ProjectResponse.of(p, 0, 0, 0)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "Cancel project — provide reason in request body")
    public ResponseEntity<ApiResponse<ProjectResponse>> cancel(
            @PathVariable UUID id, @RequestBody Map<String, String> body) {
        Project p = projectService.updateProjectStatus(TenantContext.getTenantIdAsObject(), id,
                "CANCEL", body.get("reason"));
        return ResponseEntity.ok(ApiResponse.success("Project cancelled", ProjectResponse.of(p, 0, 0, 0)));
    }

    // ── Milestones (convenience endpoint) ────────────────────────────────────

    @GetMapping("/{id}/milestones")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Get milestone tasks for a project — used by client portal and Gantt")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getMilestones(@PathVariable UUID id) {
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
    public ResponseEntity<ApiResponse<TimeEntryResponse>> approveTime(@PathVariable UUID entryId) {
        UUID approverId = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("Approved",
                TimeEntryResponse.of(projectService.approveTime(
                        TenantContext.getTenantIdAsObject(), entryId, approverId, true))));
    }

    @PostMapping("/time-entries/{entryId}/reject")
    @PreAuthorize("hasAuthority('PM_APPROVE')")
    public ResponseEntity<ApiResponse<TimeEntryResponse>> rejectTime(@PathVariable UUID entryId) {
        return ResponseEntity.ok(ApiResponse.success("Rejected",
                TimeEntryResponse.of(projectService.approveTime(
                        TenantContext.getTenantIdAsObject(), entryId, null, false))));
    }
}
