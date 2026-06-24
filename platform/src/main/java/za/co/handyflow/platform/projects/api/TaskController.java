package za.co.handyflow.platform.projects.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.projects.application.internal.PhaseService;
import za.co.handyflow.platform.projects.application.internal.ProjectService;
import za.co.handyflow.platform.projects.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Tasks & Phases", description = "WBS tasks, phases, dependencies, progress")
public class TaskController {

    private final ProjectService projectService;
    private final PhaseService   phaseService;

    // ── Phases ────────────────────────────────────────────────────────────────

    @GetMapping("/{projectId}/phases")
    @PreAuthorize("hasAuthority('PM_READ')")
    public ResponseEntity<ApiResponse<List<PhaseResponse>>> getPhases(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                phaseService.getPhases(TenantContext.getTenantIdAsObject(), projectId)
                        .stream().map(PhaseResponse::of).toList()));
    }

    @PostMapping("/{projectId}/phases")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Add a phase to a project — phases define major schedule blocks")
    public ResponseEntity<ApiResponse<PhaseResponse>> createPhase(
            @PathVariable UUID projectId, @RequestBody CreatePhaseRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Phase created",
                PhaseResponse.of(phaseService.createPhase(
                        TenantContext.getTenantIdAsObject(), projectId, req))));
    }

    @PostMapping("/phases/{phaseId}/{action}")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Phase lifecycle: action = start | complete | skip")
    public ResponseEntity<ApiResponse<PhaseResponse>> updatePhaseStatus(
            @PathVariable UUID phaseId, @PathVariable String action) {
        return ResponseEntity.ok(ApiResponse.success("Phase updated",
                PhaseResponse.of(phaseService.updatePhaseStatus(
                        TenantContext.getTenantIdAsObject(), phaseId, action))));
    }

    // ── Tasks ─────────────────────────────────────────────────────────────────

    @GetMapping("/{projectId}/tasks")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "All tasks for a project — flat list, ordered by sort_order")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getTasks(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                projectService.getTasks(TenantContext.getTenantIdAsObject(), projectId)
                        .stream().map(TaskResponse::of).toList()));
    }

    @PostMapping("/{projectId}/tasks")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Create a task — auto-assigns T-number, links to phase if provided")
    public ResponseEntity<ApiResponse<TaskResponse>> createTask(
            @PathVariable UUID projectId, @RequestBody CreateTaskRequest req) {
        UUID userId = TenantContext.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Task created",
                TaskResponse.of(projectService.createTask(
                        TenantContext.getTenantIdAsObject(), projectId, req, userId))));
    }

    @PostMapping("/tasks/{taskId}/status")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Update task status — action: START | COMPLETE | PROGRESS | BLOCK | CANCEL")
    public ResponseEntity<ApiResponse<TaskResponse>> updateTaskStatus(
            @PathVariable UUID taskId, @RequestBody TaskStatusRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Task updated",
                TaskResponse.of(projectService.updateTaskStatus(
                        TenantContext.getTenantIdAsObject(), taskId,
                        req.action(), req.progressPct()))));
    }

    // ── Time entries ──────────────────────────────────────────────────────────

    @GetMapping("/{projectId}/time-entries")
    @PreAuthorize("hasAuthority('PM_READ')")
    public ResponseEntity<ApiResponse<List<TimeEntryResponse>>> getTimeEntries(
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                projectService.getTimeEntries(TenantContext.getTenantIdAsObject(), projectId)
                        .stream().map(TimeEntryResponse::of).toList()));
    }

    @PostMapping("/{projectId}/time-entries")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Log time against a project/task — GPS coords optional for field teams")
    public ResponseEntity<ApiResponse<TimeEntryResponse>> logTime(
            @PathVariable UUID projectId, @RequestBody LogTimeRequest req) {
        UUID userId   = TenantContext.getCurrentUserId();
        String name   = userId != null ? userId.toString() : "unknown";
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Time logged",
                TimeEntryResponse.of(projectService.logTime(
                        TenantContext.getTenantIdAsObject(), projectId, req, userId, name))));
    }
}
