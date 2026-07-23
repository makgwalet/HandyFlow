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
import za.co.handyflow.platform.ap.application.internal.*;
import za.co.handyflow.platform.ap.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
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
    private final ApRecurringBillService recurringBillService;
    private final ApSupplierBankingService supplierBankingService;
    private final ApRemittanceEmailService remittanceEmailService;
    private final ApPdfGenerator apPdfGenerator;

    // ── Summary ───────────────────────────────────────────────────────────────

    @GetMapping("/summary")
    @PreAuthorize("hasAnyAuthority('AP_READ','BILLING_READ')")
    @Operation(summary = "AP dashboard summary — outstanding, overdue, due this week/month")
    public ResponseEntity<ApiResponse<ApSummaryResponse>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success(
                apService.getSummary(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/aging")
    @PreAuthorize("hasAnyAuthority('AP_READ','BILLING_READ')")
    @Operation(summary = "AP aging report — outstanding bills (APPROVED + OVERDUE) bucketed by days overdue, same 30/60/90 boundaries as Accounting's AR aging")
    public ResponseEntity<ApiResponse<ApAgingReportResponse>> getAgingReport() {
        return ResponseEntity.ok(ApiResponse.success(
                apService.getAgingReport(TenantContext.getTenantIdAsObject())));
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
        BillResponse created = apService.createBill(TenantContext.getTenantIdAsObject(),
                TenantContext.getCurrentUserId(), req);
        String message = created.possibleDuplicateWarning() != null
                ? created.possibleDuplicateWarning()
                : "Bill created";
        return ResponseEntity.status(201).body(ApiResponse.success(message, created));
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
    @Operation(summary = "Approve a bill — posts accounting journal entry (debit expense, credit AP). "
            + "Bills above the second-approval threshold require two approvals from different people: "
            + "the first moves status to SECOND_APPROVAL with no journal posted yet; the second "
            + "(by a different user) posts the journal and moves status to APPROVED.")
    public ResponseEntity<ApiResponse<BillResponse>> approveBill(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Bill approved",
                apService.approveBill(TenantContext.getTenantIdAsObject(), id,
                        TenantContext.getCurrentUserId())));
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

    // ── Recurring Bill Templates ─────────────────────────────────────────────

    @GetMapping("/recurring-templates")
    @PreAuthorize("hasAnyAuthority('AP_READ','BILLING_READ')")
    @Operation(summary = "List recurring bill templates")
    public ResponseEntity<ApiResponse<List<RecurringBillTemplateResponse>>> getRecurringTemplates() {
        return ResponseEntity.ok(ApiResponse.success(
                recurringBillService.getTemplates(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/recurring-templates")
    @PreAuthorize("hasAnyAuthority('AP_MANAGE','BILLING_MANAGE')")
    @Operation(summary = "Create a recurring bill template — generates a DRAFT bill automatically each month, leadDays before the due date")
    public ResponseEntity<ApiResponse<RecurringBillTemplateResponse>> createRecurringTemplate(
            @Valid @RequestBody CreateRecurringBillTemplateRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Recurring bill template created",
                recurringBillService.createTemplate(TenantContext.getTenantIdAsObject(),
                        TenantContext.getCurrentUserId(), req)));
    }

    @PutMapping("/recurring-templates/{id}")
    @PreAuthorize("hasAnyAuthority('AP_MANAGE','BILLING_MANAGE')")
    @Operation(summary = "Update a recurring bill template")
    public ResponseEntity<ApiResponse<RecurringBillTemplateResponse>> updateRecurringTemplate(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRecurringBillTemplateRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Recurring bill template updated",
                recurringBillService.updateTemplate(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/recurring-templates/{id}/pause")
    @PreAuthorize("hasAnyAuthority('AP_MANAGE','BILLING_MANAGE')")
    @Operation(summary = "Pause a recurring bill template — stops generating new bills until resumed")
    public ResponseEntity<ApiResponse<RecurringBillTemplateResponse>> pauseRecurringTemplate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Template paused",
                recurringBillService.pauseTemplate(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/recurring-templates/{id}/resume")
    @PreAuthorize("hasAnyAuthority('AP_MANAGE','BILLING_MANAGE')")
    @Operation(summary = "Resume a paused recurring bill template")
    public ResponseEntity<ApiResponse<RecurringBillTemplateResponse>> resumeRecurringTemplate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Template resumed",
                recurringBillService.resumeTemplate(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/recurring-templates/{id}/generate-now")
    @PreAuthorize("hasAnyAuthority('AP_MANAGE','BILLING_MANAGE')")
    @Operation(summary = "Generate a bill from this template immediately, without waiting for the daily schedule — useful for testing or an urgent one-off need")
    public ResponseEntity<ApiResponse<BillResponse>> generateRecurringBillNow(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Bill generated",
                recurringBillService.generateNow(TenantContext.getTenantIdAsObject(), id)));
    }

    // ── Supplier Banking ─────────────────────────────────────────────────────

    @GetMapping("/suppliers/banking")
    @PreAuthorize("hasAnyAuthority('AP_READ','BILLING_READ')")
    @Operation(summary = "List configured supplier banking details, used by the EFT batch CSV export")
    public ResponseEntity<ApiResponse<List<SupplierBankingResponse>>> getSupplierBanking() {
        return ResponseEntity.ok(ApiResponse.success(
                supplierBankingService.getAll(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/suppliers/known-names")
    @PreAuthorize("hasAnyAuthority('AP_READ','BILLING_READ')")
    @Operation(summary = "Distinct supplier names seen across existing bills — for picking a name to attach banking details to, avoiding a typo'd mismatch")
    public ResponseEntity<ApiResponse<List<String>>> getKnownSupplierNames() {
        return ResponseEntity.ok(ApiResponse.success(
                supplierBankingService.getKnownSupplierNames(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/suppliers/banking")
    @PreAuthorize("hasAnyAuthority('AP_MANAGE','BILLING_MANAGE')")
    @Operation(summary = "Add banking details for a supplier, matched by name to bills — fixes the EFT CSV export, which previously always exported blank account/branch columns")
    public ResponseEntity<ApiResponse<SupplierBankingResponse>> createSupplierBanking(
            @Valid @RequestBody SupplierBankingRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Supplier banking details added",
                supplierBankingService.create(TenantContext.getTenantIdAsObject(),
                        TenantContext.getCurrentUserId(), req)));
    }

    @PutMapping("/suppliers/banking/{id}")
    @PreAuthorize("hasAnyAuthority('AP_MANAGE','BILLING_MANAGE')")
    @Operation(summary = "Update a supplier's banking details")
    public ResponseEntity<ApiResponse<SupplierBankingResponse>> updateSupplierBanking(
            @PathVariable UUID id, @Valid @RequestBody SupplierBankingRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Supplier banking details updated",
                supplierBankingService.update(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @DeleteMapping("/suppliers/banking/{id}")
    @PreAuthorize("hasAnyAuthority('AP_MANAGE','BILLING_MANAGE')")
    @Operation(summary = "Remove a supplier's banking details")
    public ResponseEntity<ApiResponse<Void>> deleteSupplierBanking(@PathVariable UUID id) {
        supplierBankingService.delete(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Supplier banking details removed", null));
    }

    // ── PDFs ──────────────────────────────────────────────────────────────────

    @GetMapping("/bills/{id}/remittance")
    @PreAuthorize("hasAnyAuthority('AP_READ','BILLING_READ')")
    @Operation(summary = "Download remittance advice PDF for a directly-paid bill — 400 if the bill isn't PAID")
    public ResponseEntity<byte[]> downloadBillRemittance(@PathVariable UUID id) {
        byte[] pdf = apPdfGenerator.generateBillRemittance(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=remittance-advice.pdf")
                .body(pdf);
    }

    @PostMapping("/bills/{id}/send-remittance")
    @PreAuthorize("hasAnyAuthority('AP_MANAGE','BILLING_MANAGE')")
    @Operation(summary = "Email the remittance advice PDF to the supplier — requires an email configured on the Suppliers tab, and the bill must be PAID")
    public ResponseEntity<ApiResponse<Void>> sendBillRemittance(@PathVariable UUID id) {
        remittanceEmailService.sendBillRemittance(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Remittance email sent", null));
    }

    @GetMapping("/batches/{id}/advice")
    @PreAuthorize("hasAnyAuthority('AP_READ','BILLING_READ')")
    @Operation(summary = "Download a human-readable batch payment advice PDF for internal sign-off")
    public ResponseEntity<byte[]> downloadBatchAdvice(@PathVariable UUID id) {
        byte[] pdf = apPdfGenerator.generateBatchAdvice(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=batch-payment-advice.pdf")
                .body(pdf);
    }

    @GetMapping("/batches/{id}/remittance")
    @PreAuthorize("hasAnyAuthority('AP_READ','BILLING_READ')")
    @Operation(summary = "Download remittance advice PDF for one supplier's bills within this batch")
    public ResponseEntity<byte[]> downloadBatchRemittance(
            @PathVariable UUID id, @RequestParam String supplierName) {
        byte[] pdf = apPdfGenerator.generateBatchRemittance(
                TenantContext.getTenantIdAsObject(), id, supplierName);
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=remittance-advice.pdf")
                .body(pdf);
    }

    @PostMapping("/batches/{id}/send-remittance")
    @PreAuthorize("hasAnyAuthority('AP_MANAGE','BILLING_MANAGE')")
    @Operation(summary = "Email one supplier's remittance advice PDF for this batch — requires an email configured on the Suppliers tab")
    public ResponseEntity<ApiResponse<Void>> sendBatchRemittance(
            @PathVariable UUID id, @RequestParam String supplierName) {
        remittanceEmailService.sendBatchRemittance(TenantContext.getTenantIdAsObject(), id, supplierName);
        return ResponseEntity.ok(ApiResponse.success("Remittance email sent", null));
    }

    @GetMapping("/suppliers/statement")
    @PreAuthorize("hasAnyAuthority('AP_READ','BILLING_READ')")
    @Operation(summary = "Download a supplier statement PDF — matched by supplier name, since supplierId is frequently unset")
    public ResponseEntity<byte[]> downloadSupplierStatement(@RequestParam String supplierName) {
        byte[] pdf = apPdfGenerator.generateSupplierStatement(
                TenantContext.getTenantIdAsObject(), supplierName);
        return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=supplier-statement.pdf")
                .body(pdf);
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