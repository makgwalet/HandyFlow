package za.co.handyflow.platform.contracting.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.contracting.application.internal.ContractRiskRuleService;
import za.co.handyflow.platform.contracting.application.internal.ContractRiskService;
import za.co.handyflow.platform.contracting.dto.ContractRiskDtos.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Risk rules (authored and reviewed by the tenant's legal reviewer) and risk checks on contracts. Covered by the module entitlement check on /api/v1/contracts. */
@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
@Tag(name = "Contract risk")
public class ContractRiskController {

    private static final String READ = "hasAnyAuthority('CONTRACTS_READ','CONTRACTS_TEMPLATES','CONTRACTS_MANAGE','CONTRACTS_ADMIN')";
    private static final String AUTHOR = "hasAnyAuthority('CONTRACTS_TEMPLATES','CONTRACTS_ADMIN')";
    private static final String CHECK = "hasAnyAuthority('CONTRACTS_MANAGE','CONTRACTS_ADMIN')";

    private final ContractRiskRuleService rules;
    private final ContractRiskService risk;

    @GetMapping("/risk-rules")
    @PreAuthorize(READ)
    @Operation(summary = "Risk rules, optionally by status")
    public ResponseEntity<ApiResponse<List<RuleResponse>>> list(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success("Risk rules", rules.list(TenantContext.getTenantIdAsObject(), status)));
    }

    @PostMapping("/risk-rules")
    @PreAuthorize(AUTHOR)
    @Operation(summary = "Create a draft risk rule")
    public ResponseEntity<ApiResponse<RuleResponse>> create(@Valid @RequestBody RuleRequest q) {
        return ResponseEntity.ok(ApiResponse.success("Rule created", rules.create(TenantContext.getTenantIdAsObject(), q, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/risk-rules/{id}")
    @PreAuthorize(AUTHOR)
    @Operation(summary = "Edit a draft rule")
    public ResponseEntity<ApiResponse<RuleResponse>> edit(@PathVariable UUID id, @Valid @RequestBody RuleRequest q) {
        return ResponseEntity.ok(ApiResponse.success("Rule updated", rules.edit(TenantContext.getTenantIdAsObject(), id, q)));
    }

    @PostMapping("/risk-rules/{id}/approve")
    @PreAuthorize(AUTHOR)
    @Operation(summary = "Record the legal reviewer's approval of a draft rule")
    public ResponseEntity<ApiResponse<RuleResponse>> approve(@PathVariable UUID id, @Valid @RequestBody ApproveRuleRequest q) {
        return ResponseEntity.ok(ApiResponse.success("Rule approved", rules.approve(TenantContext.getTenantIdAsObject(), id, q)));
    }

    @PostMapping("/risk-rules/{id}/publish")
    @PreAuthorize(AUTHOR)
    @Operation(summary = "Publish an approved rule")
    public ResponseEntity<ApiResponse<RuleResponse>> publish(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Rule published", rules.publish(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/risk-rules/{id}/new-version")
    @PreAuthorize(AUTHOR)
    @Operation(summary = "Start a new draft version of a rule")
    public ResponseEntity<ApiResponse<RuleResponse>> newVersion(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("New version", rules.newVersion(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/risk-rules/{id}/withdraw")
    @PreAuthorize(AUTHOR)
    @Operation(summary = "Withdraw a rule")
    public ResponseEntity<ApiResponse<RuleResponse>> withdraw(@PathVariable UUID id, @Valid @RequestBody WithdrawRuleRequest q) {
        return ResponseEntity.ok(ApiResponse.success("Rule withdrawn", rules.withdraw(TenantContext.getTenantIdAsObject(), id, q.reason())));
    }

    @PostMapping("/{contractId}/risk-assessments")
    @PreAuthorize(CHECK)
    @Operation(summary = "Run a risk check on a contract and store the result")
    public ResponseEntity<ApiResponse<AssessmentResponse>> assess(@PathVariable UUID contractId) {
        return ResponseEntity.ok(ApiResponse.success("Risk check", risk.assess(TenantContext.getTenantIdAsObject(), contractId, TenantContext.getCurrentUserId())));
    }

    @GetMapping("/{contractId}/risk-assessments")
    @PreAuthorize(READ)
    @Operation(summary = "Previous risk checks on a contract")
    public ResponseEntity<ApiResponse<List<AssessmentResponse>>> history(@PathVariable UUID contractId) {
        return ResponseEntity.ok(ApiResponse.success("Risk checks", risk.history(TenantContext.getTenantIdAsObject(), contractId)));
    }
}
