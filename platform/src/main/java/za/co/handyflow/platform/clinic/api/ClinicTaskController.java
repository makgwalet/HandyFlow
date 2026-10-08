package za.co.handyflow.platform.clinic.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.clinic.application.internal.ClinicTaskService;
import za.co.handyflow.platform.clinic.dto.TaskDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.UserContext;

import java.util.List;
import java.util.UUID;

/** Clinic tasks: follow-ups and calls that must not be lost (patch 0151). */
@RestController
@RequestMapping("/api/v1/clinic/tasks")
@RequiredArgsConstructor
@Tag(name = "Clinic tasks", description = "Follow-ups, calls and results to chase")
public class ClinicTaskController {

    private final ClinicTaskService service;

    private static UUID tenant() { return TenantContext.getTenantIdAsObject().getValue(); }
    private static UUID user() { try { return UserContext.getCurrentUserId(); } catch (RuntimeException e) { return null; } }

    @GetMapping
    @PreAuthorize("hasAuthority('CLINIC_TASK_READ')")
    @Operation(summary = "Open tasks, overdue first. mine=true: assigned to me or made by me")
    public ResponseEntity<ApiResponse<List<TaskRow>>> list(@RequestParam(defaultValue = "true") boolean mine,
                                                           @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(ApiResponse.success("Success", service.open(tenant(), user(), mine, limit)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CLINIC_TASK_CREATE')")
    @Operation(summary = "Create a task. A second request for the same open source returns the existing task")
    public ResponseEntity<ApiResponse<TaskRow>> create(@RequestBody CreateTaskRequest body) {
        return ResponseEntity.status(201).body(ApiResponse.success("Task created", service.create(tenant(), user(), body)));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('CLINIC_TASK_COMPLETE')")
    @Operation(summary = "Mark a task done (note optional)")
    public ResponseEntity<ApiResponse<TaskRow>> complete(@PathVariable UUID id, @RequestBody(required = false) NoteRequest body) {
        return ResponseEntity.ok(ApiResponse.success("Task completed", service.complete(tenant(), user(), id, body == null ? null : body.note())));
    }

    @PostMapping("/{id}/dismiss")
    @PreAuthorize("hasAuthority('CLINIC_TASK_COMPLETE')")
    @Operation(summary = "Dismiss a task (reason required)")
    public ResponseEntity<ApiResponse<TaskRow>> dismiss(@PathVariable UUID id, @RequestBody NoteRequest body) {
        return ResponseEntity.ok(ApiResponse.success("Task dismissed", service.dismiss(tenant(), user(), id, body == null ? null : body.note())));
    }
}
