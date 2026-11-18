package za.co.handyflow.platform.supplychain.api;

import za.co.handyflow.platform.billing.FeatureGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.application.internal.RequisitionService;
import za.co.handyflow.platform.supplychain.dto.*;

import java.util.List;
import java.util.UUID;

/**
 * Purchase requisitions. Each action accepts its fine-grained authority or an existing one, so
 * current roles keep working while you start granting SCM_REQUISITION_CREATE / _APPROVE to
 * department requesters and approvers.
 */
@RestController
@RequestMapping("/api/v1/supply-chain/requisitions")
@RequiredArgsConstructor
@Tag(name = "Supply Chain", description = "Purchase requisitions")
public class ScmRequisitionController {

    private final FeatureGuard featureGuard;
    private final RequisitionService service;

    private static final String CAN_REQUEST = "hasAnyAuthority('SCM_REQUISITION_CREATE','SCM_ORDER','SCM_ADMIN')";
    private static final String CAN_APPROVE = "hasAnyAuthority('SCM_REQUISITION_APPROVE','SCM_ADMIN')";
    private static final String CAN_CONVERT = "hasAnyAuthority('SCM_PO_CREATE','SCM_ORDER','SCM_ADMIN')";
    private static final String CAN_VIEW = "hasAnyAuthority('SCM_READ','SCM_REQUISITION_CREATE','SCM_REQUISITION_APPROVE','SCM_ORDER','SCM_ADMIN')";


    /** Module entitlement: a company whose plan does not include Supply Chain cannot reach any endpoint here. Runs before every handler. */
    @ModelAttribute
    void requireSupplyChainModule() { featureGuard.requireModule("supply_chain"); }

    @PostMapping
    @PreAuthorize(CAN_REQUEST)
    @Operation(summary = "Create a DRAFT requisition")
    public ResponseEntity<ApiResponse<RequisitionResponse>> create(@RequestBody CreateRequisitionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Requisition created",
                service.create(tenant(), uid(), uname(), req)));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_REQUEST)
    @Operation(summary = "Replace the header and lines of a DRAFT requisition")
    public ResponseEntity<ApiResponse<RequisitionResponse>> update(@PathVariable UUID id, @RequestBody CreateRequisitionRequest req) {
        return ok("Requisition updated", service.update(tenant(), id, uid(), req));
    }

    @GetMapping
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "List requisitions — requesters without SCM_READ see only their own")
    public ResponseEntity<ApiResponse<Page<RequisitionResponse>>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "false") boolean mine,
            @PageableDefault(size = 20) Pageable pageable) {
        return ok("Success", service.list(tenant(), uid(), status, mine, pageable));
    }

    @GetMapping("/suggestions")
    @PreAuthorize("hasAnyAuthority('SCM_READ','SCM_ORDER','SCM_ADMIN')")
    @Operation(summary = "Replenishment suggestions from low stock (suggestions only — nothing is created)")
    public ResponseEntity<ApiResponse<List<RequisitionSuggestionResponse>>> suggestions() {
        return ok("Success", service.suggestions(tenant()));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Requisition detail with lines, stock advisories and the POs raised from it")
    public ResponseEntity<ApiResponse<RequisitionResponse>> get(@PathVariable UUID id) {
        return ok("Success", service.get(tenant(), id, uid()));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize(CAN_REQUEST)
    @Operation(summary = "Submit for approval — runs the stock / open-order / duplicate-demand check")
    public ResponseEntity<ApiResponse<RequisitionResponse>> submit(@PathVariable UUID id) {
        return ok("Requisition submitted", service.submit(tenant(), id, uid(), uname()));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(CAN_APPROVE)
    public ResponseEntity<ApiResponse<RequisitionResponse>> approve(
            @PathVariable UUID id, @RequestBody(required = false) RequisitionDecisionRequest req) {
        return ok("Approval recorded", service.approve(tenant(), id, uid(), uname(), req != null ? req.note() : null));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(CAN_APPROVE)
    public ResponseEntity<ApiResponse<RequisitionResponse>> reject(@PathVariable UUID id, @RequestBody RequisitionDecisionRequest req) {
        return ok("Requisition rejected", service.reject(tenant(), id, uid(), req.note()));
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize(CAN_REQUEST)
    @Operation(summary = "Pull a pending requisition back to DRAFT")
    public ResponseEntity<ApiResponse<RequisitionResponse>> withdraw(@PathVariable UUID id) {
        return ok("Requisition withdrawn", service.withdraw(tenant(), id, uid()));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize(CAN_REQUEST)
    @Operation(summary = "Re-open a rejected requisition for correction")
    public ResponseEntity<ApiResponse<RequisitionResponse>> reopen(@PathVariable UUID id) {
        return ok("Requisition re-opened", service.reopen(tenant(), id, uid()));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(CAN_REQUEST)
    public ResponseEntity<ApiResponse<RequisitionResponse>> cancel(@PathVariable UUID id, @RequestBody RequisitionDecisionRequest req) {
        return ok("Requisition cancelled", service.cancel(tenant(), id, uid(), req.note()));
    }

    @GetMapping("/price-overrides")
    @PreAuthorize("hasAnyAuthority('SCM_PRICE_OVERRIDE_APPROVE','SCM_ADMIN')")
    @Operation(summary = "Buyer price increases beyond tolerance (default: waiting for approval)")
    public ResponseEntity<ApiResponse<List<RequisitionResponse.PriceOverride>>> priceOverrides(@RequestParam(required = false) String status) {
        return ok("Success", service.priceOverrides(tenant(), status));
    }

    public record PriceDecision(boolean approve, String note) {}

    @PostMapping("/price-overrides/{conversionId}/decision")
    @PreAuthorize("hasAnyAuthority('SCM_PRICE_OVERRIDE_APPROVE','SCM_ADMIN')")
    @Operation(summary = "Approve or reject a buyer's price increase beyond tolerance (not the buyer who made it)")
    public ResponseEntity<ApiResponse<Void>> decidePrice(@PathVariable UUID conversionId, @RequestBody PriceDecision body) {
        service.decidePriceOverride(tenant(), conversionId, body.approve(), body.note(), uid(), uname());
        return ResponseEntity.ok(ApiResponse.success(body.approve() ? "Price increase approved" : "Price increase rejected", null));
    }

    @PostMapping("/{id}/convert-to-po")
    @PreAuthorize(CAN_CONVERT)
    @Operation(summary = "Turn (part of) an approved requisition into a DRAFT purchase order with one supplier")
    public ResponseEntity<ApiResponse<RequisitionResponse>> convert(@PathVariable UUID id, @RequestBody ConvertRequisitionRequest req) {
        return ok("Purchase order created", service.convertToPo(tenant(), id, uid(), uname(), req));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String m, T d) { return ResponseEntity.ok(ApiResponse.success(m, d)); }
    private static TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
}
