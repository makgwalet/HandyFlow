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
import za.co.handyflow.platform.bookkeeping.application.internal.BkBankAccountService;
import za.co.handyflow.platform.bookkeeping.dto.BkBankAccountResponse;
import za.co.handyflow.platform.bookkeeping.dto.CreateBkBankAccountRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookkeeping")
@RequiredArgsConstructor
@Tag(name = "Bookkeeping - Bank Accounts", description = "A client's own bank accounts, linked to a chart-of-accounts line before reconciliation")
public class BkBankAccountController {

    private final BkBankAccountService bankAccountService;
    private final FeatureGuard featureGuard;

    @GetMapping("/clients/{clientId}/bank-accounts")
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_READ','BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<Page<BkBankAccountResponse>>> getBankAccounts(
            @PathVariable UUID clientId, @PageableDefault(size = 20) Pageable pageable) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.ok(ApiResponse.success(
                bankAccountService.getBankAccounts(TenantContext.getTenantIdAsObject(), clientId, pageable)));
    }

    @GetMapping("/bank-accounts/{id}")
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_READ','BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<BkBankAccountResponse>> getBankAccount(@PathVariable UUID id) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.ok(ApiResponse.success(bankAccountService.getBankAccount(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/bank-accounts")
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<BkBankAccountResponse>> createBankAccount(@Valid @RequestBody CreateBkBankAccountRequest request) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Bank account created",
                bankAccountService.createBankAccount(TenantContext.getTenantIdAsObject(), request)));
    }

    @PostMapping("/bank-accounts/{id}/link-account")
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<BkBankAccountResponse>> linkAccount(@PathVariable UUID id, @RequestBody Map<String, UUID> body) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.ok(ApiResponse.success("Bank account linked",
                bankAccountService.linkAccount(TenantContext.getTenantIdAsObject(), id, body.get("accountId"))));
    }

    @DeleteMapping("/bank-accounts/{id}")
    @PreAuthorize("hasAuthority('BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteBankAccount(@PathVariable UUID id) {
        featureGuard.requireModule("bookkeeping");
        bankAccountService.deleteBankAccount(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Bank account deleted", null));
    }
}
