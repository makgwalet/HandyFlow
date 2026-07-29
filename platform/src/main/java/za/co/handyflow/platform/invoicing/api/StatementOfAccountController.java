package za.co.handyflow.platform.invoicing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.handyflow.platform.invoicing.application.internal.StatementOfAccountPdfService;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.UUID;

/** FIX: "no statement of account PDF" gap. */
@RestController
@RequestMapping("/api/v1/invoicing/customers")
@RequiredArgsConstructor
@Tag(name = "Invoicing - Statement of Account", description = "Rolled-up statement across a customer's invoices")
public class StatementOfAccountController {

    private final StatementOfAccountPdfService statementPdfService;

    @GetMapping("/{customerId}/statement")
    @PreAuthorize("hasAuthority('INVOICE_READ')")
    @Operation(summary = "Download a statement of account PDF for a customer — omit from/to for all-time")
    public ResponseEntity<byte[]> downloadStatement(
            @PathVariable UUID customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var tenantId = TenantContext.getTenantIdAsObject();
        byte[] pdf = statementPdfService.generateStatementPdf(tenantId, customerId, from, to);
        String filename = "statement-" + customerId + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .body(pdf);
    }
}