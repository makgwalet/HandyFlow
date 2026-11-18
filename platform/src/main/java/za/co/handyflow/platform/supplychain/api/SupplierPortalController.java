package za.co.handyflow.platform.supplychain.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.supplychain.application.internal.SupplierPortalService;
import za.co.handyflow.platform.supplychain.dto.QuotationResponse;
import za.co.handyflow.platform.supplychain.dto.SupplierPortalDtos.*;

import java.util.List;
import java.util.UUID;

/**
 * The supplier's own view: RFQs they were invited to, and their own quotation. Authenticated by the shared portal token (PORTAL_USER);
 * there is no tenant on the request, so the tenant and supplier come from the person's grant and the buyer's Supply Chain entitlement
 * is checked in SupplierPortalService, per grant. Lives outside /api/v1/supply-chain/ on purpose: that prefix demands a staff tenant.
 */
@RestController
@RequestMapping("/api/v1/supplier/portal")
@RequiredArgsConstructor
@Tag(name = "Supplier Portal", description = "Supplier-facing RFQs and quotations")
public class SupplierPortalController {

    private final SupplierPortalService service;

    @GetMapping("/rfqs")
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    @Operation(summary = "RFQs this supplier was invited to")
    public ResponseEntity<ApiResponse<List<RfqSummary>>> rfqs() { return ok("Success", service.rfqs(me())); }

    @GetMapping("/rfqs/{rfqId}")
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    @Operation(summary = "One RFQ: what is wanted, the closing time and this supplier's own quotation")
    public ResponseEntity<ApiResponse<RfqDetail>> rfq(@PathVariable UUID rfqId) { return ok("Success", service.rfq(me(), rfqId)); }

    @PostMapping("/rfqs/{rfqId}/quotation")
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    @Operation(summary = "Submit a quotation, or revise the one already submitted, while the RFQ is open")
    public ResponseEntity<ApiResponse<QuotationResponse>> submit(@PathVariable UUID rfqId, @RequestBody QuoteRequest req) {
        return ok("Quotation submitted", service.submit(me(), rfqId, req));
    }

    @PostMapping(value = "/rfqs/{rfqId}/quotation/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    @Operation(summary = "Attach the quote document to the submitted quotation")
    public ResponseEntity<ApiResponse<QuotationResponse>> attach(@PathVariable UUID rfqId, @RequestPart("file") MultipartFile file) {
        return ok("Document attached", service.attachFile(me(), rfqId, file));
    }

    @PostMapping("/rfqs/{rfqId}/quotation/withdraw")
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    @Operation(summary = "Withdraw the submitted quotation while the RFQ is open (reason required)")
    public ResponseEntity<ApiResponse<QuotationResponse>> withdraw(@PathVariable UUID rfqId, @RequestBody ReasonRequest req) {
        return ok("Quotation withdrawn", service.withdraw(me(), rfqId, req.reason()));
    }

    @PostMapping("/rfqs/{rfqId}/decline")
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    @Operation(summary = "Decline to quote (reason required)")
    public ResponseEntity<ApiResponse<RfqDetail>> decline(@PathVariable UUID rfqId, @RequestBody ReasonRequest req) {
        return ok("Declined", service.decline(me(), rfqId, req.reason()));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String m, T d) { return ResponseEntity.ok(ApiResponse.success(m, d)); }

    private static UUID me() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(a.getName());
    }
}
