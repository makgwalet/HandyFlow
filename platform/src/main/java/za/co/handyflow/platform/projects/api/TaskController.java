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
import za.co.handyflow.platform.projects.application.internal.ProjectService;
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
 * CHANGES FROM ORIGINAL
 * ──────────────────────
 * 1. @Validated on class + @Valid on every @RequestBody — Bean Validation
 *    constraints on DTOs are now enforced.  Invalid requests get HTTP 400
 *    with field-level error details instead of silently proceeding to the DB.
 *
 * 2. logTime(): submittedByName was userId.toString() — a UUID.
 *    FIX: TenantContext.getCurrentUserName() provides the real display name,
 *    which is what's shown in the time-entry approval list and payroll reports.
 *
 * 3. Phases are grouped under this controller since they are tightly coupled
 *    to task scheduling (tasks belong to phases).
 *
 * WHAT @Validated DOES AT THE CLASS LEVEL:
 * ─────────────────────────────────────────
 * It tells Spring to proxy the controller and run the JSR-380 constraint
 * validator on every method parameter marked with @Valid.  Without @Validated,
 * the @Valid annotation is present in code but ignored at runtime — a common
 * gotcha in Spring MVC.
 */
@Validated
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Tasks & Phases", description = "WBS tasks, milestones, phase management and time tracking")
public class TaskController {

    private final ProjectService projectService;
    private final PhaseService   phaseService;

    // ── Tasks ─────────────────────────────────────────────────────────────────

    @GetMapping("/{projectId}/tasks")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "All tasks for a project — ordered by sort_order then planned_start")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> getTasks(
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                projectService.getTasks(TenantContext.getTenantIdAsObject(), projectId)
                        .stream().map(TaskResponse::of).toList()));
    }

    @PostMapping("/{projectId}/tasks")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Create a task — auto-assigns task number (T001, T002…)")
    public ResponseEntity<ApiResponse<TaskResponse>> createTask(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateTaskRequest req) {
        UUID userId = TenantContext.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Task created",
                        TaskResponse.of(projectService.createTask(
                                TenantContext.getTenantIdAsObject(), projectId, req, userId))));
    }

    /**
     * Status action endpoint.
     *
     * Supports: START, COMPLETE, BLOCK, CANCEL, PROGRESS (with progressPct body).
     *
     * Sending { "action": "PROGRESS", "progressPct": 65 } updates the task's
     * progress and drives status automatically (0% = NOT_STARTED, 1–99% = IN_PROGRESS,
     * 100% = COMPLETED).
     */
    @PostMapping("/tasks/{taskId}/status")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Transition task status: START | COMPLETE | BLOCK | CANCEL | PROGRESS")
    public ResponseEntity<ApiResponse<TaskResponse>> updateStatus(
            @PathVariable UUID taskId,
            @RequestBody Map<String, Object> body) {
        String action = (String) body.get("action");
        BigDecimal pct = body.get("progressPct") instanceof Number n
                ? BigDecimal.valueOf(n.doubleValue()) : null;
        return ResponseEntity.ok(ApiResponse.success("Status updated",
                TaskResponse.of(projectService.updateTaskStatus(
                        TenantContext.getTenantIdAsObject(), taskId, action, pct))));
    }

    // ── Time entries ──────────────────────────────────────────────────────────

    @GetMapping("/{projectId}/time-entries")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Time entries logged against a project")
    public ResponseEntity<ApiResponse<List<TimeEntryResponse>>> getTimeEntries(
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                projectService.getTimeEntries(TenantContext.getTenantIdAsObject(), projectId)
                        .stream().map(TimeEntryResponse::of).toList()));
    }

    /**
     * Logs hours for the authenticated user.
     *
     * FIX: submittedByName was:  userId.toString()  → "3f4a7b2c-..."
     *      Now it is:            TenantContext.getCurrentUserName() → "Thabo Molefe"
     *
     * The user's name appears in:
     *   - Time entry approval list (ProjectController /time-approvals)
     *   - Payroll exports (when payroll_run_id is set)
     *   - Time entry history visible in project reporting
     */
    @PostMapping("/{projectId}/time-entries")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Log time against a project or task — updates task.actual_hours")
    public ResponseEntity<ApiResponse<TimeEntryResponse>> logTime(
            @PathVariable UUID projectId,
            @Valid @RequestBody LogTimeRequest req) {
        UUID   userId   = TenantContext.getCurrentUserId();
        String userName = TenantContext.getCurrentUserName();  // FIX: real name not UUID
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Time logged",
                        TimeEntryResponse.of(projectService.logTime(
                                TenantContext.getTenantIdAsObject(),
                                projectId, req, userId, userName))));
    }

    // ── Phases ────────────────────────────────────────────────────────────────

    @GetMapping("/{projectId}/phases")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Phases for a project — ordered by sort_order")
    public ResponseEntity<ApiResponse<List<PhaseResponse>>> getPhases(
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                phaseService.getPhases(TenantContext.getTenantIdAsObject(), projectId)
                        .stream().map(PhaseResponse::of).toList()));
    }

    @PostMapping("/{projectId}/phases")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "Create a phase — sort_order auto-assigned if not provided")
    public ResponseEntity<ApiResponse<PhaseResponse>> createPhase(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreatePhaseRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Phase created",
                        PhaseResponse.of(phaseService.createPhase(
                                TenantContext.getTenantIdAsObject(), projectId, req))));
    }

    @PutMapping("/phases/{phaseId}")
    @PreAuthorize("hasAuthority('PM_ADMIN')")
    @Operation(summary = "Update phase name, dates or sort order")
    public ResponseEntity<ApiResponse<PhaseResponse>> updatePhase(
            @PathVariable UUID phaseId,
            @Valid @RequestBody CreatePhaseRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Phase updated",
                PhaseResponse.of(phaseService.updatePhase(
                        TenantContext.getTenantIdAsObject(), phaseId, req))));
    }

    @PostMapping("/phases/{phaseId}/status")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Transition phase status: START | COMPLETE | SKIP")
    public ResponseEntity<ApiResponse<PhaseResponse>> updatePhaseStatus(
            @PathVariable UUID phaseId,
            @RequestBody Map<String, String> body) {
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
}
