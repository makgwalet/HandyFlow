package za.co.handyflow.platform.debtcollection.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.debtcollection.application.internal.CaseAuditService;
import za.co.handyflow.platform.debtcollection.application.internal.RecoveryLedgerService;
import za.co.handyflow.platform.debtcollection.application.internal.RecoveryLedgerService.LedgerSummary;
import za.co.handyflow.platform.debtcollection.domain.model.RecoveryEntry;
import za.co.handyflow.platform.debtcollection.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Recovery ledger and case audit trail. Recording payments and charging fees are MANAGE/ADMIN; reversing a payment
 * and waiving fees are financial determinations restricted to DEBTCOLLECTION_ADMIN. The ledger is append-only —
 * there are no edit or delete endpoints, by design.
 */
@RestController
@RequestMapping("/api/v1/debtcollection")
@RequiredArgsConstructor
@Tag(name = "Debt Collection - Recovery Ledger", description = "Payments, reversals, fees and the case audit trail")
public class RecoveryLedgerController {

    private final RecoveryLedgerService ledgerService;
    private final CaseAuditService auditService;
    private final FeatureGuard featureGuard;

    @PostMapping("/cases/{caseId}/ledger/payments")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Record a payment recovered on this case (idempotent on externalReference)")
    public ResponseEntity<ApiResponse<LedgerEntryResponse>> recordPayment(@PathVariable UUID caseId,
                                                                          @Valid @RequestBody RecordPaymentRequest req) {
        featureGuard.requireModule("debtcollection");
        RecoveryEntry e = ledgerService.recordPayment(tenant(), caseId, req.amount(), req.effectiveDate(), req.source(),
                req.externalReference(), req.allocateToFees(), req.ptpId(), req.notes(),
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Payment recorded", LedgerEntryResponse.of(e)));
    }

    @PostMapping("/ledger/entries/{entryId}/reverse")
    @PreAuthorize("hasAuthority('DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Reverse a payment (e.g. bounced EFT) — appends a REVERSAL; the original stays")
    public ResponseEntity<ApiResponse<LedgerEntryResponse>> reverse(@PathVariable UUID entryId,
                                                                    @Valid @RequestBody LedgerReasonRequest req) {
        featureGuard.requireModule("debtcollection");
        RecoveryEntry e = ledgerService.reversePayment(tenant(), entryId, req.reason(), req.effectiveDate(),
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Payment reversed", LedgerEntryResponse.of(e)));
    }

    @PostMapping("/cases/{caseId}/ledger/fees")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Charge a fee under an APPROVED fee rule — the amount is calculated, never typed")
    public ResponseEntity<ApiResponse<LedgerEntryResponse>> chargeFee(@PathVariable UUID caseId,
                                                                      @Valid @RequestBody ChargeFeeRequest req) {
        featureGuard.requireModule("debtcollection");
        RecoveryEntry e = ledgerService.chargeFee(tenant(), caseId, req.ruleCode(), req.effectiveDate(), req.notes(),
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Fee charged", LedgerEntryResponse.of(e)));
    }

    @PostMapping("/cases/{caseId}/ledger/fee-waivers")
    @PreAuthorize("hasAuthority('DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Waive fees currently owed (supervisor) — reason mandatory")
    public ResponseEntity<ApiResponse<LedgerEntryResponse>> waive(@PathVariable UUID caseId,
                                                                  @Valid @RequestBody WaiveFeesRequest req) {
        featureGuard.requireModule("debtcollection");
        RecoveryEntry e = ledgerService.waiveFees(tenant(), caseId, req.amount(), req.effectiveDate(), req.reason(),
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Fees waived", LedgerEntryResponse.of(e)));
    }

    @GetMapping("/cases/{caseId}/ledger")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<List<LedgerEntryResponse>>> entries(@PathVariable UUID caseId) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success(
                ledgerService.list(tenant(), caseId).stream().map(LedgerEntryResponse::of).toList()));
    }

    @GetMapping("/cases/{caseId}/ledger/summary")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Ledger totals plus live outstanding per invoicing, side by side")
    public ResponseEntity<ApiResponse<LedgerSummary>> summary(@PathVariable UUID caseId) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success(ledgerService.summary(tenant(), caseId)));
    }

    @GetMapping("/cases/{caseId}/audit")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Append-only audit trail of changes to this case, newest first")
    public ResponseEntity<ApiResponse<List<CaseAuditEventResponse>>> audit(@PathVariable UUID caseId) {
        featureGuard.requireModule("debtcollection");
        TenantId t = tenant();
        return ResponseEntity.ok(ApiResponse.success(
                auditService.list(t, caseId).stream().map(CaseAuditEventResponse::of).toList()));
    }

    private TenantId tenant() {
        return TenantContext.getTenantIdAsObject();
    }
}
