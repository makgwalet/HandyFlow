package za.co.handyflow.platform.accounting.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.accounting.application.internal.BankReconciliationService;
import za.co.handyflow.platform.accounting.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Finance patch F12: bank reconciliation report, sign-off per statement, and reusable matching rules.
 * Signing off and reopening need ACCOUNTING_RECON_SIGNOFF (V393); rules are managed with ACCOUNTING_MANAGE.
 */
@RestController
@RequestMapping("/api/v1/accounting")
@RequiredArgsConstructor
@Tag(name = "Bank reconciliation")
public class BankReconciliationController {

    private final BankReconciliationService service;

    @GetMapping("/bank-accounts/{id}/reconciliation")
    @PreAuthorize("hasAuthority('ACCOUNTING_READ')")
    @Operation(summary = "Reconciliation report: statement vs ledger balance, unmatched items on both sides, lines to review")
    public ResponseEntity<ApiResponse<BankReconciliationReport>> report(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asAt) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                service.report(TenantContext.getTenantIdAsObject(), id, asAt != null ? asAt : LocalDate.now())));
    }

    @GetMapping("/bank-accounts/{id}/reconciliations")
    @PreAuthorize("hasAuthority('ACCOUNTING_READ')")
    @Operation(summary = "Sign-off history for a bank account")
    public ResponseEntity<ApiResponse<List<BankReconciliationResponse>>> history(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                service.history(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/bank-accounts/{id}/reconciliations")
    @PreAuthorize("hasAuthority('ACCOUNTING_RECON_SIGNOFF')")
    @Operation(summary = "Sign off the reconciliation up to a statement date")
    public ResponseEntity<ApiResponse<BankReconciliationResponse>> signOff(
            @PathVariable UUID id, @Valid @RequestBody SignOffBankReconciliationRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Reconciliation signed off",
                service.signOff(TenantContext.getTenantIdAsObject(), id, req.periodEnd(), req.note(),
                        TenantContext.getCurrentUserId())));
    }

    @PostMapping("/bank-reconciliations/{id}/reopen")
    @PreAuthorize("hasAuthority('ACCOUNTING_RECON_SIGNOFF')")
    @Operation(summary = "Reopen the latest signed-off reconciliation")
    public ResponseEntity<ApiResponse<BankReconciliationResponse>> reopen(
            @PathVariable UUID id, @Valid @RequestBody ReopenBankReconciliationRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Reconciliation reopened",
                service.reopen(TenantContext.getTenantIdAsObject(), id, req.reason(),
                        TenantContext.getCurrentUserId())));
    }

    @GetMapping("/bank-rules")
    @PreAuthorize("hasAuthority('ACCOUNTING_READ')")
    @Operation(summary = "Bank matching rules")
    public ResponseEntity<ApiResponse<List<BankRuleResponse>>> rules() {
        return ResponseEntity.ok(ApiResponse.success("Success", service.rules(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/bank-rules")
    @PreAuthorize("hasAuthority('ACCOUNTING_MANAGE')")
    @Operation(summary = "Create a bank matching rule")
    public ResponseEntity<ApiResponse<BankRuleResponse>> createRule(@RequestBody BankRuleRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Rule created",
                service.createRule(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId())));
    }

    @PutMapping("/bank-rules/{id}")
    @PreAuthorize("hasAuthority('ACCOUNTING_MANAGE')")
    @Operation(summary = "Change a bank matching rule")
    public ResponseEntity<ApiResponse<BankRuleResponse>> updateRule(@PathVariable UUID id, @RequestBody BankRuleRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Rule updated",
                service.updateRule(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @DeleteMapping("/bank-rules/{id}")
    @PreAuthorize("hasAuthority('ACCOUNTING_MANAGE')")
    @Operation(summary = "Switch a bank matching rule off")
    public ResponseEntity<ApiResponse<Void>> deactivateRule(@PathVariable UUID id) {
        service.deactivateRule(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Rule switched off", null));
    }

    @GetMapping("/bank-accounts/{id}/rules/preview")
    @PreAuthorize("hasAuthority('ACCOUNTING_READ')")
    @Operation(summary = "What the rules would do to the unmatched lines (posts nothing)")
    public ResponseEntity<ApiResponse<List<RuleSuggestionResponse>>> preview(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success", service.preview(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/bank-accounts/{id}/rules/apply")
    @PreAuthorize("hasAuthority('ACCOUNTING_MANAGE')")
    @Operation(summary = "Apply the rules to the chosen lines (all or nothing)")
    public ResponseEntity<ApiResponse<ApplyBankRulesResponse>> apply(
            @PathVariable UUID id, @Valid @RequestBody ApplyBankRulesRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Rules applied",
                service.apply(TenantContext.getTenantIdAsObject(), id, req.transactionIds(),
                        TenantContext.getCurrentUserId())));
    }
}
