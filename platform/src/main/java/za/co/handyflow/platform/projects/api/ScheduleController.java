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
import za.co.handyflow.platform.projects.application.internal.DependencyService;
import za.co.handyflow.platform.projects.application.internal.ScheduleService;
import za.co.handyflow.platform.projects.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Scheduling: dependencies, schedule analysis, safe date changes.
 *
 * No new authorities: reads need PM_READ, changes need PM_WRITE. Project access
 * (membership, restricted mode) is enforced by ProjectAccessInterceptor, which already
 * covers /{projectId}/... and /tasks/{taskId}/... routes — nothing to register for these paths.
 */
@Validated
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Scheduling", description = "Task dependencies, critical path, baseline vs forecast")
public class ScheduleController {

    private final ScheduleService   scheduleService;
    private final DependencyService dependencyService;

    // ── Analysis ──────────────────────────────────────────────────────────────

    @GetMapping("/{projectId}/schedule")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Schedule analysis — critical path, float, forecast vs baseline, overdue tasks, broken dependencies")
    public ResponseEntity<ApiResponse<ScheduleReportResponse>> schedule(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                scheduleService.getSchedule(TenantContext.getTenantIdAsObject(), projectId)));
    }

    @PostMapping("/{projectId}/schedule/recalculate")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Recalculate the critical path and save the critical flag on each task")
    public ResponseEntity<ApiResponse<ScheduleReportResponse>> recalculate(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Schedule recalculated",
                scheduleService.recalculate(TenantContext.getTenantIdAsObject(), projectId)));
    }

    // ── Dependencies ──────────────────────────────────────────────────────────

    @GetMapping("/{projectId}/dependencies")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "All task dependencies in the project")
    public ResponseEntity<ApiResponse<List<DependencyResponse>>> dependencies(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                dependencyService.list(TenantContext.getTenantIdAsObject(), projectId)));
    }

    @PostMapping("/{projectId}/dependencies")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Link two tasks (FS/SS/FF/SF + lag). Cycles are rejected (409); date conflicts come back as warnings.")
    public ResponseEntity<ApiResponse<DependencyCreatedResponse>> addDependency(
            @PathVariable UUID projectId, @Valid @RequestBody CreateDependencyRequest req) {
        DependencyCreatedResponse created = dependencyService.add(
                TenantContext.getTenantIdAsObject(), projectId, req);
        String message = created.warnings().isEmpty()
                ? "Dependency added"
                : "Dependency added — " + created.warnings().size() + " date conflict(s)";
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(message, created));
    }

    @DeleteMapping("/{projectId}/dependencies/{dependencyId}")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Remove a task dependency")
    public ResponseEntity<ApiResponse<Void>> removeDependency(
            @PathVariable UUID projectId, @PathVariable UUID dependencyId) {
        dependencyService.remove(TenantContext.getTenantIdAsObject(), projectId, dependencyId);
        return ResponseEntity.ok(ApiResponse.success("Dependency removed", null));
    }

    // ── Date change ───────────────────────────────────────────────────────────

    @PutMapping("/tasks/{taskId}/dates")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Change a task's planned dates. shiftDependents=true pushes later tasks out; "
            + "the response always lists dependencies still broken afterwards.")
    public ResponseEntity<ApiResponse<TaskDatesChangeResponse>> updateDates(
            @PathVariable UUID taskId, @Valid @RequestBody UpdateTaskDatesRequest req) {
        TaskDatesChangeResponse result = scheduleService.updateTaskDates(
                TenantContext.getTenantIdAsObject(), taskId, req);
        String message = result.remainingViolations().isEmpty()
                ? "Dates updated"
                : "Dates updated — " + result.remainingViolations().size() + " dependency conflict(s) remain";
        return ResponseEntity.ok(ApiResponse.success(message, result));
    }
}