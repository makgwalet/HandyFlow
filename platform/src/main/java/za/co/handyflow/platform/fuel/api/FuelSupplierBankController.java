package za.co.handyflow.platform.fuel.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.application.internal.FuelSupplierBankService;
import za.co.handyflow.platform.fuel.dto.SupplierBankDtos.BankAccountView;
import za.co.handyflow.platform.fuel.dto.SupplierBankDtos.DecisionRequest;
import za.co.handyflow.platform.fuel.dto.SupplierBankDtos.ProposeRequest;
import za.co.handyflow.platform.fuel.dto.SupplierBankDtos.RevealedAccount;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** B04: supplier banking details. Proposed by one person, verified by another. Lists are masked; the full number is its own permission. */
@RestController
@RequestMapping("/api/v1/fuel/suppliers/{supplierId}/bank-accounts")
@RequiredArgsConstructor
@Tag(name = "Fuel Supplier Banking", description = "Supplier bank details with second-person verification")
public class FuelSupplierBankController {

    private final FuelSupplierBankService bank;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public ResponseEntity<ApiResponse<List<BankAccountView>>> list(@PathVariable UUID supplierId) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success(bank.list(TenantContext.getTenantIdAsObject(), supplierId)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('FUEL_SUPPLIER_BANK_EDIT')")
    public ResponseEntity<ApiResponse<BankAccountView>> propose(@PathVariable UUID supplierId, @Valid @RequestBody ProposeRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Bank details proposed. Someone else must verify them before they count.",
                bank.propose(TenantContext.getTenantIdAsObject(), supplierId, req, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("hasAuthority('FUEL_SUPPLIER_BANK_VERIFY')")
    public ResponseEntity<ApiResponse<BankAccountView>> verify(@PathVariable UUID supplierId, @PathVariable UUID id, @Valid @RequestBody DecisionRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Bank details verified",
                bank.verify(TenantContext.getTenantIdAsObject(), supplierId, id, req.note(), TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('FUEL_SUPPLIER_BANK_VERIFY')")
    public ResponseEntity<ApiResponse<BankAccountView>> reject(@PathVariable UUID supplierId, @PathVariable UUID id, @Valid @RequestBody DecisionRequest req) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Bank details rejected",
                bank.reject(TenantContext.getTenantIdAsObject(), supplierId, id, req.note(), TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasAuthority('FUEL_SUPPLIER_BANK_EDIT')")
    public ResponseEntity<ApiResponse<BankAccountView>> withdraw(@PathVariable UUID supplierId, @PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok(ApiResponse.success("Proposal withdrawn",
                bank.withdraw(TenantContext.getTenantIdAsObject(), supplierId, id, TenantContext.getCurrentUserId())));
    }

    /** Shows the full account number. Logged against the user. Never cached. */
    @GetMapping("/{id}/reveal")
    @PreAuthorize("hasAuthority('FUEL_SUPPLIER_BANK_READ')")
    public ResponseEntity<ApiResponse<RevealedAccount>> reveal(@PathVariable UUID supplierId, @PathVariable UUID id) {
        featureGuard.requireModule("fuel");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .body(ApiResponse.success(bank.reveal(TenantContext.getTenantIdAsObject(), supplierId, id, TenantContext.getCurrentUserId())));
    }
}
