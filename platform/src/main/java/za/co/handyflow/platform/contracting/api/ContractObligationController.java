package za.co.handyflow.platform.contracting.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.contracting.application.internal.ContractObligationService;
import za.co.handyflow.platform.contracting.dto.ContractObligationDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Obligations and renewal decisions. The module entitlement check covers /api/v1/contracts (ModuleEntitlementPaths). */
@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
@Tag(name = "Contract obligations and renewals")
public class ContractObligationController {

    private static final String READ = "hasAnyAuthority('CONTRACTS_READ','CONTRACTS_MANAGE','CONTRACTS_ADMIN')";
    private static final String WRITE = "hasAnyAuthority('CONTRACTS_MANAGE','CONTRACTS_ADMIN')";

    private final ContractObligationService service;

    @GetMapping("/obligations")
    @PreAuthorize(READ)
    @Operation(summary = "Open obligations across all contracts, soonest first")
    public ResponseEntity<ApiResponse<List<ObligationResponse>>> open(@RequestParam(required = false) Integer dueWithinDays,
                                                                      @RequestParam(defaultValue = "false") boolean overdueOnly) {
        return ResponseEntity.ok(ApiResponse.success("Obligations", service.open(TenantContext.getTenantIdAsObject(), dueWithinDays, overdueOnly)));
    }

    @GetMapping("/obligations/summary")
    @PreAuthorize(READ)
    @Operation(summary = "Counts of open, overdue and soon-due obligations")
    public ResponseEntity<ApiResponse<ObligationSummary>> summary() {
        return ResponseEntity.ok(ApiResponse.success("Obligation summary", service.summary(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/{contractId}/obligations")
    @PreAuthorize(READ)
    @Operation(summary = "Obligations of one contract")
    public ResponseEntity<ApiResponse<List<ObligationResponse>>> forContract(@PathVariable UUID contractId) {
        return ResponseEntity.ok(ApiResponse.success("Obligations", service.forContract(TenantContext.getTenantIdAsObject(), contractId)));
    }

    @PostMapping("/{contractId}/obligations")
    @PreAuthorize(WRITE)
    @Operation(summary = "Add an obligation to a contract")
    public ResponseEntity<ApiResponse<ObligationResponse>> create(@PathVariable UUID contractId, @Valid @RequestBody ObligationRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Obligation added",
                service.create(TenantContext.getTenantIdAsObject(), contractId, req, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/obligations/{id}")
    @PreAuthorize(WRITE)
    @Operation(summary = "Change an open obligation")
    public ResponseEntity<ApiResponse<ObligationResponse>> update(@PathVariable UUID id, @Valid @RequestBody ObligationRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Obligation updated", service.update(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/obligations/{id}/start")
    @PreAuthorize(WRITE)
    @Operation(summary = "Mark an obligation as in progress")
    public ResponseEntity<ApiResponse<ObligationResponse>> start(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Obligation started", service.start(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/obligations/{id}/complete")
    @PreAuthorize(WRITE)
    @Operation(summary = "Complete an obligation. A recurring one creates its next occurrence")
    public ResponseEntity<ApiResponse<ObligationResponse>> complete(@PathVariable UUID id, @RequestBody(required = false) CompleteObligationRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Obligation completed", service.complete(TenantContext.getTenantIdAsObject(), id,
                req == null ? null : req.note(), TenantContext.getCurrentUserId())));
    }

    @PostMapping("/obligations/{id}/waive")
    @PreAuthorize(WRITE)
    @Operation(summary = "Waive an obligation, with the reason")
    public ResponseEntity<ApiResponse<ObligationResponse>> waive(@PathVariable UUID id, @Valid @RequestBody WaiveObligationRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Obligation waived", service.waive(TenantContext.getTenantIdAsObject(), id, req.reason())));
    }

    @GetMapping("/renewals")
    @PreAuthorize(READ)
    @Operation(summary = "Signed contracts ending within the window, with their notice deadline and decision state")
    public ResponseEntity<ApiResponse<List<RenewalItem>>> renewals(@RequestParam(defaultValue = "90") int withinDays) {
        return ResponseEntity.ok(ApiResponse.success("Renewals", service.renewals(TenantContext.getTenantIdAsObject(), Math.min(Math.max(withinDays, 1), 365))));
    }

    @GetMapping("/{contractId}/renewal-decisions")
    @PreAuthorize(READ)
    @Operation(summary = "Renewal decisions made on a contract, newest first")
    public ResponseEntity<ApiResponse<List<RenewalDecisionResponse>>> decisions(@PathVariable UUID contractId) {
        return ResponseEntity.ok(ApiResponse.success("Renewal decisions", service.decisions(TenantContext.getTenantIdAsObject(), contractId)));
    }

    @PostMapping("/{contractId}/renewal-decisions")
    @PreAuthorize(WRITE)
    @Operation(summary = "Decide to renew (creates a successor draft), renegotiate or not renew a signed contract")
    public ResponseEntity<ApiResponse<RenewalDecisionResponse>> decide(@PathVariable UUID contractId, @Valid @RequestBody RenewalDecisionRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Renewal decision recorded",
                service.decide(TenantContext.getTenantIdAsObject(), contractId, req, TenantContext.getCurrentUserId())));
    }
}
