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
import za.co.handyflow.platform.projects.application.internal.ProjectIssueService;
import za.co.handyflow.platform.projects.dto.IssueDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Validated
@Tag(name = "Issues", description = "Project issues register and action items against risks and issues")
public class IssuesController {

    private final ProjectIssueService service;

    @GetMapping("/{projectId}/issues")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Issues on a project, newest first, with their open action count")
    public ResponseEntity<ApiResponse<List<IssueResponse>>> issues(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Success", service.list(tenant(), projectId)));
    }

    @PostMapping("/{projectId}/issues")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Raise an issue (optionally from a risk on the same project)")
    public ResponseEntity<ApiResponse<IssueResponse>> raise(@PathVariable UUID projectId, @Valid @RequestBody CreateIssueRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Issue raised",
                service.raise(tenant(), projectId, req, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PutMapping("/issues/{issueId}")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Edit an issue's title, description, priority, owner or due date")
    public ResponseEntity<ApiResponse<IssueResponse>> update(@PathVariable UUID issueId, @Valid @RequestBody UpdateIssueRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Issue updated", service.update(tenant(), issueId, req)));
    }

    @PostMapping("/issues/{issueId}/action")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "START | RESOLVE (with resolution) | CLOSE | REOPEN")
    public ResponseEntity<ApiResponse<IssueResponse>> act(@PathVariable UUID issueId, @Valid @RequestBody IssueActionRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Issue updated", service.act(tenant(), issueId, req, TenantContext.getCurrentUserName())));
    }

    @GetMapping("/{projectId}/action-items")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Action items on a project's risks and issues")
    public ResponseEntity<ApiResponse<List<ActionItemResponse>>> actions(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Success", service.actions(tenant(), projectId)));
    }

    @PostMapping("/{projectId}/action-items")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Add an action to a risk or an issue on this project")
    public ResponseEntity<ApiResponse<ActionItemResponse>> addAction(@PathVariable UUID projectId, @Valid @RequestBody CreateActionItemRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Action added",
                service.addAction(tenant(), projectId, req, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PutMapping("/action-items/{actionId}")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Edit an action's title, owner or due date")
    public ResponseEntity<ApiResponse<ActionItemResponse>> updateAction(@PathVariable UUID actionId, @Valid @RequestBody UpdateActionItemRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Action updated", service.updateAction(tenant(), actionId, req)));
    }

    @PostMapping("/action-items/{actionId}/action")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "DONE | CANCEL | REOPEN")
    public ResponseEntity<ApiResponse<ActionItemResponse>> actOnAction(@PathVariable UUID actionId, @Valid @RequestBody ActionItemActionRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Action updated", service.actOnAction(tenant(), actionId, req, TenantContext.getCurrentUserName())));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
}
