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
import za.co.handyflow.platform.supplychain.application.internal.QualityInspectionService;
import za.co.handyflow.platform.supplychain.dto.QualityDtos.*;

import java.util.List;
import java.util.UUID;

/** Quality inspection of received goods. */
@RestController
@RequestMapping("/api/v1/supply-chain/quality-inspections")
@RequiredArgsConstructor
@Tag(name = "Supply Chain", description = "Quality inspection")
public class ScmQualityController {

    private static final String CAN_READ = "hasAnyAuthority('SCM_READ','SCM_QUALITY_MANAGE','SCM_ADMIN')";
    private static final String CAN_MANAGE = "hasAnyAuthority('SCM_QUALITY_MANAGE','SCM_ADMIN')";

    private final FeatureGuard featureGuard;
    private final QualityInspectionService service;

    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static <T> ResponseEntity<ApiResponse<T>> ok(String m, T body) { return ResponseEntity.ok(ApiResponse.success(m, body)); }


    /** Module entitlement: a company whose plan does not include Supply Chain cannot reach any endpoint here. Runs before every handler. */
    @ModelAttribute
    void requireSupplyChainModule() { featureGuard.requireModule("supply_chain"); }

    @GetMapping
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<List<Response>>> list(@RequestParam(required = false) UUID supplierId, @RequestParam(required = false) UUID goodsReceiptId) {
        return ok("Success", service.list(tenant(), supplierId, goodsReceiptId));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_READ)
    public ResponseEntity<ApiResponse<Response>> get(@PathVariable UUID id) { return ok("Success", service.get(tenant(), id)); }

    @PostMapping
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Start an inspection of a posted goods receipt — one at a time per receipt")
    public ResponseEntity<ApiResponse<Response>> start(@RequestBody StartRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Inspection started",
                service.start(tenant(), TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName(), req.goodsReceiptId())));
    }

    @PutMapping("/{id}/results")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Record passed / failed quantities per line (while the inspection is open)")
    public ResponseEntity<ApiResponse<Response>> record(@PathVariable UUID id, @RequestBody RecordRequest req) {
        return ok("Results saved", service.record(tenant(), id, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName(), req));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize(CAN_MANAGE)
    @Operation(summary = "Complete: fixes PASSED / PARTIAL / FAILED and can raise a draft return for the failed quantity")
    public ResponseEntity<ApiResponse<Response>> complete(@PathVariable UUID id, @RequestBody(required = false) CompleteRequest req) {
        return ok("Inspection completed", service.complete(tenant(), id, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName(),
                req != null && req.raiseReturnForFailures()));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(CAN_MANAGE)
    public ResponseEntity<ApiResponse<Response>> cancel(@PathVariable UUID id) { return ok("Inspection cancelled", service.cancel(tenant(), id)); }
}
