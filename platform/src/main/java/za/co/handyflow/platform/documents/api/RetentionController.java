package za.co.handyflow.platform.documents.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.documents.application.RetentionViews;
import za.co.handyflow.platform.documents.application.internal.RetentionService;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Records retention, tenant-wide across every module that tracks records. RECORDS_READ to look; RECORDS_MANAGE to hold, set
 * policies and propose a disposal; RECORDS_APPROVE to approve or reject one, and never your own proposal. A support session
 * (impersonation) can look but cannot propose or decide a disposal.
 */
@RestController
@RequestMapping("/api/v1/records/retention")
public class RetentionController {

    private final RetentionService retention;

    public RetentionController(RetentionService retention) { this.retention = retention; }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('RECORDS_READ')")
    public ResponseEntity<ApiResponse<RetentionViews.Summary>> summary() {
        return ResponseEntity.ok(ApiResponse.success(retention.summary(TenantContext.getTenantIdAsObject())));
    }

    /** {@code view}: DUE (default), UPCOMING, HELD, DISPOSED or ALL. */
    @GetMapping("/items")
    @PreAuthorize("hasAuthority('RECORDS_READ')")
    public ResponseEntity<ApiResponse<List<RetentionViews.Item>>> items(@RequestParam(required = false) String view,
                                                                        @RequestParam(required = false) String module,
                                                                        @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(ApiResponse.success(retention.items(TenantContext.getTenantIdAsObject(), view, module, limit)));
    }

    @GetMapping("/items/{id}")
    @PreAuthorize("hasAuthority('RECORDS_READ')")
    public ResponseEntity<ApiResponse<RetentionViews.Item>> item(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(retention.item(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/items/{id}/hold")
    @PreAuthorize("hasAuthority('RECORDS_MANAGE')")
    public ResponseEntity<ApiResponse<RetentionViews.Item>> hold(@PathVariable UUID id, @RequestBody RetentionViews.HoldRequest req) {
        RetentionViews.Item i = retention.setHold(TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.ok(ApiResponse.success(i.legalHold() ? "Legal hold placed" : "Legal hold lifted", i));
    }

    @GetMapping("/policies")
    @PreAuthorize("hasAuthority('RECORDS_READ')")
    public ResponseEntity<ApiResponse<List<RetentionViews.Policy>>> policies() {
        return ResponseEntity.ok(ApiResponse.success(retention.policies(TenantContext.getTenantIdAsObject())));
    }

    /** Creates or replaces the policy for one module, category ('*' for the module's default) and jurisdiction; re-dates its records. */
    @PutMapping("/policies")
    @PreAuthorize("hasAuthority('RECORDS_MANAGE')")
    public ResponseEntity<ApiResponse<RetentionViews.Policy>> savePolicy(@RequestBody RetentionViews.PolicyRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Policy saved",
                retention.savePolicy(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }

    @DeleteMapping("/policies/{id}")
    @PreAuthorize("hasAuthority('RECORDS_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> removePolicy(@PathVariable UUID id) {
        retention.removePolicy(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.ok(ApiResponse.success("Policy removed: its records now follow the module default or 7 years", null));
    }

    @GetMapping("/disposals")
    @PreAuthorize("hasAuthority('RECORDS_READ')")
    public ResponseEntity<ApiResponse<List<RetentionViews.Batch>>> disposals(@RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(ApiResponse.success(retention.batches(TenantContext.getTenantIdAsObject(), limit)));
    }

    @GetMapping("/disposals/{id}")
    @PreAuthorize("hasAuthority('RECORDS_READ')")
    public ResponseEntity<ApiResponse<RetentionViews.Batch>> disposal(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(retention.batch(TenantContext.getTenantIdAsObject(), id)));
    }

    /** Step one: propose records that are due. Nothing is deleted until a different person approves. */
    @PostMapping("/disposals")
    @PreAuthorize("hasAuthority('RECORDS_MANAGE')")
    public ResponseEntity<ApiResponse<RetentionViews.Batch>> propose(@RequestBody RetentionViews.ProposeRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Disposal proposed: someone else must approve it",
                retention.propose(TenantContext.getTenantIdAsObject(), req, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName(),
                        TenantContext.isImpersonation())));
    }

    /** Step two: approve someone else's proposal; the files are deleted now. Also carries on a run that did not finish. */
    @PostMapping("/disposals/{id}/approve")
    @PreAuthorize("hasAuthority('RECORDS_APPROVE')")
    public ResponseEntity<ApiResponse<RetentionViews.Batch>> approve(@PathVariable UUID id, @RequestBody(required = false) RetentionViews.DecisionRequest req) {
        RetentionViews.Batch b = retention.approve(TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId(),
                TenantContext.getCurrentUserName(), TenantContext.isImpersonation());
        return ResponseEntity.ok(ApiResponse.success(b.failedCount() > 0 || b.skippedCount() > 0
                ? "Disposal finished with records skipped or not disposed: see each record" : "Disposal finished", b));
    }

    @PostMapping("/disposals/{id}/reject")
    @PreAuthorize("hasAuthority('RECORDS_APPROVE')")
    public ResponseEntity<ApiResponse<RetentionViews.Batch>> reject(@PathVariable UUID id, @RequestBody RetentionViews.DecisionRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Disposal rejected: the records stay",
                retention.reject(TenantContext.getTenantIdAsObject(), id, req, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName(),
                        TenantContext.isImpersonation())));
    }

    @PostMapping("/disposals/{id}/cancel")
    @PreAuthorize("hasAuthority('RECORDS_MANAGE')")
    public ResponseEntity<ApiResponse<RetentionViews.Batch>> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Disposal request withdrawn",
                retention.cancel(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }
}
