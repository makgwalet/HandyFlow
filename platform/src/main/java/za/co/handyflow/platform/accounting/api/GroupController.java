package za.co.handyflow.platform.accounting.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.accounting.application.internal.ConsolidationService;
import za.co.handyflow.platform.accounting.application.internal.GroupService;
import za.co.handyflow.platform.accounting.dto.GroupDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Groups of companies and consolidated statements (finance F26). */
@RestController
@RequestMapping("/api/v1/accounting/group")
@RequiredArgsConstructor
@Tag(name = "Company groups")
public class GroupController {

    private final GroupService groups;
    private final ConsolidationService consolidation;

    private static za.co.handyflow.platform.shared.TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static <T> ResponseEntity<ApiResponse<T>> ok(String msg, T data) { return ResponseEntity.ok(ApiResponse.success(msg, data)); }

    @GetMapping
    @PreAuthorize("hasAuthority('ACCOUNTING_READ')")
    @Operation(summary = "The group this company belongs to, if any")
    public ResponseEntity<ApiResponse<GroupInfo>> mine() { return ok("Success", groups.mine(tenant())); }

    @PostMapping
    @PreAuthorize("hasAuthority('ACCOUNTING_ADMIN')")
    public ResponseEntity<ApiResponse<GroupInfo>> create(@RequestBody CreateGroup req) { return ok("Created", groups.create(tenant(), req.name(), TenantContext.getCurrentUserId())); }

    @DeleteMapping
    @PreAuthorize("hasAuthority('ACCOUNTING_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> dissolve() { groups.dissolve(tenant()); return ok("Dissolved", null); }

    @PostMapping("/invites")
    @PreAuthorize("hasAuthority('ACCOUNTING_ADMIN')")
    @Operation(summary = "Invite a company. The code is shown once and works for 7 days")
    public ResponseEntity<ApiResponse<InviteCreated>> invite(@RequestBody CreateInvite req) { return ok("Invitation made", groups.invite(tenant(), req, TenantContext.getCurrentUserId())); }

    @GetMapping("/invites")
    @PreAuthorize("hasAuthority('ACCOUNTING_ADMIN')")
    public ResponseEntity<ApiResponse<List<Invite>>> invites() { return ok("Success", groups.invites(tenant())); }

    @DeleteMapping("/invites/{id}")
    @PreAuthorize("hasAuthority('ACCOUNTING_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> cancelInvite(@PathVariable UUID id) { groups.cancelInvite(tenant(), id); return ok("Cancelled", null); }

    @PostMapping("/join")
    @PreAuthorize("hasAuthority('ACCOUNTING_ADMIN')")
    @Operation(summary = "Join a group with an invitation code. This lets the group's parent read this company's account totals")
    public ResponseEntity<ApiResponse<GroupInfo>> join(@RequestBody JoinRequest req) { return ok("Joined", groups.join(tenant(), req.code(), TenantContext.getCurrentUserId())); }

    @PostMapping("/leave")
    @PreAuthorize("hasAuthority('ACCOUNTING_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> leave() { groups.leave(tenant()); return ok("Left the group", null); }

    @DeleteMapping("/members/{tenantId}")
    @PreAuthorize("hasAuthority('ACCOUNTING_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable UUID tenantId) { groups.remove(tenant(), tenantId); return ok("Removed", null); }

    @PutMapping("/members/{tenantId}/ownership")
    @PreAuthorize("hasAuthority('ACCOUNTING_ADMIN')")
    public ResponseEntity<ApiResponse<GroupInfo>> ownership(@PathVariable UUID tenantId, @RequestBody SetOwnership req) { return ok("Saved", groups.setOwnership(tenant(), tenantId, req.ownershipPercent())); }

    @GetMapping("/members/{tenantId}/accounts")
    @PreAuthorize("hasAuthority('ACCOUNTING_READ')")
    @Operation(summary = "Account codes and names (no balances) of a member company, for mapping and intercompany rules")
    public ResponseEntity<ApiResponse<List<AccountRef>>> accounts(@PathVariable UUID tenantId) { return ok("Success", groups.accountsOf(tenant(), tenantId)); }

    @GetMapping("/mappings")
    @PreAuthorize("hasAuthority('ACCOUNTING_READ')")
    public ResponseEntity<ApiResponse<List<Mapping>>> mappings() { return ok("Success", groups.mappings(tenant())); }

    @PutMapping("/mappings")
    @PreAuthorize("hasAuthority('ACCOUNTING_ADMIN')")
    public ResponseEntity<ApiResponse<List<Mapping>>> map(@RequestBody MapRequest req) { return ok("Saved", groups.map(tenant(), req)); }

    @DeleteMapping("/mappings/{id}")
    @PreAuthorize("hasAuthority('ACCOUNTING_ADMIN')")
    public ResponseEntity<ApiResponse<List<Mapping>>> unmap(@PathVariable UUID id) { return ok("Removed", groups.unmap(tenant(), id)); }

    @GetMapping("/eliminations")
    @PreAuthorize("hasAuthority('ACCOUNTING_READ')")
    public ResponseEntity<ApiResponse<List<Elimination>>> eliminations() { return ok("Success", groups.eliminations(tenant())); }

    @PostMapping("/eliminations")
    @PreAuthorize("hasAuthority('ACCOUNTING_ADMIN')")
    @Operation(summary = "Pair an intercompany account in one company with its mirror in another so both are eliminated")
    public ResponseEntity<ApiResponse<List<Elimination>>> addElimination(@RequestBody EliminationRequest req) { return ok("Saved", groups.addElimination(tenant(), req, TenantContext.getCurrentUserId())); }

    @DeleteMapping("/eliminations/{id}")
    @PreAuthorize("hasAuthority('ACCOUNTING_ADMIN')")
    public ResponseEntity<ApiResponse<List<Elimination>>> removeElimination(@PathVariable UUID id) { return ok("Removed", groups.removeElimination(tenant(), id)); }

    @GetMapping("/report")
    @PreAuthorize("hasAuthority('ACCOUNTING_READ')")
    @Operation(summary = "Consolidated statements for the group (parent company only)")
    public ResponseEntity<ApiResponse<Report>> report(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ok("Success", consolidation.report(tenant(), from, to));
    }
}
