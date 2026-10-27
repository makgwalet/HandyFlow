package za.co.handyflow.platform.facilitiesmanagement.api;

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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmPdfService;
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmPortalDataService;
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmPortalQuoteService;
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmPortalRequestService;
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmPortalSla;
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmPortalStatus;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmCreditNote;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmInvoice;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmSite;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.facilitiesmanagement.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Client-facing endpoints, gated by the portal JWT ({@code PORTAL_USER} authority). A portal token is not
 * tenant-scoped, so nothing here reads {@code TenantContext}: the service resolves tenant and client from the
 * caller's active grants. A person with access to several clients says which with {@code ?clientId=}
 * (see {@code GET /portal/me/clients}).
 * <p>
 * Responses use portal-specific DTOs that omit internal cost, technician/vendor details and staff notes. Internal
 * lifecycle detail is hidden too: a job awaiting internal verification is shown as IN_PROGRESS with no completion
 * date, a client's own unapproved request is SUBMITTED and a turned-down one DECLINED (without the reason).
 */
@RestController
@RequestMapping("/api/v1/facilitiesmanagement/portal/me")
@RequiredArgsConstructor
@Tag(name = "Facilities Management Client Portal - Data", description = "What a logged-in client contact can see and do for their own organization")
public class FmPortalDataController {

    private final FmPortalDataService portalDataService;
    private final FmPortalRequestService portalRequestService;
    private final FmPortalQuoteService portalQuoteService;
    private final FmPdfService pdfService;

    @GetMapping("/clients")
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    @Operation(summary = "The client organisations this login can act for (pass one as ?clientId= when there are several)")
    public ResponseEntity<ApiResponse<List<FmPortalClientResponse>>> getMyClients() {
        return ResponseEntity.ok(ApiResponse.success(portalDataService.myClients(getPortalUserId())));
    }

