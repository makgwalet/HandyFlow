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
import za.co.handyflow.platform.supplychain.application.internal.SupplierAuditService;
import za.co.handyflow.platform.supplychain.application.internal.SupplierBankChangeService;
import za.co.handyflow.platform.supplychain.application.internal.SupplierLifecycleService;

import java.util.List;
import java.util.UUID;

/**
 * Supplier governance. Each action accepts either its fine-grained authority (SCM_SUPPLIER_*)
 * or SCM_ADMIN, so existing admins keep working and you can start granting the narrower
 * permissions to specific roles without another release.
 */
@RestController
@RequestMapping("/api/v1/supply-chain")
@RequiredArgsConstructor
@Tag(name = "Supply Chain", description = "Supplier lifecycle and bank-detail control")
public class ScmSupplierGovernanceController {

    private final SupplierLifecycleService lifecycle;
    private final SupplierBankChangeService bankChanges;
    private final SupplierAuditService audit;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @PostMapping("/suppliers/{id}/approve")
    @PreAuthorize("hasAnyAuthority('SCM_SUPPLIER_APPROVE','SCM_ADMIN')")
    @Operation(summary = "Approve a supplier awaiting onboarding approval")
    public ResponseEntity<ApiResponse<SupplierStatusResponse>> approve(
            @PathVariable UUID id, @RequestBody(required = false) SupplierReasonRequest req) {
        return ok("Supplier approved", lifecycle.approve(tenant(), id, uid(), uname(), note(req)));
    }

    @PostMapping("/suppliers/{id}/reject")
    @PreAuthorize("hasAnyAuthority('SCM_SUPPLIER_APPROVE','SCM_ADMIN')")
    @Operation(summary = "Reject a supplier awaiting approval (reason required)")
    public ResponseEntity<ApiResponse<SupplierStatusResponse>> reject(
            @PathVariable UUID id, @RequestBody SupplierReasonRequest req) {
        return ok("Supplier rejected", lifecycle.reject(tenant(), id, uid(), uname(), req.reason()));
    }

    @PostMapping("/suppliers/{id}/suspend")
    @PreAuthorize("hasAnyAuthority('SCM_SUPPLIER_BLOCK','SCM_ADMIN')")
    @Operation(summary = "Suspend a supplier (temporary hold — no new POs)")
    public ResponseEntity<ApiResponse<SupplierStatusResponse>> suspend(
            @PathVariable UUID id, @RequestBody SupplierReasonRequest req) {
        return ok("Supplier suspended", lifecycle.suspend(tenant(), id, uid(), uname(), req.reason()));
    }

    @PostMapping("/suppliers/{id}/block")
    @PreAuthorize("hasAnyAuthority('SCM_SUPPLIER_BLOCK','SCM_ADMIN')")
    @Operation(summary = "Block (blacklist) a supplier — no new POs, existing POs cannot be sent")
    public ResponseEntity<ApiResponse<SupplierStatusResponse>> block(
            @PathVariable UUID id, @RequestBody SupplierReasonRequest req) {
        return ok("Supplier blocked", lifecycle.block(tenant(), id, uid(), uname(), req.reason()));
    }

    @PostMapping("/suppliers/{id}/reinstate")
    @PreAuthorize("hasAnyAuthority('SCM_SUPPLIER_BLOCK','SCM_ADMIN')")
    @Operation(summary = "Lift a suspension or block (reason required, audited)")
    public ResponseEntity<ApiResponse<SupplierStatusResponse>> reinstate(
            @PathVariable UUID id, @RequestBody SupplierReasonRequest req) {
        return ok("Supplier reinstated", lifecycle.reinstate(tenant(), id, uid(), uname(), req.reason()));
    }

    @PostMapping("/suppliers/{id}/deactivate")
    @PreAuthorize("hasAnyAuthority('SCM_SUPPLIER_BLOCK','SCM_ADMIN')")
    @Operation(summary = "Deactivate (archive) a supplier")
    public ResponseEntity<ApiResponse<SupplierStatusResponse>> deactivate(
            @PathVariable UUID id, @RequestBody SupplierReasonRequest req) {
        return ok("Supplier deactivated", lifecycle.deactivate(tenant(), id, uid(), uname(), req.reason()));
    }

