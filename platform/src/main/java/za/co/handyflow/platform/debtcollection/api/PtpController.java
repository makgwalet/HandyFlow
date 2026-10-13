package za.co.handyflow.platform.debtcollection.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.debtcollection.application.internal.PtpService;
import za.co.handyflow.platform.debtcollection.application.internal.PtpService.PtpMetrics;
import za.co.handyflow.platform.debtcollection.application.internal.WorkItemService;
import za.co.handyflow.platform.debtcollection.domain.model.PromiseToPay;
import za.co.handyflow.platform.debtcollection.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Promise-to-pay engine API. Case-scoped create/list live under /cases/{caseId}/ptps; everything else is addressed by ptpId. */
@RestController
@RequestMapping("/api/v1/debtcollection")
@RequiredArgsConstructor
@Tag(name = "Debt Collection - Promise to Pay", description = "Structured payment commitments, reconciliation and metrics")
public class PtpController {

    private final PtpService ptpService;
    private final WorkItemService workItemService;
    private final FeatureGuard featureGuard;

    @PostMapping("/cases/{caseId}/ptps")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Record a promise to pay (one-off or recurring series); optionally clears a work item")
    public ResponseEntity<ApiResponse<PtpResponse>> propose(@PathVariable UUID caseId,
                                                            @Valid @RequestBody ProposePtpRequest req) {
        featureGuard.requireModule("debtcollection");
        TenantId t = TenantContext.getTenantIdAsObject();
        if (req.workItemId() != null) {
            workItemService.assertOwnerOrAdmin(t, req.workItemId(), TenantContext.getCurrentUserId(), isAdmin());
        }
        PromiseToPay p = ptpService.propose(t, caseId, req.amount(), req.dueDate(), req.frequency(),
                req.additionalOccurrences() != null ? req.additionalOccurrences() : 0, req.source(), req.terms(),
                req.graceDays() != null ? req.graceDays() : 2, req.accepted() == null || req.accepted(),
                req.workItemId(), TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Promise recorded", PtpResponse.of(p)));
    }

    @GetMapping("/cases/{caseId}/ptps")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<List<PtpResponse>>> listForCase(@PathVariable UUID caseId) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success(
                ptpService.listForCase(TenantContext.getTenantIdAsObject(), caseId).stream().map(PtpResponse::of).toList()));
    }

    @GetMapping("/ptps/{id}")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<PtpResponse>> get(@PathVariable UUID id) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success(PtpResponse.of(ptpService.get(TenantContext.getTenantIdAsObject(), id))));
    }

    @GetMapping("/ptps/{id}/history")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<List<PtpEventResponse>>> history(@PathVariable UUID id) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success(
                ptpService.history(TenantContext.getTenantIdAsObject(), id).stream().map(PtpEventResponse::of).toList()));
    }

    @GetMapping("/ptps/{id}/receipts")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_READ','DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<List<PtpReceiptResponse>>> receipts(@PathVariable UUID id) {
        featureGuard.requireModule("debtcollection");
        return ResponseEntity.ok(ApiResponse.success(
                ptpService.receipts(TenantContext.getTenantIdAsObject(), id).stream().map(PtpReceiptResponse::of).toList()));
    }

    @PostMapping("/ptps/{id}/accept")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Debtor confirmed a PROPOSED promise — snapshots the baseline balance and starts tracking")
    public ResponseEntity<ApiResponse<PtpResponse>> accept(@PathVariable UUID id) {
        featureGuard.requireModule("debtcollection");
        PromiseToPay p = ptpService.accept(TenantContext.getTenantIdAsObject(), id,
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.ok(ApiResponse.success("Promise accepted", PtpResponse.of(p)));
    }

    @PostMapping("/ptps/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    public ResponseEntity<ApiResponse<PtpResponse>> cancel(@PathVariable UUID id, @Valid @RequestBody CancelPtpRequest req) {
        featureGuard.requireModule("debtcollection");
        PromiseToPay p = ptpService.cancel(TenantContext.getTenantIdAsObject(), id, req.reason(),
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.ok(ApiResponse.success("Promise cancelled", PtpResponse.of(p)));
    }

    @PostMapping("/ptps/{id}/receipts")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Attribute a payment invoicing doesn't show yet to this promise, then re-assess")
    public ResponseEntity<ApiResponse<PtpResponse>> recordReceipt(@PathVariable UUID id,
                                                                  @Valid @RequestBody RecordPtpReceiptRequest req) {
        featureGuard.requireModule("debtcollection");
        PromiseToPay p = ptpService.recordReceipt(TenantContext.getTenantIdAsObject(), id, req.amount(),
                req.receivedDate(), req.reference(), req.notes(),
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Receipt recorded", PtpResponse.of(p)));
    }

    @PostMapping("/ptps/{id}/reconcile")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "Re-check payment now — use when late or corrected payment data may change a BROKEN outcome")
    public ResponseEntity<ApiResponse<PtpResponse>> reconcile(@PathVariable UUID id) {
        featureGuard.requireModule("debtcollection");
        PromiseToPay p = ptpService.reassess(TenantContext.getTenantIdAsObject(), id,
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName());
        return ResponseEntity.ok(ApiResponse.success("Promise re-assessed", PtpResponse.of(p)));
    }

    @GetMapping("/ptps/metrics")
    @PreAuthorize("hasAnyAuthority('DEBTCOLLECTION_MANAGE','DEBTCOLLECTION_ADMIN')")
    @Operation(summary = "PTP performance by count and value for promises resolved in the window (default: last 30 days)")
    public ResponseEntity<ApiResponse<PtpMetrics>> metrics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        featureGuard.requireModule("debtcollection");
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(30);
        return ResponseEntity.ok(ApiResponse.success(ptpService.metrics(TenantContext.getTenantIdAsObject(), start, end)));
    }

    private boolean isAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> "DEBTCOLLECTION_ADMIN".equals(a.getAuthority()));
    }
}
