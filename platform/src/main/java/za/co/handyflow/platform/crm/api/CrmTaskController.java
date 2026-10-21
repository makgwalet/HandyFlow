package za.co.handyflow.platform.crm.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.crm.application.internal.CrmTaskService;
import za.co.handyflow.platform.crm.dto.TaskListResponse;
import za.co.handyflow.platform.crm.dto.TaskResponse;
import za.co.handyflow.platform.crm.dto.UpdateTaskRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/crm/tasks")
@RequiredArgsConstructor
@Tag(name = "CRM - Task queue", description = "Follow-ups across all customers, with reassign, snooze and priority")
public class CrmTaskController {

    private final CrmTaskService taskService;

    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "The task queue: open tasks (default) or tasks finished in the last 30 days (done=true)")
    public ResponseEntity<ApiResponse<TaskListResponse>> list(@RequestParam(defaultValue = "false") boolean done) {
        return ResponseEntity.ok(ApiResponse.success(taskService.list(TenantContext.getTenantIdAsObject(), done)));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Reassign, snooze (new due date) or change the priority of an open task")
    public ResponseEntity<ApiResponse<TaskResponse>> update(@PathVariable UUID id, @Valid @RequestBody UpdateTaskRequest request) {
        return ResponseEntity.ok(ApiResponse.success(taskService.update(TenantContext.getTenantIdAsObject(), id, request)));
    }
}
