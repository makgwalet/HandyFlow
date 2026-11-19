package za.co.handyflow.platform.contracting.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.contracting.application.internal.AuthorityRules;
import za.co.handyflow.platform.contracting.application.internal.ContractAuthorityService;
import za.co.handyflow.platform.contracting.domain.model.AuthorityAction;
import za.co.handyflow.platform.contracting.domain.model.ContractAuthorityGrant;
import za.co.handyflow.platform.contracting.dto.ContractAuthorityDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** Delegated authority: who may approve, negotiate, sign, renew or terminate contracts for the organisation. */
@RestController
@RequestMapping("/api/v1/contracts/authority")
@RequiredArgsConstructor
@Tag(name = "Contract authority")
public class ContractAuthorityController {

    private static final String READ = "hasAnyAuthority('CONTRACTS_READ','CONTRACTS_TEMPLATES','CONTRACTS_MANAGE','CONTRACTS_ADMIN')";
    private static final String ADMIN = "hasAuthority('CONTRACTS_ADMIN')";
    private static final String HOLDER = "hasAnyAuthority('CONTRACTS_MANAGE','CONTRACTS_ADMIN')";

    private final ContractAuthorityService service;

    @GetMapping("/settings")
    @PreAuthorize(READ)
    @Operation(summary = "Whether delegated authority is enforced for this organisation")
    public ResponseEntity<ApiResponse<SettingsView>> settings() {
        return ResponseEntity.ok(ApiResponse.success("Settings", new SettingsView(service.enforced(TenantContext.getTenantIdAsObject()))));
    }

    @PutMapping("/settings")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Turn enforcement of delegated authority on or off")
    public ResponseEntity<ApiResponse<SettingsView>> setSettings(@RequestBody SettingsRequest q) {
        boolean on = service.setEnforced(TenantContext.getTenantIdAsObject(), q.enforced(), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Settings updated", new SettingsView(on)));
    }

    @GetMapping("/grants")
    @PreAuthorize(READ)
    @Operation(summary = "Every grant of authority, newest first, with whether each is in force today")
    public ResponseEntity<ApiResponse<List<GrantView>>> grants(Authentication auth) {
        TenantId t = TenantContext.getTenantIdAsObject();
        List<ContractAuthorityGrant> all = service.grants(t);
        Map<UUID, ContractAuthorityGrant> byId = all.stream().collect(Collectors.toMap(ContractAuthorityGrant::getId, g -> g));
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Africa/Johannesburg"));
        return ResponseEntity.ok(ApiResponse.success("Authority", all.stream().map(g -> GrantView.of(g, AuthorityRules.inForce(g, byId, today), AuthorityRules.appliesTo(g, TenantContext.getCurrentUserId(), roles(auth)))).toList()));
    }

    @GetMapping("/mine")
    @PreAuthorize(READ)
    @Operation(summary = "The authority the current person holds today")
    public ResponseEntity<ApiResponse<List<GrantView>>> mine(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("My authority", service.heldBy(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(), roles(auth))
                .stream().map(g -> GrantView.of(g, true, true)).toList()));
    }

    @GetMapping("/check")
    @PreAuthorize(READ)
    @Operation(summary = "Whether the current person may take an action on one contract")
    public ResponseEntity<ApiResponse<CheckView>> check(@RequestParam AuthorityAction action, @RequestParam UUID contractId, Authentication auth) {
        TenantId t = TenantContext.getTenantIdAsObject();
        var d = service.decideFor(t, TenantContext.getCurrentUserId(), roles(auth), action, contractId);
        return ResponseEntity.ok(ApiResponse.success("Check", new CheckView(d.allowed(), d.reason(), service.enforced(t))));
    }

    @PostMapping("/grants")
    @PreAuthorize(ADMIN)
    @Operation(summary = "Give authority to one person or one role")
    public ResponseEntity<ApiResponse<GrantView>> grant(@RequestBody GrantRequest q) {
        var g = service.grant(TenantContext.getTenantIdAsObject(), new ContractAuthorityService.GrantRequest(q.userId(), q.role(), q.businessUnit(),
                q.contractTypes(), q.actions(), q.unlimited(), q.maxAmount(), q.effectiveFrom(), q.expiresOn(), q.conditions()), TenantContext.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success("Authority granted", GrantView.of(g, true, false)));
    }

    @PostMapping("/grants/{id}/delegate")
    @PreAuthorize(HOLDER)
    @Operation(summary = "Delegate part of your own authority to someone else for a stated period")
    public ResponseEntity<ApiResponse<GrantView>> delegate(@PathVariable UUID id, @RequestBody DelegateRequest q, Authentication auth) {
        var g = service.delegate(TenantContext.getTenantIdAsObject(), id, new ContractAuthorityService.DelegationRequest(q.toUserId(), q.actions(),
                q.contractTypes(), q.unlimited(), q.maxAmount(), q.effectiveFrom(), q.expiresOn(), q.conditions()), TenantContext.getCurrentUserId(), roles(auth));
        return ResponseEntity.ok(ApiResponse.success("Authority delegated", GrantView.of(g, true, true)));
    }

    @PostMapping("/grants/{id}/revoke")
    @PreAuthorize(HOLDER)
    @Operation(summary = "Revoke a grant, or a delegation you made")
    public ResponseEntity<ApiResponse<GrantView>> revoke(@PathVariable UUID id, @RequestBody RevokeRequest q, Authentication auth) {
        TenantId t = TenantContext.getTenantIdAsObject();
        UUID me = TenantContext.getCurrentUserId();
        boolean admin = roles(auth).contains("CONTRACTS_ADMIN");
        var existing = service.grants(t).stream().filter(g -> g.getId().equals(id)).findFirst();
        // administrators revoke anything; others only a delegation they made themselves
        if (!admin && (existing.isEmpty() || existing.get().getDelegatedBy() == null || !existing.get().getDelegatedBy().equals(me)))
            throw new org.springframework.security.access.AccessDeniedException("Only an administrator can revoke this authority");
        return ResponseEntity.ok(ApiResponse.success("Authority revoked", GrantView.of(service.revoke(t, id, q.reason(), me), false, false)));
    }

    @GetMapping("/events")
    @PreAuthorize(ADMIN)
    @Operation(summary = "The audit trail of changes to authority")
    public ResponseEntity<ApiResponse<List<EventView>>> events() {
        return ResponseEntity.ok(ApiResponse.success("Events", service.events(TenantContext.getTenantIdAsObject()).stream().map(EventView::of).toList()));
    }

    private static Set<String> roles(Authentication auth) {
        return auth == null ? Set.of() : auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }
}
