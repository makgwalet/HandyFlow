package za.co.handyflow.platform.supplychain.api;

import za.co.handyflow.platform.billing.FeatureGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.application.internal.ContractService;
import za.co.handyflow.platform.supplychain.dto.ContractDtos.*;

import java.util.List;
import java.util.UUID;

/** Supplier contracts. */
@RestController
@RequestMapping("/api/v1/supply-chain/contracts")
@RequiredArgsConstructor
@Tag(name = "Supply Chain", description = "Supplier contracts")
public class ScmContractController {

    private static final String CAN_READ = "hasAnyAuthority('SCM_READ','SCM_CONTRACT_READ','SCM_CONTRACT_MANAGE','SCM_ADMIN')";
    private static final String CAN_MANAGE = "hasAnyAuthority('SCM_CONTRACT_MANAGE','SCM_ADMIN')";

    private final FeatureGuard featureGuard;
    private final ContractService service;

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static <T> ResponseEntity<ApiResponse<T>> ok(String m, T body) { return ResponseEntity.ok(ApiResponse.success(m, body)); }


    /** Module entitlement: a company whose plan does not include Supply Chain cannot reach any endpoint here. Runs before every handler. */
    @ModelAttribute
    void requireSupplyChainModule() { featureGuard.requireModule("supply_chain"); }

    @GetMapping
    @PreAuthorize(CAN_READ)
    @Operation(summary = "Contracts by end date; filter by status, supplier, or those inside their renewal notice window")
    public ResponseEntity<ApiResponse<List<Response>>> list(@RequestParam(required = false) String status, @RequestParam(required = false) UUID supplierId,
                                                            @RequestParam(required = false) Boolean expiringSoon) {
        return ok("Success", service.list(tenant(), status, supplierId, expiringSoon));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<Response>> get(@PathVariable UUID id) { return ok("Success", service.get(tenant(), id)); }

    @PostMapping
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<Response>> create(@RequestBody SaveRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Draft saved",
                service.create(tenant(), TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName(), req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Edit a DRAFT contract (header and priced lines)")
    public ResponseEntity<ApiResponse<Response>> update(@PathVariable UUID id, @RequestBody SaveRequest req) { return ok("Draft saved", service.update(tenant(), id, req)); }

    @PostMapping("/{id}/activate")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Activate — a second person: the drafter cannot, unless nobody else holds SCM_CONTRACT_MANAGE")
    public ResponseEntity<ApiResponse<Response>> activate(@PathVariable UUID id) {
        return ok("Contract active", service.activate(tenant(), id, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName()));
    }

    @PostMapping("/{id}/terminate")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<Response>> terminate(@PathVariable UUID id, @RequestBody ReasonRequest req) {
        return ok("Contract terminated", service.terminate(tenant(), id, req != null ? req.reason() : null));
    }

    @GetMapping("/price-check/purchase-orders/{purchaseOrderId}")
    @PreAuthorize(CAN_READ)
    @Operation(summary = "PO lines priced above the agreed price on an active contract")
    public ResponseEntity<ApiResponse<List<PriceVariance>>> priceCheck(@PathVariable UUID purchaseOrderId) { return ok("Success", service.priceVariances(tenant(), purchaseOrderId)); }
}
