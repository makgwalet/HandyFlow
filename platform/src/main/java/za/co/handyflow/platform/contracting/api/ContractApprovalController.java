package za.co.handyflow.platform.contracting.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.contracting.application.internal.ContractApprovalService;
import za.co.handyflow.platform.contracting.domain.model.ApprovalAction;
import za.co.handyflow.platform.contracting.domain.model.ContractApproval;
import za.co.handyflow.platform.contracting.dto.ContractApprovalDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Maker-checker: the approval policy and the approval requests. Covered by the module entitlement check on /api/v1/contracts. */
@RestController
@RequestMapping("/api/v1/contracts/approvals")
@RequiredArgsConstructor
@Tag(name = "Contract approvals")
public class ContractApprovalController {

    private static final String READ = "hasAnyAuthority('CONTRACTS_READ','CONTRACTS_TEMPLATES','CONTRACTS_MANAGE','CONTRACTS_ADMIN')";
    private static final String ACT = "hasAnyAuthority('CONTRACTS_TEMPLATES','CONTRACTS_MANAGE','CONTRACTS_ADMIN')";
    private static final String ADMIN = "hasAuthority('CONTRACTS_ADMIN')";

    private final ContractApprovalService service;

    @GetMapping("/policy")
    @PreAuthorize(READ)
    @Operation(summary = "The approval policy for each action (defaults where the organisation has not set one)")
    public ResponseEntity<ApiResponse<List<PolicyView>>> policy() {
        return ResponseEntity.ok(ApiResponse.success("Policy", service.policies(TenantContext.getTenantIdAsObject()).stream()
                .map(p -> new PolicyView(p.action(), p.required(), p.allowSoleUserException(), p.isDefault())).toList()));
    }

    @PutMapping("/policy/{action}")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Set whether an action needs approval and whether a sole user may approve their own request")
    public ResponseEntity<ApiResponse<PolicyView>> setPolicy(@PathVariable ApprovalAction action, @RequestBody PolicyRequest q) {
        var p = service.setPolicy(TenantContext.getTenantIdAsObject(), action, q.required(), q.allowSoleUserException(), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Policy updated", new PolicyView(p.action(), p.required(), p.allowSoleUserException(), p.isDefault())));
    }

    @GetMapping
    @PreAuthorize(READ)
    @Operation(summary = "Approval requests, newest first (optionally one status)")
    public ResponseEntity<ApiResponse<List<ApprovalView>>> list(@RequestParam(required = false) String status, Authentication auth) {
        UUID me = TenantContext.getCurrentUserId();
        Set<String> roles = authorities(auth);
        return ResponseEntity.ok(ApiResponse.success("Approvals", service.list(TenantContext.getTenantIdAsObject(), status).stream()
                .map(a -> ApprovalView.of(a, me, a.getAction().canBeDecidedBy(roles))).toList()));
    }

    @GetMapping("/subject/{action}/{subjectId}")
    @PreAuthorize(READ)
    @Operation(summary = "Every approval request made for one contract, clause or template, newest first")
    public ResponseEntity<ApiResponse<List<ApprovalView>>> forSubject(@PathVariable ApprovalAction action, @PathVariable UUID subjectId, Authentication auth) {
        UUID me = TenantContext.getCurrentUserId();
        Set<String> roles = authorities(auth);
        return ResponseEntity.ok(ApiResponse.success("Approvals", service.forSubject(TenantContext.getTenantIdAsObject(), action, subjectId).stream()
                .map(a -> ApprovalView.of(a, me, a.getAction().canBeDecidedBy(roles))).toList()));
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ)
    @Operation(summary = "One approval request with its audit trail")
    public ResponseEntity<ApiResponse<Detail>> get(@PathVariable UUID id, Authentication auth) {
        var tenant = TenantContext.getTenantIdAsObject();
        UUID me = TenantContext.getCurrentUserId();
        ContractApproval a = service.get(tenant, id);
        return ResponseEntity.ok(ApiResponse.success("Approval", new Detail(ApprovalView.of(a, me, a.getAction().canBeDecidedBy(authorities(auth))),
                service.events(tenant, id).stream().map(EventView::of).toList())));
    }

    @PostMapping
    @PreAuthorize(ACT)
    @Operation(summary = "Ask for approval of a contract, clause or template as it is now")
    public ResponseEntity<ApiResponse<ApprovalView>> request(@Valid @RequestBody RequestApproval q, Authentication auth) {
        Set<String> roles = authorities(auth);
        if (!q.action().canBeDecidedBy(roles)) throw new AccessDeniedException("Your role cannot do this");
        UUID me = TenantContext.getCurrentUserId();
        ContractApproval a = service.request(TenantContext.getTenantIdAsObject(), q.action(), q.subjectId(), me, q.note());
        return ResponseEntity.ok(ApiResponse.success("Approval requested", ApprovalView.of(a, me, a.getAction().canBeDecidedBy(roles))));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(ACT)
    @Operation(summary = "Approve. A sole-user exception lets the requester approve their own request, with a reason, where the policy allows it")
    public ResponseEntity<ApiResponse<ApprovalView>> approve(@PathVariable UUID id, @Valid @RequestBody Decision q, Authentication auth) {
        UUID me = TenantContext.getCurrentUserId();
        Set<String> roles = authorities(auth);
        ContractApproval a = service.approve(TenantContext.getTenantIdAsObject(), id, me, roles, q.note(), q.soleUserException(), q.exceptionReason());
        return ResponseEntity.ok(ApiResponse.success("Approved", ApprovalView.of(a, me, a.getAction().canBeDecidedBy(roles))));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(ACT)
    @Operation(summary = "Reject, with a reason")
    public ResponseEntity<ApiResponse<ApprovalView>> reject(@PathVariable UUID id, @Valid @RequestBody Decision q, Authentication auth) {
        UUID me = TenantContext.getCurrentUserId();
        Set<String> roles = authorities(auth);
        ContractApproval a = service.reject(TenantContext.getTenantIdAsObject(), id, me, roles, q.note());
        return ResponseEntity.ok(ApiResponse.success("Rejected", ApprovalView.of(a, me, a.getAction().canBeDecidedBy(roles))));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(ACT)
    @Operation(summary = "Withdraw your own request")
    public ResponseEntity<ApiResponse<ApprovalView>> cancel(@PathVariable UUID id, Authentication auth) {
        UUID me = TenantContext.getCurrentUserId();
        ContractApproval a = service.cancel(TenantContext.getTenantIdAsObject(), id, me);
        return ResponseEntity.ok(ApiResponse.success("Withdrawn", ApprovalView.of(a, me, a.getAction().canBeDecidedBy(authorities(auth)))));
    }

    private static Set<String> authorities(Authentication auth) {
        return auth == null ? Set.of() : auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }
}
