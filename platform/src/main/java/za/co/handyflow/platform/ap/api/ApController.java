package za.co.handyflow.platform.ap.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.ap.application.internal.ApService;
import za.co.handyflow.platform.ap.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.UUID;

/**
 * Security fix: all endpoints used BILLING_READ/BILLING_MANAGE.
 * AP is a distinct module — the correct permissions are AP_READ and AP_MANAGE
 * (seeded in V34__ap_permissions.sql). Using BILLING_* permissions means anyone
 * with billing access can also manage AP, which are separate concerns.
 *
 * hasAnyAuthority() is used to remain backward-compatible with tenants that
 * already have BILLING_* grants configured while the AP_* grants propagate.
 */
@RestController
@RequestMapping("/api/v1/ap")
@RequiredArgsConstructor
@Tag(name = "Accounts Payable", description = "Supplier bills, EFT batches and payment tracking")
public class ApController {

    private final ApService apService;

    // ── Summary ───────────────────────────────────────────────────────────────

    @GetMapping("/summary")
    @PreAuthorize("hasAnyAuthority('AP_READ','BILLING_READ')")
    @Operation(summary = "AP dashboard summary — outstanding, overdue, due this week/month")
    public ResponseEntity<ApiResponse<ApSummaryResponse>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success(
                apService.getSummary(TenantContext.getTenantIdAsObject())));
    }

    // ── Bills ─────────────────────────────────────────────────────────────────

    @GetMapping("/bills")
    @PreAuthorize("hasAnyAuthority('AP_READ','BILLING_READ')")
    @Operation(summary = "List bills — filter by status: DRAFT | APPROVED | PAID | OVERDUE | CANCELLED")
    public ResponseEntity<ApiResponse<Page<BillResponse>>> getBills(
            @RequestParam(required = false) String status,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                apService.getBills(TenantContext.getTenantIdAsObject(), status, pageable)));
    }

    @GetMapping("/bills/{id}")
    @PreAuthorize("hasAnyAuthority('AP_READ','BILLING_READ')")
    @Operation(summary = "Get bill detail")
    public ResponseEntity<ApiResponse<BillResponse>> getBill(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                apService.getBill(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/bills")
    @PreAuthorize("hasAnyAuthority('AP_MANAGE','BILLING_MANAGE')")
    @Operation(summary = "Create a new supplier bill")
    public ResponseEntity<ApiResponse<BillResponse>> createBill(
            @Valid @RequestBody CreateBillRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Bill created",
                apService.createBill(TenantContext.getTenantIdAsObject(),
                        TenantContext.getCurrentUserId(), req)));
    }

    @PutMapping("/bills/{id}")
    @PreAuthorize("hasAnyAuthority('AP_MANAGE','BILLING_MANAGE')")
    @Operation(summary = "Update a DRAFT bill")
    public ResponseEntity<ApiResponse<BillResponse>> updateBill(
            @PathVariable UUID id,
            @RequestBody UpdateBillRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Bill updated",
                apService.updateBill(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/bills/{id}/approve")
    @PreAuthorize("hasAnyAuthority('AP_MANAGE','BILLING_MANAGE')")
    @Operation(summary = "Approve a bill — posts accounting journal entry (debit expense, credit AP)")
    public ResponseEntity<ApiResponse<BillResponse>> approveBill(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Bill approved",
                apService.approveBill(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/bills/{id}/pay")
    @PreAuthorize("hasAnyAuthority('AP_MANAGE','BILLING_MANAGE')")
    @Operation(summary = "Mark bill as paid directly — posts payment journal entry")
    public ResponseEntity<ApiResponse<BillResponse>> payBill(
            @PathVariable UUID id,
            @RequestBody PayBillRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Bill marked as paid",
                apService.payBill(TenantContext.getTenantIdAsObject(), id, req,
                        TenantContext.getCurrentUserId())));
    }

    @PostMapping("/bills/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('AP_MANAGE','BILLING_MANAGE')")
    @Operation(summary = "Cancel a bill")
    public ResponseEntity<ApiResponse<BillResponse>> cancelBill(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Bill cancelled",
                apService.cancelBill(TenantContext.getTenantIdAsObject(), id)));
    }

    // ── Evidence uploads ──────────────────────────────────────────────────────

    @PostMapping("/bills/{id}/attachment")
    @PreAuthorize("hasAnyAuthority('AP_MANAGE','BILLING_MANAGE')")
    @Operation(summary = "Upload supplier invoice document (PDF/image as base64)")
    public ResponseEntity<ApiResponse<BillResponse>> uploadAttachment(
            @PathVariable UUID id,
            @Valid @RequestBody UploadEvidenceRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Attachment uploaded",
                apService.uploadBillAttachment(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/bills/{id}/pop")
    @PreAuthorize("hasAnyAuthority('AP_MANAGE','BILLING_MANAGE')")
    @Operation(summary = "Upload proof of payment for a bill")
    public ResponseEntity<ApiResponse<BillResponse>> uploadBillPop(
            @PathVariable UUID id,
            @Valid @RequestBody UploadEvidenceRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Proof of payment uploaded",
                apService.uploadBillPop(TenantContext.getTenantIdAsObject(), id, req,
                        TenantContext.getCurrentUserId())));
    }

    // ── EFT Batches ───────────────────────────────────────────────────────────

    @GetMapping("/batches")
    @PreAuthorize("hasAnyAuthority('AP_READ','BILLING_READ')")
    @Operation(summary = "List EFT batches")
    public ResponseEntity<ApiResponse<Page<BatchResponse>>> getBatches(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                apService.getBatches(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @GetMapping("/batches/{id}")
    @PreAuthorize("hasAnyAuthority('AP_READ','BILLING_READ')")
    @Operation(summary = "Get EFT batch detail with all included bills")
    public ResponseEntity<ApiResponse<BatchResponse>> getBatch(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                apService.getBatch(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/batches")
    @PreAuthorize("hasAnyAuthority('AP_MANAGE','BILLING_MANAGE')")
    @Operation(summary = "Create EFT batch — group approved bills for bulk bank payment")
    public ResponseEntity<ApiResponse<BatchResponse>> createBatch(
            @Valid @RequestBody CreateBatchRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("EFT batch created",
                apService.createBatch(TenantContext.getTenantIdAsObject(),
                        TenantContext.getCurrentUserId(), req)));
    }

    @PostMapping("/batches/{id}/submit")
    @PreAuthorize("hasAnyAuthority('AP_MANAGE','BILLING_MANAGE')")
    @Operation(summary = "Mark batch as submitted to bank")
    public ResponseEntity<ApiResponse<BatchResponse>> submitBatch(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Batch submitted",
                apService.submitBatch(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/batches/{id}/confirm-paid")
    @PreAuthorize("hasAnyAuthority('AP_MANAGE','BILLING_MANAGE')")
    @Operation(summary = "Confirm batch payment received — marks all bills paid, posts journal entries")
    public ResponseEntity<ApiResponse<BatchResponse>> confirmBatchPaid(
            @PathVariable UUID id,
            @RequestBody ConfirmBatchPaidRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Batch payment confirmed",
                apService.confirmBatchPaid(TenantContext.getTenantIdAsObject(), id, req,
                        TenantContext.getCurrentUserId())));
    }

    @PostMapping("/batches/{id}/pop")
    @PreAuthorize("hasAnyAuthority('AP_MANAGE','BILLING_MANAGE')")
    @Operation(summary = "Upload proof of payment / remittance advice for the batch")
    public ResponseEntity<ApiResponse<BatchResponse>> uploadBatchPop(
            @PathVariable UUID id,
            @Valid @RequestBody UploadEvidenceRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Batch POP uploaded",
                apService.uploadBatchPop(TenantContext.getTenantIdAsObject(), id, req,
                        TenantContext.getCurrentUserId())));
    }

    @GetMapping("/batches/{id}/export")
    @PreAuthorize("hasAnyAuthority('AP_READ','BILLING_READ')")
    @Operation(summary = "Export batch as CSV for bank EFT upload (Nedbank/Standard Bank format)")
    public ResponseEntity<byte[]> exportBatch(@PathVariable UUID id) {
        String csv = apService.exportBatchCsv(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"eft-batch-" + id + ".csv\"")
                .body(csv.getBytes());
    }

    @PostMapping("/batches/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('AP_MANAGE','BILLING_MANAGE')")
    @Operation(summary = "Cancel a DRAFT or SUBMITTED batch — releases bills back to approved state")
    public ResponseEntity<ApiResponse<BatchResponse>> cancelBatch(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Batch cancelled",
                apService.cancelBatch(TenantContext.getTenantIdAsObject(), id)));
    }
}
