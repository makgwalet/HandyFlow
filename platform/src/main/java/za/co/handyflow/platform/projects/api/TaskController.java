package za.co.handyflow.platform.projects.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.projects.application.internal.PhaseService;
import za.co.handyflow.platform.projects.application.internal.ProjectAccessGuard;
import za.co.handyflow.platform.projects.application.internal.ProjectService;
import za.co.handyflow.platform.projects.domain.model.ProjectTask;
import za.co.handyflow.platform.projects.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tasks, task status transitions, phases, and time-entry logging.
 *
 * CHUNK 2b: task responses omit budgetAmount / actualCost for project roles without
 * cost visibility (see ProjectMemberRole). Everything else is unchanged.
 * @Validated + @Valid make Bean Validation on request bodies actually run.
 */
@Validated
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Tasks & Phases", description = "WBS tasks, milestones, phase management and time tracking")
public class TaskController {

    private final ProjectService      projectService;
    private final PhaseService        phaseService;
    private final ProjectAccessGuard  accessGuard;

    // ── Tasks ─────────────────────────────────────────────────────────────────

    @GetMapping("/{projectId}/tasks")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "All tasks for a project — ordered by sort_order then planned_start")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getTasks(@PathVariable UUID projectId) {
        boolean cost = canViewCost(projectId);
        return ResponseEntity.ok(ApiResponse.success("Success",
                projectService.getTasks(TenantContext.getTenantIdAsObject(), projectId)
                        .stream().map(t -> view(t, cost)).toList()));
    }

    @PostMapping("/{projectId}/tasks")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Create a task — auto-assigns task number (T001, T002…)")
    public ResponseEntity<ApiResponse<TaskResponse>> createTask(
            @PathVariable UUID projectId, @Valid @RequestBody CreateTaskRequest req) {
        UUID userId = TenantContext.getCurrentUserId();
        ProjectTask t = projectService.createTask(
                TenantContext.getTenantIdAsObject(), projectId, req, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Task created", view(t, canViewCost(projectId))));
    }

    /**
     * Supports START, COMPLETE, BLOCK, CANCEL, PROGRESS (with progressPct body).
     * PROGRESS drives status: 0% = NOT_STARTED, 1–99% = IN_PROGRESS, 100% = COMPLETED.
     */
    @PostMapping("/tasks/{taskId}/status")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Transition task status: START | REVIEW | RETURN | COMPLETE | BLOCK | CANCEL | PROGRESS")
    public ResponseEntity<ApiResponse<TaskResponse>> updateStatus(
            @PathVariable UUID taskId, @RequestBody Map<String, Object> body) {
        String action = (String) body.get("action");
        BigDecimal pct = body.get("progressPct") instanceof Number n
                ? BigDecimal.valueOf(n.doubleValue()) : null;
        ProjectTask t = projectService.updateTaskStatus(
                TenantContext.getTenantIdAsObject(), taskId, action, pct);
        return ResponseEntity.ok(ApiResponse.success("Status updated", view(t, canViewCost(t.getProjectId()))));
    }

    @PutMapping("/tasks/{taskId}")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Edit a task's title, priority, assignee (a project member), estimate or notes. Dates: PUT /tasks/{id}/dates")
    public ResponseEntity<ApiResponse<TaskResponse>> updateTask(
            @PathVariable UUID taskId, @Valid @RequestBody UpdateTaskDetailsRequest req) {
        ProjectTask t = projectService.updateTaskDetails(TenantContext.getTenantIdAsObject(), taskId, req);
        return ResponseEntity.ok(ApiResponse.success("Task updated", view(t, canViewCost(t.getProjectId()))));
    }

    // ── Time entries ──────────────────────────────────────────────────────────

    @GetMapping("/{projectId}/time-entries")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Time entries logged against a project")
    public ResponseEntity<ApiResponse<List<TimeEntryResponse>>> getTimeEntries(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                projectService.getTimeEntries(TenantContext.getTenantIdAsObject(), projectId)
                        .stream().map(TimeEntryResponse::of).toList()));
    }

    @PostMapping("/{projectId}/time-entries")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Log time against a project or task — updates task.actual_hours")
    public ResponseEntity<ApiResponse<TimeEntryResponse>> logTime(
            @PathVariable UUID projectId, @Valid @RequestBody LogTimeRequest req) {
        UUID   userId   = TenantContext.getCurrentUserId();
        String userName = TenantContext.getCurrentUserName();   // real name, not UUID
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Time logged",
                        TimeEntryResponse.of(projectService.logTime(
                                TenantContext.getTenantIdAsObject(), projectId, req, userId, userName))));
    }

    // ── Phases ────────────────────────────────────────────────────────────────

    @GetMapping("/{projectId}/phases")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Phases for a project — ordered by sort_order")
    public ResponseEntity<ApiResponse<List<PhaseResponse>>> getPhases(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                phaseService.getPhases(TenantContext.getTenantIdAsObject(), projectId)
                        .stream().map(PhaseResponse::of).toList()));
    }

    @PostMapping("/{projectId}/phases")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "Create a phase — sort_order auto-assigned if not provided")
    public ResponseEntity<ApiResponse<PhaseResponse>> createPhase(
            @PathVariable UUID projectId, @Valid @RequestBody CreatePhaseRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Phase created",
                        PhaseResponse.of(phaseService.createPhase(
                                TenantContext.getTenantIdAsObject(), projectId, req))));
    }

    @PutMapping("/phases/{phaseId}")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "Update phase name, dates or sort order")
    public ResponseEntity<ApiResponse<PhaseResponse>> updatePhase(
            @PathVariable UUID phaseId, @Valid @RequestBody CreatePhaseRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Phase updated",
                PhaseResponse.of(phaseService.updatePhase(
                        TenantContext.getTenantIdAsObject(), phaseId, req))));
    }

    @PostMapping("/phases/{phaseId}/status")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Transition phase status: START | COMPLETE | SKIP")
    public ResponseEntity<ApiResponse<PhaseResponse>> updatePhaseStatus(
            @PathVariable UUID phaseId, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.success("Phase status updated",
                PhaseResponse.of(phaseService.updatePhaseStatus(
                        TenantContext.getTenantIdAsObject(), phaseId, body.get("action")))));
    }

    @DeleteMapping("/phases/{phaseId}")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "Delete a phase — tasks are unlinked (phase_id set to null)")
    public ResponseEntity<ApiResponse<Void>> deletePhase(@PathVariable UUID phaseId) {
        phaseService.deletePhase(TenantContext.getTenantIdAsObject(), phaseId);
        return ResponseEntity.ok(ApiResponse.success("Phase deleted", null));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean canViewCost(UUID projectId) {
        return accessGuard.accessFor(TenantContext.getTenantIdAsObject().getValue(), projectId).cost();
    }

    private static TaskResponse view(ProjectTask t, boolean cost) {
        TaskResponse r = TaskResponse.of(t);
        return cost ? r : r.withoutCost();
    }
}