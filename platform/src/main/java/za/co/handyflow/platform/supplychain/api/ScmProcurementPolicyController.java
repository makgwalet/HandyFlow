package za.co.handyflow.platform.supplychain.api;

import za.co.handyflow.platform.billing.FeatureGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.supplychain.application.internal.ProcurementPolicy;
import za.co.handyflow.platform.supplychain.application.internal.ProcurementPolicy.Policy;
import za.co.handyflow.platform.supplychain.application.internal.ProcurementPolicy.View;

/** The company's procurement financial policy: budget VAT basis and the buyer price-increase tolerance. */
@RestController
@RequestMapping("/api/v1/supply-chain/procurement-policy")
@RequiredArgsConstructor
@Tag(name = "Supply Chain", description = "Procurement financial policy")
public class ScmProcurementPolicyController {

    private final FeatureGuard featureGuard;
    private final ProcurementPolicy policy;


    /** Module entitlement: a company whose plan does not include Supply Chain cannot reach any endpoint here. Runs before every handler. */
    @ModelAttribute
    void requireSupplyChainModule() { featureGuard.requireModule("supply_chain"); }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SCM_READ','SCM_ADMIN')")
    @Operation(summary = "Effective policy, the defaults, and whether the company has its own")
    public ResponseEntity<ApiResponse<View>> get() {
        return ResponseEntity.ok(ApiResponse.success("Success", policy.view(TenantContext.getTenantIdAsObject().getValue())));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('SCM_ADMIN')")
    @Operation(summary = "Set the policy — applies to new commitments and conversions and is written to the audit trail")
    public ResponseEntity<ApiResponse<View>> put(@RequestBody Policy body) {
        return ResponseEntity.ok(ApiResponse.success("Policy saved", policy.save(TenantContext.getTenantIdAsObject().getValue(), body,
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }
}
