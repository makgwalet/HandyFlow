package za.co.handyflow.platform.supplychain.api;

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
import za.co.handyflow.platform.supplychain.application.internal.ReplenishmentService;
import za.co.handyflow.platform.supplychain.application.internal.ReplenishmentService.Item;
import za.co.handyflow.platform.supplychain.application.internal.ReplenishmentService.RaiseRequest;
import za.co.handyflow.platform.supplychain.dto.RequisitionResponse;

import java.util.List;

@RestController
@RequestMapping("/api/v1/supply-chain/replenishment")
@RequiredArgsConstructor
@Tag(name = "Supply Chain", description = "Replenishment recommendations")
public class ScmReplenishmentController {

    private final ReplenishmentService service;

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('SCM_READ','SCM_REQUISITION_CREATE','SCM_ADMIN')")
    @Operation(summary = "What to reorder, from stock, open orders and requisitions, recent use and actual supplier lead times (recommendations only)")
    public ResponseEntity<ApiResponse<List<Item>>> recommendations() { return ResponseEntity.ok(ApiResponse.success("Success", service.recommendations(tenant()))); }

    @PostMapping("/requisition")
    @PreAuthorize("hasAnyAuthority('SCM_REQUISITION_CREATE','SCM_ADMIN')")
    @Operation(summary = "Raise a DRAFT stock-replenishment requisition from chosen recommendations; it still needs submitting and approval")
    public ResponseEntity<ApiResponse<RequisitionResponse>> raise(@RequestBody RaiseRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Draft requisition raised",
                service.raiseRequisition(tenant(), TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName(), req)));
    }
}
