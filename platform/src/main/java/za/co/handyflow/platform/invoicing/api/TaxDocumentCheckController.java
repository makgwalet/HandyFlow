package za.co.handyflow.platform.invoicing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.invoicing.application.internal.TaxDocumentCheckService;
import za.co.handyflow.platform.invoicing.application.internal.TaxDocumentRules.Issue;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/** Finance patch F14: is the business set up to issue compliant tax invoices and credit notes? */
@RestController
@RequestMapping("/api/v1/invoicing")
@RequiredArgsConstructor
@Tag(name = "Invoicing - Tax document check")
public class TaxDocumentCheckController {

    private final TaxDocumentCheckService service;

    @GetMapping("/tax-document-check")
    @PreAuthorize("hasAuthority('INVOICE_READ')")
    @Operation(summary = "Gaps in the business's details for issuing tax invoices and credit notes")
    public ResponseEntity<ApiResponse<List<Issue>>> business() {
        return ResponseEntity.ok(ApiResponse.success("Success", service.forBusiness(TenantContext.getTenantIdAsObject())));
    }

    @GetMapping("/invoices/{id}/tax-document-check")
    @PreAuthorize("hasAuthority('INVOICE_READ')")
    @Operation(summary = "Gaps in the business's and this invoice's customer details")
    public ResponseEntity<ApiResponse<List<Issue>>> invoice(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success", service.forInvoice(TenantContext.getTenantIdAsObject(), id)));
    }
}
