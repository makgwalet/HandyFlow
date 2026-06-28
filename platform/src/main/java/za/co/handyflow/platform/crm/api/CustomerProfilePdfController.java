package za.co.handyflow.platform.crm.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.crm.application.internal.CustomerProfilePdfService;
import za.co.handyflow.platform.shared.TenantContext;

import java.io.IOException;
import java.util.UUID;

/**
 * CustomerProfilePdfController — serves the single-customer profile PDF.
 *
 * WHY stream to HttpServletResponse instead of returning ResponseEntity<byte[]>?
 * Returning byte[] means the entire PDF is held in heap memory before the
 * response starts.  Streaming writes directly to the servlet output stream
 * so memory usage is O(page buffer), not O(PDF size).  For a multi-page
 * statement this matters; for a 1-2 page profile it's good hygiene.
 *
 * WHY Content-Disposition: attachment?
 * The browser should save the file rather than try to render it inline.
 * Some browsers render PDF inline in a viewer tab — useful for review but
 * not the default expectation for a "Download profile" button.
 * Change to "inline" if you want the browser to open it in its PDF viewer.
 */
@RestController
@RequestMapping("/api/v1/crm/customers")
@RequiredArgsConstructor
@Tag(name = "CRM - Customers", description = "Customer relationship management")
public class CustomerProfilePdfController {

    private final CustomerProfilePdfService pdfService;

    @GetMapping(value = "/{id}/profile.pdf",
            produces = "application/pdf")
    @PreAuthorize("hasAuthority('CUSTOMER_READ')")
    @Operation(summary = "Download a customer profile as PDF",
            description = "Generates a single-page PDF with customer details, " +
                    "tags, notes, and last 20 activity entries.")
    public void downloadProfilePdf(
            @PathVariable UUID id,
            HttpServletResponse response
    ) throws IOException {

        var tenantId   = TenantContext.getTenantIdAsObject();
        var filename   = "customer-profile-" + id + ".pdf";

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        pdfService.generateProfile(tenantId, id, response.getOutputStream());
    }
}
