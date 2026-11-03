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
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.projects.application.internal.ChangeApplicationService;
import za.co.handyflow.platform.projects.application.internal.ChangeOrderService;
import za.co.handyflow.platform.projects.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Change control endpoints. The URLs that already existed (list, create, submit, approve, reject,
 * client-approve) are unchanged, so the frontend keeps working; this chunk adds detail, edit,
 * withdraw, reopen, client dispute / resolve, impact analysis, attachments and baseline history.
 *
 * Every route contains "change-orders" or "/budget/", so ProjectAccessInterceptor already limits
 * them to project roles with cost visibility when restricted mode is on.
 */
@Validated
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Change Control", description = "Change orders: impact assessment, approval, baseline history")
public class ChangeOrderController {

    private final ChangeOrderService       changeOrderService;
    private final ChangeApplicationService applicationService;

    // ── Read ──────────────────────────────────────────────────────────────────

    @GetMapping("/{projectId}/change-orders")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "List change orders for a project")
    public ResponseEntity<ApiResponse<List<ChangeOrderResponse>>> list(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                changeOrderService.getChangeOrders(tenant(), projectId).stream().map(ChangeOrderResponse::of).toList()));
    }

    @GetMapping("/change-orders/{id}")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "A change order with its cost lines, affected tasks, submission history and linked RFIs")
    public ResponseEntity<ApiResponse<ChangeOrderDetailResponse>> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success", changeOrderService.getDetail(tenant(), id)));
    }

    @GetMapping("/change-orders/{id}/impact-analysis")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "What approving this change order would do — budget before/after, forecast finish with and without the delays. Changes nothing.")
    public ResponseEntity<ApiResponse<ImpactAnalysisResponse>> impact(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success", changeOrderService.impactAnalysis(tenant(), id)));
    }

    @GetMapping("/{projectId}/budget/baseline-history")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Every version of the approved baseline — version 1, then one per applied change order — with old and new values")
    public ResponseEntity<ApiResponse<List<BaselineRevisionResponse>>> baselineHistory(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                applicationService.history(tenant(), projectId).stream().map(BaselineRevisionResponse::of).toList()));
    }

    // ── Create / edit ─────────────────────────────────────────────────────────

    @PostMapping("/{projectId}/change-orders")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Create a change order (DRAFT) — auto-assigns the CO number")
    public ResponseEntity<ApiResponse<ChangeOrderResponse>> create(
            @PathVariable UUID projectId, @Valid @RequestBody CreateChangeOrderRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Change order created",
                ChangeOrderResponse.of(changeOrderService.createChangeOrder(
                        tenant(), projectId, req, TenantContext.getCurrentUserId()))));
    }

    @PutMapping("/change-orders/{id}")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Edit a DRAFT change order (replaces its content, cost lines and affected tasks)")
    public ResponseEntity<ApiResponse<ChangeOrderResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody CreateChangeOrderRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Change order updated",
                ChangeOrderResponse.of(changeOrderService.updateChangeOrder(tenant(), id, req))));
    }

    // ── Workflow ──────────────────────────────────────────────────────────────

    @PostMapping("/change-orders/{id}/submit")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Submit for approval (DRAFT → SUBMITTED) — cost allocations must be consistent with the current budget")
    public ResponseEntity<ApiResponse<ChangeOrderResponse>> submit(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Submitted",
                ChangeOrderResponse.of(changeOrderService.submitChangeOrder(tenant(), id,
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName()))));
    }

    @PostMapping("/change-orders/{id}/approve")
    @PreAuthorize("hasAuthority('PM_APPROVE')")
    @Operation(summary = "Approve and APPLY — adjusts budget lines, moves the project end date and baseline, pushes affected tasks and dependents, and records a new baseline version")
    public ResponseEntity<ApiResponse<ChangeOrderResponse>> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Change order approved",
                ChangeOrderResponse.of(changeOrderService.approveChangeOrder(tenant(), id,
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName()))));
    }

    @PostMapping("/change-orders/{id}/reject")
    @PreAuthorize("hasAuthority('PM_APPROVE')")
    @Operation(summary = "Reject a submitted change order — a reason is required")
    public ResponseEntity<ApiResponse<ChangeOrderResponse>> reject(
            @PathVariable UUID id, @Valid @RequestBody ChangeOrderReasonRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Change order rejected",
                ChangeOrderResponse.of(changeOrderService.rejectChangeOrder(tenant(), id, req.reason()))));
    }

    @PostMapping("/change-orders/{id}/withdraw")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Withdraw a DRAFT or SUBMITTED change order — a reason is required; final")
    public ResponseEntity<ApiResponse<ChangeOrderResponse>> withdraw(
            @PathVariable UUID id, @Valid @RequestBody ChangeOrderReasonRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Change order withdrawn",
                ChangeOrderResponse.of(changeOrderService.withdraw(tenant(), id, req.reason()))));
    }

    @PostMapping("/change-orders/{id}/reopen")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Reopen a REJECTED change order as a new revision (back to DRAFT) so it can be edited and resubmitted")
    public ResponseEntity<ApiResponse<ChangeOrderResponse>> reopen(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Change order reopened",
                ChangeOrderResponse.of(changeOrderService.reopen(tenant(), id))));
    }

    // ── Client side ───────────────────────────────────────────────────────────

    @PostMapping("/change-orders/{id}/client-approve")
    @PreAuthorize("hasAuthority('PM_APPROVE')")
    @Operation(summary = "Record client sign-off on an approved change order (blocked while a dispute is open)")
    public ResponseEntity<ApiResponse<ChangeOrderResponse>> clientApprove(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Client approval recorded",
                ChangeOrderResponse.of(changeOrderService.markClientApproved(tenant(), id))));
    }

    @PostMapping("/change-orders/{id}/client-dispute")
    @PreAuthorize("hasAuthority('PM_APPROVE')")
    @Operation(summary = "Record that the client disputes an approved change order — a reason is required")
    public ResponseEntity<ApiResponse<ChangeOrderResponse>> clientDispute(
            @PathVariable UUID id, @Valid @RequestBody ChangeOrderReasonRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Client dispute recorded",
                ChangeOrderResponse.of(changeOrderService.raiseClientDispute(tenant(), id, req.reason()))));
    }

    @PostMapping("/change-orders/{id}/client-dispute/resolve")
    @PreAuthorize("hasAuthority('PM_APPROVE')")
    @Operation(summary = "Resolve an open client dispute — body.reason holds the resolution")
    public ResponseEntity<ApiResponse<ChangeOrderResponse>> resolveDispute(
            @PathVariable UUID id, @Valid @RequestBody ChangeOrderReasonRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Client dispute resolved",
                ChangeOrderResponse.of(changeOrderService.resolveClientDispute(tenant(), id, req.reason()))));
    }

    // ── Evidence ──────────────────────────────────────────────────────────────

    @PostMapping(value = "/change-orders/{id}/attachments", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('PM_WRITE')")
    @Operation(summary = "Attach supporting evidence (quotation, drawing, instruction) to a change order")
    public ResponseEntity<ApiResponse<EvidenceResponse>> attach(
            @PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Attachment uploaded",
                changeOrderService.attachEvidence(tenant(), id, file,
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @GetMapping("/change-orders/{id}/attachments")
    @PreAuthorize("hasAuthority('PM_READ')")
    @Operation(summary = "Supporting evidence attached to a change order")
    public ResponseEntity<ApiResponse<List<EvidenceResponse>>> attachments(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success", changeOrderService.getAttachments(tenant(), id)));
    }

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
}