    @PostMapping("/suppliers/{id}/reopen")
    @PreAuthorize("hasAnyAuthority('SCM_SUPPLIER_APPROVE','SCM_ADMIN')")
    @Operation(summary = "Bring an inactive supplier back (via approval if the tenant requires it)")
    public ResponseEntity<ApiResponse<SupplierStatusResponse>> reopen(
            @PathVariable UUID id, @RequestBody SupplierReasonRequest req) {
        return ok("Supplier reopened", lifecycle.reopen(tenant(), id, uid(), uname(), req.reason()));
    }

    @GetMapping("/suppliers/{id}/status")
    @PreAuthorize("hasAuthority('SCM_READ')")
    public ResponseEntity<ApiResponse<SupplierStatusResponse>> status(@PathVariable UUID id) {
        return ok("Success", lifecycle.status(tenant(), id));
    }

    @GetMapping("/suppliers/{id}/events")
    @PreAuthorize("hasAnyAuthority('SCM_READ','SCM_AUDIT_VIEW')")
    @Operation(summary = "Audit trail for a supplier")
    public ResponseEntity<ApiResponse<List<SupplierEventResponse>>> events(@PathVariable UUID id) {
        lifecycle.status(tenant(), id); // 404 if not this tenant's supplier
        return ok("Success", audit.list(tenant(), id));
    }

    // ── Bank-detail changes ───────────────────────────────────────────────────

    @PostMapping("/suppliers/{id}/bank-changes")
    @PreAuthorize("hasAnyAuthority('SCM_SUPPLIER_MANAGE','SCM_ADMIN')")
    @Operation(summary = "Propose a bank-detail change — applied only after independent verification")
    public ResponseEntity<ApiResponse<BankChangeResponse>> requestBankChange(
            @PathVariable UUID id, @RequestBody CreateBankChangeRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Bank change requested",
                bankChanges.request(tenant(), uid(), uname(), id, req)));
    }

    @GetMapping("/suppliers/{id}/bank-changes")
    @PreAuthorize("hasAnyAuthority('SCM_READ','SCM_AUDIT_VIEW')")
    public ResponseEntity<ApiResponse<List<BankChangeResponse>>> listBankChanges(@PathVariable UUID id) {
        return ok("Success", bankChanges.list(tenant(), id));
    }

    @PostMapping("/bank-changes/{changeId}/verify")
    @PreAuthorize("hasAnyAuthority('SCM_SUPPLIER_BANK_VERIFY','SCM_ADMIN')")
    @Operation(summary = "Verify and apply a bank change (different person from the requester)")
    public ResponseEntity<ApiResponse<BankChangeResponse>> verify(
            @PathVariable UUID changeId, @RequestBody BankChangeDecisionRequest req) {
        return ok("Bank change verified and applied",
                bankChanges.verify(tenant(), changeId, uid(), uname(), req.method(), req.note()));
    }

    @PostMapping("/bank-changes/{changeId}/reject")
    @PreAuthorize("hasAnyAuthority('SCM_SUPPLIER_BANK_VERIFY','SCM_ADMIN')")
    public ResponseEntity<ApiResponse<BankChangeResponse>> rejectBankChange(
            @PathVariable UUID changeId, @RequestBody BankChangeDecisionRequest req) {
        return ok("Bank change rejected", bankChanges.reject(tenant(), changeId, uid(), uname(), req.note()));
    }

    @PostMapping("/bank-changes/{changeId}/cancel")
    @PreAuthorize("hasAnyAuthority('SCM_SUPPLIER_MANAGE','SCM_ADMIN')")
    public ResponseEntity<ApiResponse<BankChangeResponse>> cancelBankChange(@PathVariable UUID changeId) {
        return ok("Bank change cancelled", bankChanges.cancel(tenant(), changeId, uid(), uname()));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static <T> ResponseEntity<ApiResponse<T>> ok(String msg, T data) {
        return ResponseEntity.ok(ApiResponse.success(msg, data));
    }
    private static za.co.handyflow.platform.shared.TenantId tenant() { return TenantContext.getTenantIdAsObject(); }
    private static UUID uid() { return TenantContext.getCurrentUserId(); }
    private static String uname() { return TenantContext.getCurrentUserName(); }
    private static String note(SupplierReasonRequest r) { return r != null ? r.reason() : null; }
}
