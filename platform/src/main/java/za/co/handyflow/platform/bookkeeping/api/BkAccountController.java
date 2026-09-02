package za.co.handyflow.platform.bookkeeping.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.bookkeeping.application.internal.BkAccountService;
import za.co.handyflow.platform.bookkeeping.dto.BkAccountResponse;
import za.co.handyflow.platform.bookkeeping.dto.CreateBkAccountRequest;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookkeeping/clients/{clientId}/accounts")
@RequiredArgsConstructor
@Tag(name = "Bookkeeping - Chart of Accounts", description = "A client's own chart of accounts — seeded with a standard chart on first use")
public class BkAccountController {

    private final BkAccountService accountService;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_READ','BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<List<BkAccountResponse>>> getAccounts(@PathVariable UUID clientId) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.ok(ApiResponse.success(accountService.getAccounts(TenantContext.getTenantIdAsObject(), clientId)));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('BOOKKEEPING_MANAGE','BOOKKEEPING_ADMIN')")
    public ResponseEntity<ApiResponse<BkAccountResponse>> createCustomAccount(
            @PathVariable UUID clientId, @Valid @RequestBody CreateBkAccountRequest request) {
        featureGuard.requireModule("bookkeeping");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Account created",
                accountService.createCustomAccount(TenantContext.getTenantIdAsObject(), clientId, request)));
    }
}