    @GetMapping("/sites")
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    @Operation(summary = "This client's own sites")
    public ResponseEntity<ApiResponse<Page<FmPortalSiteResponse>>> getMySites(
            @RequestParam(required = false) UUID clientId, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                portalDataService.getMySites(getPortalUserId(), clientId, pageable).map(this::toResponse)));
    }

    @GetMapping("/work-orders")
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    @Operation(summary = "This client's own work orders and the requests they submitted (internal triage and verification states are never shown)")
    public ResponseEntity<ApiResponse<Page<FmPortalWorkOrderResponse>>> getMyWorkOrders(
            @RequestParam(required = false) UUID clientId, @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                portalDataService.getMyWorkOrders(getPortalUserId(), clientId, status, pageable).map(this::toResponse)));
    }

    @PostMapping("/work-orders")
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    @Operation(summary = "Report a fault at one of this client's sites. It is a request until staff approve it; priority is set by staff.")
    public ResponseEntity<ApiResponse<FmPortalWorkOrderResponse>> raiseRequest(
            @RequestParam(required = false) UUID clientId, @Valid @RequestBody FmPortalRaiseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Request submitted",
                toResponse(portalRequestService.raise(getPortalUserId(), clientId, request))));
    }

    @DeleteMapping("/work-orders/{id}")
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    @Operation(summary = "Withdraw a request you submitted, while staff have not yet picked it up")
    public ResponseEntity<ApiResponse<FmPortalWorkOrderResponse>> withdrawRequest(
            @PathVariable UUID id, @RequestParam(required = false) UUID clientId) {
        return ResponseEntity.ok(ApiResponse.success("Request withdrawn",
                toResponse(portalRequestService.withdraw(getPortalUserId(), clientId, id))));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    @Operation(summary = "At-a-glance position: work by status, SLA risk, and what is owed")
    public ResponseEntity<ApiResponse<FmPortalSummaryResponse>> getMySummary(@RequestParam(required = false) UUID clientId) {
        return ResponseEntity.ok(ApiResponse.success(portalDataService.getMySummary(getPortalUserId(), clientId)));
    }

    @GetMapping("/quotes")
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    @Operation(summary = "Quotes sent to this client (drafts are never shown)")
    public ResponseEntity<ApiResponse<List<FmPortalQuoteResponse>>> getMyQuotes(@RequestParam(required = false) UUID clientId) {
        return ResponseEntity.ok(ApiResponse.success(portalQuoteService.list(getPortalUserId(), clientId)));
    }

    @GetMapping("/quotes/{id}")
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    public ResponseEntity<ApiResponse<FmPortalQuoteResponse>> getMyQuote(@PathVariable UUID id, @RequestParam(required = false) UUID clientId) {
        return ResponseEntity.ok(ApiResponse.success(portalQuoteService.get(getPortalUserId(), clientId, id)));
    }

    @PostMapping("/quotes/{id}/accept")
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    @Operation(summary = "Accept an open quote: authorises the work (a work order is created if the quote was not tied to a request)")
    public ResponseEntity<ApiResponse<FmPortalQuoteResponse>> acceptQuote(@PathVariable UUID id, @RequestParam(required = false) UUID clientId,
            @Valid @RequestBody(required = false) FmQuoteDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(portalQuoteService.accept(getPortalUserId(), clientId, id, request == null ? null : request.note())));
    }

    @PostMapping("/quotes/{id}/decline")
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    @Operation(summary = "Decline an open quote (a reason is required)")
    public ResponseEntity<ApiResponse<FmPortalQuoteResponse>> declineQuote(@PathVariable UUID id, @RequestParam(required = false) UUID clientId,
            @Valid @RequestBody FmQuoteDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(portalQuoteService.decline(getPortalUserId(), clientId, id, request.note())));
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    @Operation(summary = "This client's own invoices (sent invoices only — drafts, unsent and cancelled invoices are never shown)")
    public ResponseEntity<ApiResponse<List<FmPortalInvoiceResponse>>> getMyInvoices(@RequestParam(required = false) UUID clientId) {
        List<FmPortalInvoiceResponse> invoices = portalDataService.getMyInvoices(getPortalUserId(), clientId)
                .stream().map(this::toResponse).toList();
        return ResponseEntity.ok(ApiResponse.success(invoices));
    }

    @GetMapping(value = "/invoices/{id}/invoice.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    @Operation(summary = "Download one of this client's invoices (a Tax Invoice when VAT is charged)")
    public ResponseEntity<byte[]> downloadInvoice(@PathVariable UUID id, @RequestParam(required = false) UUID clientId) {
        FmPortalDataService.Scope scope = portalDataService.requireMyInvoice(getPortalUserId(), clientId, id);
        return pdf(pdfService.generateInvoicePdf(scope.tenantId(), id), "invoice.pdf");
    }

    @GetMapping("/credit-notes")
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    @Operation(summary = "This client's approved credit notes")
    public ResponseEntity<ApiResponse<List<FmPortalCreditNoteResponse>>> getMyCreditNotes(@RequestParam(required = false) UUID clientId) {
        List<FmPortalCreditNoteResponse> notes = portalDataService.getMyCreditNotes(getPortalUserId(), clientId).entrySet().stream()
                .map(e -> toResponse(e.getKey(), e.getValue())).toList();
        return ResponseEntity.ok(ApiResponse.success(notes));
    }

    @GetMapping(value = "/credit-notes/{id}/credit-note.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    @Operation(summary = "Download one of this client's credit notes")
    public ResponseEntity<byte[]> downloadCreditNote(@PathVariable UUID id, @RequestParam(required = false) UUID clientId) {
        FmPortalDataService.Scope scope = portalDataService.requireMyCreditNote(getPortalUserId(), clientId, id);
        return pdf(pdfService.generateCreditNotePdf(scope.tenantId(), id), "credit-note.pdf");
    }

    private ResponseEntity<byte[]> pdf(byte[] bytes, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }

    /** PortalJwtFilter stores the portal user's ID (UUID string) as the Authentication principal. */
    private UUID getPortalUserId() {
        try {
            return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
        } catch (Exception e) {
            throw new HandyFlowException("Invalid portal session", HttpStatus.UNAUTHORIZED, "INVALID_PORTAL_SESSION");
        }
    }

    private FmPortalSiteResponse toResponse(FmSite s) {
        return new FmPortalSiteResponse(s.getId(), s.getName(), s.getSiteType(), s.getAddress(),
                s.getStatus(), s.getCreatedAt());
    }

    private FmPortalWorkOrderResponse toResponse(FmWorkOrder w) {
        boolean hideCompletion = "PENDING_VERIFICATION".equals(w.getStatus());
        return new FmPortalWorkOrderResponse(w.getId(), w.getWorkOrderNumber(), w.getSiteId(), w.getAssetId(),
                w.getCategory(), w.getPriority(), FmPortalStatus.present(w.getStatus()),
                w.getDescription(), w.getReportedBy(), w.getScheduledDate(),
                hideCompletion ? null : w.getCompletedAt(), w.getCreatedAt(),
                "REJECTED".equals(w.getStatus()) ? w.getClientMessage() : null,
                FmPortalSla.dueAt(w), FmPortalSla.state(w, Instant.now()));
    }

    private FmPortalInvoiceResponse toResponse(FmInvoice i) {
        return new FmPortalInvoiceResponse(i.getId(), i.getInvoiceNumber(), i.getPeriodStart(), i.getPeriodEnd(),
                i.getIssueDate(), i.getDueDate(), i.getSubtotal(), i.getVatAmount(), i.getTotal(), i.getAmountPaid(),
                i.getCreditedAmount(), i.balance(), i.getStatus());
    }

    private FmPortalCreditNoteResponse toResponse(FmCreditNote c, String invoiceNumber) {
        return new FmPortalCreditNoteResponse(c.getId(), c.getCreditNoteNumber(), c.getInvoiceId(), invoiceNumber,
                c.getIssueDate(), c.getReason(), c.getSubtotal(), c.getVatAmount(), c.getTotal());
    }
}
