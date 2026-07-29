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
    @PreAuthorize("hasAuthority('INVOICE_CREATE')")
    @Operation(summary = "Issue a credit note against an invoice")
    public ResponseEntity<ApiResponse<CreditNoteResponse>> createCreditNote(
            @PathVariable UUID invoiceId,
            @Valid @RequestBody CreateCreditNoteRequest req) {
        var result = creditNoteService.createCreditNote(TenantContext.getTenantIdAsObject(), invoiceId, req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Credit note issued", result));
    }

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
        byte[] pdf = creditNotePdfService.generateCreditNotePdf(id, tenantId);
        var creditNote = creditNoteService.getCreditNote(tenantId, id);
        String filename = creditNote.creditNoteNumber() + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(pdf);
    }
}