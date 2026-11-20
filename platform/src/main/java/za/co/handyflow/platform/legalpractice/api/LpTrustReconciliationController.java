package za.co.handyflow.platform.legalpractice.api;

import io.swagger.v3.oas.annotations.Operation;
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
import za.co.handyflow.platform.legalpractice.application.internal.LpTrustReconciliationService;
import za.co.handyflow.platform.legalpractice.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/** Trust account reconciliation: prepared by one person, approved by another; approval locks the period. */
@RestController
@RequestMapping("/api/v1/legal-practice/trust-reconciliations")
@RequiredArgsConstructor
@Tag(name = "Legal Practice - Trust reconciliation", description = "Reconcile the trust bank account to the trust ledger")
public class LpTrustReconciliationController {

    private static final String VIEW = "hasAnyAuthority('LEGALPRACTICE_TRUST_RECONCILE','LEGALPRACTICE_TRUST_RECONCILE_APPROVE','LEGALPRACTICE_ADMIN')";
    private static final String PREPARE = "hasAnyAuthority('LEGALPRACTICE_TRUST_RECONCILE','LEGALPRACTICE_ADMIN')";
    private static final String APPROVE = "hasAnyAuthority('LEGALPRACTICE_TRUST_RECONCILE_APPROVE','LEGALPRACTICE_ADMIN')";

    private final LpTrustReconciliationService service;
    private final FeatureGuard featureGuard;

    @GetMapping
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<Page<TrustReconciliationSummary>>> list(@PageableDefault(size = 25) Pageable pageable) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getTenantIdAsObject(), pageable)));
    }

    /** The date the trust ledger is locked through, so a screen can warn before someone tries to back-date a transaction. */
    @GetMapping("/lock")
    @PreAuthorize("hasAnyAuthority('LEGALPRACTICE_READ','LEGALPRACTICE_MANAGE','LEGALPRACTICE_TRUST_RECONCILE','LEGALPRACTICE_TRUST_RECONCILE_APPROVE','LEGALPRACTICE_ADMIN')")
    public ResponseEntity<ApiResponse<TrustLockResponse>> lock() {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(service.lock(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/{id}")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<TrustReconciliationResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success(service.get(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize(PREPARE)
    @Operation(summary = "Start a reconciliation to a date, from the bank statement balance at that date")
    public ResponseEntity<ApiResponse<TrustReconciliationResponse>> start(@Valid @RequestBody StartReconciliationRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Reconciliation started", service.start(
                TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @PutMapping("/{id}/statement")
    @PreAuthorize(PREPARE)
    public ResponseEntity<ApiResponse<TrustReconciliationResponse>> updateStatement(@PathVariable UUID id, @Valid @RequestBody UpdateStatementRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Statement balance saved", service.updateStatement(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/{id}/items")
    @PreAuthorize(PREPARE)
    public ResponseEntity<ApiResponse<TrustReconciliationResponse>> addItem(@PathVariable UUID id, @Valid @RequestBody AddReconItemRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Item added",
                service.addItem(TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId())));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @PreAuthorize(PREPARE)
    public ResponseEntity<ApiResponse<TrustReconciliationResponse>> removeItem(@PathVariable UUID id, @PathVariable UUID itemId) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Item removed", service.removeItem(TenantContext.getTenantIdAsObject(), id, itemId)));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize(PREPARE)
    @Operation(summary = "Freeze the figures and send for approval. Only a balanced reconciliation can be submitted")
    public ResponseEntity<ApiResponse<TrustReconciliationResponse>> submit(@PathVariable UUID id) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Submitted for approval", service.submit(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/{id}/return")
    @PreAuthorize(VIEW)
    @Operation(summary = "Send a submitted reconciliation back to draft, with a reason")
    public ResponseEntity<ApiResponse<TrustReconciliationResponse>> returnToDraft(@PathVariable UUID id, @Valid @RequestBody ReturnReconciliationRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Returned to draft", service.returnToDraft(TenantContext.getTenantIdAsObject(), id, req.reason(), TenantContext.getCurrentUserName())));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(APPROVE)
    @Operation(summary = "Approve a submitted reconciliation and lock the period. A different person from the one who submitted it, unless the firm allows self-approval")
    public ResponseEntity<ApiResponse<TrustReconciliationResponse>> approve(@PathVariable UUID id, @RequestBody(required = false) ApproveReconciliationRequest req) {
        featureGuard.requireModule("legalpractice");
        return ResponseEntity.ok(ApiResponse.success("Reconciliation approved", service.approve(
                TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }
}
