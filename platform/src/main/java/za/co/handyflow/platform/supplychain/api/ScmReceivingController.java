package za.co.handyflow.platform.supplychain.api;

import za.co.handyflow.platform.billing.FeatureGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.supplychain.application.internal.GoodsReceivingService;
import za.co.handyflow.platform.supplychain.domain.model.ScGoodsReceipt;
import za.co.handyflow.platform.supplychain.dto.GoodsReceiptLineResponse;
import za.co.handyflow.platform.supplychain.dto.ReverseGoodsReceiptRequest;

import java.util.List;
import java.util.UUID;

/** Receiving visibility and reversal — kept separate so ScmController stays unchanged. */
@RestController
@RequestMapping("/api/v1/supply-chain/goods-receipts")
@RequiredArgsConstructor
@Tag(name = "Supply Chain", description = "Goods receiving controls")
public class ScmReceivingController {

    private final FeatureGuard featureGuard;
    private final GoodsReceivingService receivingService;


    /** Module entitlement: a company whose plan does not include Supply Chain cannot reach any endpoint here. Runs before every handler. */
    @ModelAttribute
    void requireSupplyChainModule() { featureGuard.requireModule("supply_chain"); }

    @GetMapping("/{id}/lines")
    @PreAuthorize("hasAuthority('SCM_READ')")
    @Operation(summary = "Lines of a goods receipt, with delivered / rejected / accepted quantities")
    public ResponseEntity<ApiResponse<List<GoodsReceiptLineResponse>>> getLines(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                receivingService.listLines(TenantContext.getTenantIdAsObject(), id)));
    }

    // Reversing a posted receipt changes stock and PO state, so it is not open to every receiving clerk (SCM_RECEIVE).
    // SCM_RECEIVE_REVERSE can be granted on its own; SCM_ADMIN keeps working, as on every other SCM controller.
    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAnyAuthority('SCM_RECEIVE_REVERSE','SCM_ADMIN')")
    @Operation(summary = "Reverse a posted goods receipt — offsets stock, rolls back PO quantities, records the reason")
    public ResponseEntity<ApiResponse<ScGoodsReceipt>> reverse(
            @PathVariable UUID id, @Valid @RequestBody ReverseGoodsReceiptRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Goods receipt reversed",
                receivingService.reverse(TenantContext.getTenantIdAsObject(), id,
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName(), req.reason())));
    }
}
