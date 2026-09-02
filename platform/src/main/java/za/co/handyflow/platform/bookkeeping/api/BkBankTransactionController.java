package za.co.handyflow.platform.bookkeeping.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.bookkeeping.application.internal.BkBankTransactionService;
import za.co.handyflow.platform.bookkeeping.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookkeeping/clients/{clientId}")
@RequiredArgsConstructor
@Tag(name = "Bookkeeping - Bank Transactions", description = "CSV import, match-candidate suggestion, and reconciliation for a client's bank feed")
public class BkBankTransactionController {

    private final BkBankTransactionService transactionService;
    private final FeatureGuard featureGuard;

    @GetMapping("/bank-transactions")
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_READ','BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<Page<BkBankTransactionResponse>>> getTransactions(
            @PathVariable UUID clientId, @RequestParam(required = false) UUID bankAccountId,
            @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.ok(ApiResponse.success(
                transactionService.getTransactions(TenantContext.getTenantIdAsObject(), clientId, bankAccountId, pageable)));
    }

    @PostMapping("/bank-transactions/import")
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<ImportBkTransactionsResponse>> importTransactions(
            @PathVariable UUID clientId, @Valid @RequestBody ImportBkTransactionsRequest request) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Bank transactions imported",
                transactionService.importTransactions(TenantContext.getTenantIdAsObject(), clientId, request)));
    }

    @GetMapping("/bank-transactions/{id}/match-candidates")
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_READ','BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<List<MatchCandidateResponse>>> getMatchCandidates(
            @PathVariable UUID clientId, @PathVariable UUID id) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.ok(ApiResponse.success(
                transactionService.getMatchCandidates(TenantContext.getTenantIdAsObject(), clientId, id)));
    }

    @PostMapping("/bank-transactions/{id}/reconcile")
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<BkBankTransactionResponse>> reconcile(
            @PathVariable UUID clientId, @PathVariable UUID id, @Valid @RequestBody ReconcileBkTransactionRequest request) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.ok(ApiResponse.success("Transaction reconciled",
                transactionService.reconcileTransaction(TenantContext.getTenantIdAsObject(), clientId, id, request.journalLineId())));
    }

    @PostMapping("/bank-transactions/{id}/reconcile-new")
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<BkBankTransactionResponse>> reconcileWithNewJournal(
            @PathVariable UUID clientId, @PathVariable UUID id, @Valid @RequestBody ReconcileBkTransactionWithNewJournalRequest request) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.ok(ApiResponse.success("Transaction reconciled with a new journal entry",
                transactionService.reconcileWithNewJournal(TenantContext.getTenantIdAsObject(), clientId, id,
                        TenantContext.getCurrentUserId(), request)));
    }
}
