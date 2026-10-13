package za.co.handyflow.platform.debtcollection.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.debtcollection.application.internal.WorkItemService;
import za.co.handyflow.platform.debtcollection.application.internal.WorkItemService.WorkItemView;
import za.co.handyflow.platform.debtcollection.domain.model.PriorityBand;
import za.co.handyflow.platform.debtcollection.domain.model.WorkItem;
import za.co.handyflow.platform.debtcollection.domain.model.WorkItemStatus;
import za.co.handyflow.platform.debtcollection.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Collections work queue. /my is the collector's landing page ("My Work"): ranked
 * work items, each with the explainable reasons behind its priority. Team-wide
 * listing, reassignment and priority override are supervisor actions
 * (DEBTCOLLECTION_ADMIN) — collectors act on their own items.
 * <p>
 * Ownership rule: a non-admin may only snooze/complete/wake items assigned to them.
 */
@RestController
@RequestMapping("/api/v1/debtcollection/work-items")
@RequiredArgsConstructor
@Tag(name = "Debt Collection - Work Queue", description = "Prioritised collector work items")
public class WorkItemController {

    private final WorkItemService workItemService;
    private final FeatureGuard featureGuard;

    @GetMapping("/my")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "My Work — the current user's actionable items, highest priority first")
    public ResponseEntity<ApiResponse<Page<WorkItemResponse>>> myQueue(@PageableDefault(size = 25) Pageable pageable) {
        featureGuard.requireModule("debtcollection");
        Page<WorkItemResponse> page = workItemService
                .myQueue(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), pageable)
                .map(WorkItemResponse::of);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/my/summary")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Counts of my open items per priority band")
    public ResponseEntity<ApiResponse<Map<PriorityBand, Long>>> mySummary() {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success(
                workItemService.summaryFor(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId())));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Team queue (supervisor) — optionally filtered by status and/or assignee")
    public ResponseEntity<ApiResponse<Page<WorkItemResponse>>> teamQueue(
            @RequestParam(required = false) WorkItemStatus status,
            @RequestParam(required = false) UUID assigneeId,
            @PageableDefault(size = 25) Pageable pageable) {
        featureGuard.requireModule("debtcollection");
        Page<WorkItemResponse> page = workItemService
                .teamQueue(TenantContext.getTenantIdAsObject(), status, assigneeId, pageable)
                .map(WorkItemResponse::of);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<List<WorkItemEventResponse>>> history(@PathVariable UUID id) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success(
                workItemService.history(TenantContext.getTenantIdAsObject(), id).stream()
                        .map(WorkItemEventResponse::of).toList()));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Create a manual work item on a case")
    public ResponseEntity<ApiResponse<WorkItemResponse>> create(@Valid @RequestBody CreateWorkItemRequest req) {
        featureGuard.requireModule("debtcollection");
        TenantId t = TenantContext.getTenantIdAsObject();
        WorkItem w = workItemService.raiseManual(t, req.caseId(), req.title(), req.dueDate(), req.assignedToUserId(),
                req.assignedToUserName(), TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Work item created", view(t, w)));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Assign / reassign (supervisor)")
    public ResponseEntity<ApiResponse<WorkItemResponse>> assign(@PathVariable UUID id,
                                                                @Valid @RequestBody AssignWorkItemRequest req) {
        featureGuard.requireModule("debtcollection");
        TenantId t = TenantContext.getTenantIdAsObject();
        WorkItem w = workItemService.assign(t, id, req.userId(), req.userName(), req.reason(),
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.ok(ApiResponse.success("Work item assigned", view(t, w)));
    }

    @PostMapping("/{id}/snooze")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Snooze until a future date — a reason is mandatory")
    public ResponseEntity<ApiResponse<WorkItemResponse>> snooze(@PathVariable UUID id,
                                                                @Valid @RequestBody SnoozeWorkItemRequest req) {
        featureGuard.requireModule("debtcollection");
        TenantId t = TenantContext.getTenantIdAsObject();
        requireOwnerOrAdmin(t, id);
        WorkItem w = workItemService.snooze(t, id, req.until(), req.reason(),
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.ok(ApiResponse.success("Work item snoozed", view(t, w)));
    }

    @PostMapping("/{id}/wake")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<WorkItemResponse>> wake(@PathVariable UUID id) {
        featureGuard.requireModule("debtcollection");
        TenantId t = TenantContext.getTenantIdAsObject();
        requireOwnerOrAdmin(t, id);
        WorkItem w = workItemService.wake(t, id, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.ok(ApiResponse.success("Work item woken", view(t, w)));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Complete with a recorded disposition valid for the item's type")
    public ResponseEntity<ApiResponse<WorkItemResponse>> complete(@PathVariable UUID id,
                                                                  @Valid @RequestBody CompleteWorkItemRequest req) {
        featureGuard.requireModule("debtcollection");
        TenantId t = TenantContext.getTenantIdAsObject();
        requireOwnerOrAdmin(t, id);
        WorkItem w = workItemService.complete(t, id, req.disposition(), req.notes(),
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.ok(ApiResponse.success("Work item completed", view(t, w)));
    }

    @PostMapping("/{id}/priority-override")
    @PreAuthorize("hasAuthority('DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Override calculated priority (supervisor) — reason mandatory, calculated score is retained")
    public ResponseEntity<ApiResponse<WorkItemResponse>> overridePriority(
            @PathVariable UUID id, @Valid @RequestBody PriorityOverrideRequest req) {
        featureGuard.requireModule("debtcollection");
        TenantId t = TenantContext.getTenantIdAsObject();
        WorkItem w = workItemService.overridePriority(t, id, req.priority(), req.reason(),
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.ok(ApiResponse.success("Priority updated", view(t, w)));
    }

    private void requireOwnerOrAdmin(TenantId t, UUID id) {
        boolean admin = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> "DEBTCOLLECTION_ADMIN".equals(a.getAuthority()));
        workItemService.assertOwnerOrAdmin(t, id, TenantContext.getCurrentUserId(), admin);
    }

    private WorkItemResponse view(TenantId t, WorkItem w) {
        return WorkItemResponse.of(workItemService.view(t, w));
    }
}
