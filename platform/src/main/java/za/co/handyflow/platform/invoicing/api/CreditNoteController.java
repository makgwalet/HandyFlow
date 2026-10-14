package za.co.handyflow.platform.invoicing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.invoicing.application.internal.CreditNotePdfService;
import za.co.handyflow.platform.invoicing.application.internal.CreditNoteService;
import za.co.handyflow.platform.invoicing.dto.CreateCreditNoteRequest;
import za.co.handyflow.platform.invoicing.dto.CreditNoteResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** FIX: "no credit note PDF" gap. */
@RestController
@RequestMapping("/api/v1/invoicing")
@RequiredArgsConstructor
@Tag(name = "Invoicing - Credit Notes", description = "Credit notes issued against invoices")
public class CreditNoteController {

    private final CreditNoteService creditNoteService;
    private final CreditNotePdfService creditNotePdfService;

    @PostMapping("/invoices/{invoiceId}/credit-notes")
    @PreAuthorize("hasAuthority('CREDIT_NOTE_ISSUE')")
    @Operation(summary = "Issue a credit note against an invoice")
    public ResponseEntity<ApiResponse<CreditNoteResponse>> createCreditNote(
            @PathVariable UUID invoiceId,
            @Valid @RequestBody CreateCreditNoteRequest req) {
        var result = creditNoteService.createCreditNote(TenantContext.getTenantIdAsObject(), invoiceId, req,
                TenantContext.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("ISSUED".equals(result.status()) ? "Credit note issued" : "Credit note waiting for approval", result));
    }

    // ── Finance patch F9: approval of credit notes above the limit ──

    @PostMapping("/credit-notes/{id}/approve")
    @PreAuthorize("hasAuthority('CREDIT_NOTE_APPROVE')")
    @Operation(summary = "Approve a credit note waiting for approval (never your own); it is issued when the last approval is given")
    public ResponseEntity<ApiResponse<CreditNoteResponse>> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Credit note approved",
                creditNoteService.approve(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId())));
    }

    @PostMapping("/credit-notes/{id}/reject")
    @PreAuthorize("hasAuthority('CREDIT_NOTE_APPROVE')")
    @Operation(summary = "Reject a credit note waiting for approval, with the reason")
    public ResponseEntity<ApiResponse<CreditNoteResponse>> reject(@PathVariable UUID id, @RequestBody RejectCreditNoteRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Credit note rejected",
                creditNoteService.reject(TenantContext.getTenantIdAsObject(), id, TenantContext.getCurrentUserId(),
                        req != null ? req.reason() : null)));
    }

    public record RejectCreditNoteRequest(String reason) {}

    @GetMapping("/invoices/{invoiceId}/credit-notes")
    @PreAuthorize("hasAuthority('INVOICE_READ')")
    @Operation(summary = "List credit notes issued against an invoice")
    public ResponseEntity<ApiResponse<List<CreditNoteResponse>>> getCreditNotesForInvoice(
            @PathVariable UUID invoiceId) {
        return ResponseEntity.ok(ApiResponse.success(
                creditNoteService.getCreditNotesForInvoice(TenantContext.getTenantIdAsObject(), invoiceId)));
    }

    @GetMapping("/credit-notes")
    @PreAuthorize("hasAuthority('INVOICE_READ')")
    @Operation(summary = "List all credit notes")
    public ResponseEntity<ApiResponse<Page<CreditNoteResponse>>> getCreditNotes(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                creditNoteService.getCreditNotes(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @GetMapping("/credit-notes/{id}/pdf")
    @PreAuthorize("hasAuthority('INVOICE_READ')")
    @Operation(summary = "Download credit note PDF")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable UUID id) {
        var tenantId = TenantContext.getTenantIdAsObject();
        var creditNote = creditNoteService.getCreditNote(tenantId, id);
        // Finance patch F9: a credit note waiting for approval, or rejected, is not a document to hand out.
        if (!"ISSUED".equals(creditNote.status()))
            throw new za.co.handyflow.platform.shared.HandyFlowException(
                    "This credit note has not been issued", org.springframework.http.HttpStatus.CONFLICT, "NOT_ISSUED");
        byte[] pdf = creditNotePdfService.generateCreditNotePdf(id, tenantId);
        String filename = creditNote.creditNoteNumber() + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(pdf);
    }
}