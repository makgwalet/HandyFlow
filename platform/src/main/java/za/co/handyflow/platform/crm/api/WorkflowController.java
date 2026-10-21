package za.co.handyflow.platform.crm.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.crm.application.internal.WorkflowService;
import za.co.handyflow.platform.crm.dto.WorkflowRequest;
import za.co.handyflow.platform.crm.dto.WorkflowResponse;
import za.co.handyflow.platform.crm.dto.WorkflowRunResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

@RestController
@RequestMapping("/api/v1/crm/workflows")
@RequiredArgsConstructor
@Tag(name = "CRM - Workflows", description = "When something happens to a lead, do something")
public class WorkflowController {
    private final WorkflowService service;

    @GetMapping
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "The workflows")
    public ResponseEntity<ApiResponse<List<WorkflowResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Add a workflow")
    public ResponseEntity<ApiResponse<WorkflowResponse>> create(@RequestBody WorkflowRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.create(TenantContext.getTenantIdAsObject(), request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Change a workflow")
    public ResponseEntity<ApiResponse<WorkflowResponse>> update(@PathVariable UUID id, @RequestBody WorkflowRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.update(TenantContext.getTenantIdAsObject(), id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_UPDATE')")
    @Operation(summary = "Delete a workflow")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/runs")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "The most recent times workflows ran (default 50, at most 200)")
    public ResponseEntity<ApiResponse<List<WorkflowRunResponse>>> runs(@RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(ApiResponse.success(service.recentRuns(TenantContext.getTenantIdAsObject(), limit)));
    }
}
