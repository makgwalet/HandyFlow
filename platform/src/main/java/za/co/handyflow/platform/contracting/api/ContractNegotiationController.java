package za.co.handyflow.platform.contracting.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.contracting.application.internal.ContractNegotiationService;
import za.co.handyflow.platform.contracting.dto.ContractNegotiationDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/**
 * Staff side of counterparty negotiation. Counterparties never use these endpoints; they get their own link-based access.
 * Covered by the module entitlement check on /api/v1/contracts.
 */
@RestController
@RequestMapping("/api/v1/contracts/{contractId}/negotiation")
@RequiredArgsConstructor
@Tag(name = "Contract negotiation")
public class ContractNegotiationController {

    private static final String READ = "hasAnyAuthority('CONTRACTS_READ','CONTRACTS_MANAGE','CONTRACTS_ADMIN')";
    private static final String WRITE = "hasAnyAuthority('CONTRACTS_MANAGE','CONTRACTS_ADMIN')";

    private final ContractNegotiationService service;

    @GetMapping
    @PreAuthorize(READ)
    @Operation(summary = "The negotiation: status, current text, proposals, comments and audit trail")
    public ResponseEntity<ApiResponse<StaffView>> get(@PathVariable UUID contractId) {
        return ResponseEntity.ok(ApiResponse.success("Negotiation", service.staffView(TenantContext.getTenantIdAsObject(), contractId)));
    }

    @PostMapping("/open")
    @PreAuthorize(WRITE)
    @Operation(summary = "Open the contract to counterparty proposals")
    public ResponseEntity<ApiResponse<NegotiationView>> open(@PathVariable UUID contractId, @RequestBody(required = false) OpenRequest q) {
        return ResponseEntity.ok(ApiResponse.success("Negotiation opened", service.open(TenantContext.getTenantIdAsObject(), contractId,
                q == null ? null : q.responseDeadline(), TenantContext.getCurrentUserId())));
    }

    @PutMapping("/deadline")
    @PreAuthorize(WRITE)
    @Operation(summary = "Set or clear the deadline for counterparty responses")
    public ResponseEntity<ApiResponse<NegotiationView>> deadline(@PathVariable UUID contractId, @RequestBody DeadlineRequest q) {
        return ResponseEntity.ok(ApiResponse.success("Deadline updated", service.setDeadline(TenantContext.getTenantIdAsObject(), contractId,
                q.responseDeadline(), TenantContext.getCurrentUserId())));
    }

    @PostMapping("/proposals/{proposalId}/accept")
    @PreAuthorize(WRITE)
    @Operation(summary = "Accept a proposal. It changes the contract only once applied")
    public ResponseEntity<ApiResponse<ProposalView>> accept(@PathVariable UUID contractId, @PathVariable UUID proposalId, @Valid @RequestBody(required = false) DecisionRequest q) {
        return ResponseEntity.ok(ApiResponse.success("Proposal accepted", service.accept(TenantContext.getTenantIdAsObject(), contractId, proposalId,
                q == null ? null : q.note(), TenantContext.getCurrentUserId())));
    }

    @PostMapping("/proposals/{proposalId}/reject")
    @PreAuthorize(WRITE)
    @Operation(summary = "Reject a proposal, with the reason the counterparty will see")
    public ResponseEntity<ApiResponse<ProposalView>> reject(@PathVariable UUID contractId, @PathVariable UUID proposalId, @Valid @RequestBody RejectRequest q) {
        return ResponseEntity.ok(ApiResponse.success("Proposal rejected", service.reject(TenantContext.getTenantIdAsObject(), contractId, proposalId,
                q.reason(), TenantContext.getCurrentUserId())));
    }

    @PostMapping("/apply")
    @PreAuthorize(WRITE)
    @Operation(summary = "Apply all accepted proposals to the contract as one new draft revision")
    public ResponseEntity<ApiResponse<StaffView>> apply(@PathVariable UUID contractId, @Valid @RequestBody(required = false) DecisionRequest q) {
        return ResponseEntity.ok(ApiResponse.success("Accepted proposals applied", service.applyAccepted(TenantContext.getTenantIdAsObject(), contractId,
                q == null ? null : q.note(), TenantContext.getCurrentUserId())));
    }

    @PostMapping("/comments")
    @PreAuthorize(WRITE)
    @Operation(summary = "Comment on a clause or proposal, or leave an internal note the counterparty never sees")
    public ResponseEntity<ApiResponse<CommentView>> comment(@PathVariable UUID contractId, @Valid @RequestBody StaffCommentRequest q) {
        return ResponseEntity.ok(ApiResponse.success("Comment added", service.staffComment(TenantContext.getTenantIdAsObject(), contractId, q, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/ready")
    @PreAuthorize(WRITE)
    @Operation(summary = "Mark the negotiation ready for signature and pin the final wording")
    public ResponseEntity<ApiResponse<NegotiationView>> ready(@PathVariable UUID contractId) {
        return ResponseEntity.ok(ApiResponse.success("Ready for signature", service.markReady(TenantContext.getTenantIdAsObject(), contractId, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/close")
    @PreAuthorize(WRITE)
    @Operation(summary = "Formally close the negotiation")
    public ResponseEntity<ApiResponse<NegotiationView>> close(@PathVariable UUID contractId, @Valid @RequestBody(required = false) CloseRequest q) {
        return ResponseEntity.ok(ApiResponse.success("Negotiation closed", service.close(TenantContext.getTenantIdAsObject(), contractId,
                q == null ? null : q.reason(), TenantContext.getCurrentUserId())));
    }

    @PostMapping("/reopen")
    @PreAuthorize(WRITE)
    @Operation(summary = "Reopen a ready or closed negotiation (the contract must still be a draft)")
    public ResponseEntity<ApiResponse<NegotiationView>> reopen(@PathVariable UUID contractId, @RequestBody(required = false) OpenRequest q) {
        return ResponseEntity.ok(ApiResponse.success("Negotiation reopened", service.reopen(TenantContext.getTenantIdAsObject(), contractId,
                q == null ? null : q.responseDeadline(), TenantContext.getCurrentUserId())));
    }
}
