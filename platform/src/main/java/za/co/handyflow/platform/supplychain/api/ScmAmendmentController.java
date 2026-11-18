package za.co.handyflow.platform.supplychain.api;

import za.co.handyflow.platform.billing.FeatureGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.supplychain.application.internal.PoAmendmentService;
import za.co.handyflow.platform.supplychain.application.internal.PoVersionService;
import za.co.handyflow.platform.supplychain.domain.model.ScPurchaseOrder;
import za.co.handyflow.platform.supplychain.dto.*;

import java.util.List;
import java.util.UUID;

/** PO revision history, amendments, and DRAFT line editing — kept separate so ScmController stays unchanged. */
@RestController
@RequestMapping("/api/v1/supply-chain")
@RequiredArgsConstructor
@Tag(name = "Supply Chain", description = "PO versioning and amendments")
public class ScmAmendmentController {

    private final FeatureGuard featureGuard;
    private final PoAmendmentService amendmentService;
    private final PoVersionService versionService;

    // ── DRAFT line editing ────────────────────────────────────────────────────


    /** Module entitlement: a company whose plan does not include Supply Chain cannot reach any endpoint here. Runs before every handler. */
    @ModelAttribute
    void requireSupplyChainModule() { featureGuard.requireModule("supply_chain"); }

    @PutMapping("/purchase-orders/{id}/lines/{lineId}")
    @PreAuthorize("hasAuthority('SCM_ORDER')")
    @Operation(summary = "Edit a line on a DRAFT purchase order")
    public ResponseEntity<ApiResponse<ScPurchaseOrder>> updateDraftLine(
            @PathVariable UUID id, @PathVariable UUID lineId, @RequestBody UpdateDraftLineRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Line updated",
                amendmentService.updateDraftLine(TenantContext.getTenantIdAsObject(), id, lineId, req)));
    }

    @DeleteMapping("/purchase-orders/{id}/lines/{lineId}")
    @PreAuthorize("hasAuthority('SCM_ORDER')")
    @Operation(summary = "Remove a line from a DRAFT purchase order")
    public ResponseEntity<ApiResponse<ScPurchaseOrder>> removeDraftLine(
            @PathVariable UUID id, @PathVariable UUID lineId) {
        return ResponseEntity.ok(ApiResponse.success("Line removed",
                amendmentService.removeDraftLine(TenantContext.getTenantIdAsObject(), id, lineId)));
    }

    // ── Versions ──────────────────────────────────────────────────────────────

    @GetMapping("/purchase-orders/{id}/versions")
    @PreAuthorize("hasAuthority('SCM_READ')")
    @Operation(summary = "Revision history of a purchase order")
    public ResponseEntity<ApiResponse<List<PoVersionResponse>>> listVersions(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                versionService.list(TenantContext.getTenantIdAsObject(), id)));
    }

    @GetMapping("/purchase-orders/{id}/versions/{revision}")
    @PreAuthorize("hasAuthority('SCM_READ')")
    @Operation(summary = "Full snapshot (header + lines) of one revision")
    public ResponseEntity<ApiResponse<PoVersionResponse>> getVersion(@PathVariable UUID id, @PathVariable int revision) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                versionService.get(TenantContext.getTenantIdAsObject(), id, revision)));
    }

    // ── Amendments ────────────────────────────────────────────────────────────

    @GetMapping("/purchase-orders/{id}/amendments")
    @PreAuthorize("hasAuthority('SCM_READ')")
    @Operation(summary = "Amendments raised against a purchase order")
    public ResponseEntity<ApiResponse<List<PoAmendmentResponse>>> listAmendments(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                amendmentService.list(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/purchase-orders/{id}/amendments")
    @PreAuthorize("hasAuthority('SCM_ORDER')")
    @Operation(summary = "Raise an amendment to an approved PO — material changes need approval before they apply")
    public ResponseEntity<ApiResponse<PoAmendmentResponse>> createAmendment(
            @PathVariable UUID id, @RequestBody CreatePoAmendmentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Amendment raised",
                amendmentService.create(TenantContext.getTenantIdAsObject(), TenantContext.getCurrentUserId(),
                        TenantContext.getCurrentUserName(), id, req)));
    }

    @PostMapping("/po-amendments/{amendmentId}/approve")
    @PreAuthorize("hasAuthority('SCM_ADMIN')")
    @Operation(summary = "Approve an amendment — applies it to the PO once the approval engine reports APPROVED")
    public ResponseEntity<ApiResponse<PoAmendmentResponse>> approve(
            @PathVariable UUID amendmentId, @RequestBody(required = false) AmendmentDecisionRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Amendment decision recorded",
                amendmentService.approve(TenantContext.getTenantIdAsObject(), amendmentId,
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName(),
                        req != null ? req.note() : null)));
    }

    @PostMapping("/po-amendments/{amendmentId}/reject")
    @PreAuthorize("hasAuthority('SCM_ADMIN')")
    @Operation(summary = "Reject an amendment — the PO stays exactly as approved")
    public ResponseEntity<ApiResponse<PoAmendmentResponse>> reject(
            @PathVariable UUID amendmentId, @Valid @RequestBody AmendmentDecisionRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Amendment rejected",
                amendmentService.reject(TenantContext.getTenantIdAsObject(), amendmentId,
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName(), req.note())));
    }

    @PostMapping("/po-amendments/{amendmentId}/withdraw")
    @PreAuthorize("hasAuthority('SCM_ORDER')")
    @Operation(summary = "Withdraw your own pending amendment")
    public ResponseEntity<ApiResponse<PoAmendmentResponse>> withdraw(
            @PathVariable UUID amendmentId, @RequestBody(required = false) AmendmentDecisionRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Amendment withdrawn",
                amendmentService.withdraw(TenantContext.getTenantIdAsObject(), amendmentId,
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName(),
                        req != null ? req.note() : null)));
    }
}
