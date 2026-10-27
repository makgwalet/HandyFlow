package za.co.handyflow.platform.facilitiesmanagement.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.facilitiesmanagement.application.internal.FmPortalDataService;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmInvoice;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmSite;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmPortalInvoiceResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmPortalSiteResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmPortalWorkOrderResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Client-facing reads, gated by the portal JWT ({@code PORTAL_USER} authority). Responses use portal-specific
 * DTOs that omit internal cost, technician/vendor details and staff notes (Chunk 2).
 * <p>
 * Chunk 6: internal lifecycle detail is hidden too. A job awaiting internal verification is shown as
 * IN_PROGRESS with no completion date until it is verified, so clients never see the verification step,
 * rework, or unapproved/rejected requests.
 */
@RestController
@RequestMapping("/api/v1/facilitiesmanagement/portal/me")
@RequiredArgsConstructor
@Tag(name = "Facilities Management Client Portal - Data", description = "What a logged-in client contact can see about their own organization")
public class FmPortalDataController {

    private final FmPortalDataService portalDataService;

    @GetMapping("/sites")
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    @Operation(summary = "This client's own sites")
    public ResponseEntity<ApiResponse<Page<FmPortalSiteResponse>>> getMySites(@PageableDefault(size = 20) Pageable pageable) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                portalDataService.getMySites(tenantId, getPortalUserId(), pageable).map(this::toResponse)));
    }

    @GetMapping("/work-orders")
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    @Operation(summary = "This client's own work orders (internal triage and verification states are never shown)")
    public ResponseEntity<ApiResponse<Page<FmPortalWorkOrderResponse>>> getMyWorkOrders(
            @RequestParam(required = false) String status, @PageableDefault(size = 20) Pageable pageable) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                portalDataService.getMyWorkOrders(tenantId, getPortalUserId(), status, pageable).map(this::toResponse)));
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    @Operation(summary = "This client's own invoices (sent invoices only — drafts, unsent and cancelled invoices are never shown)")
    public ResponseEntity<ApiResponse<List<FmPortalInvoiceResponse>>> getMyInvoices() {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        List<FmPortalInvoiceResponse> invoices = portalDataService.getMyInvoices(tenantId, getPortalUserId())
                .stream().map(this::toResponse).toList();
        return ResponseEntity.ok(ApiResponse.success(invoices));
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
        boolean pendingVerification = "PENDING_VERIFICATION".equals(w.getStatus());
        return new FmPortalWorkOrderResponse(w.getId(), w.getWorkOrderNumber(), w.getSiteId(), w.getAssetId(),
                w.getCategory(), w.getPriority(),
                pendingVerification ? "IN_PROGRESS" : w.getStatus(),
                w.getDescription(), w.getReportedBy(), w.getScheduledDate(),
                pendingVerification ? null : w.getCompletedAt(), w.getCreatedAt());
    }

    private FmPortalInvoiceResponse toResponse(FmInvoice i) {
        return new FmPortalInvoiceResponse(i.getId(), i.getInvoiceNumber(), i.getPeriodStart(), i.getPeriodEnd(),
                i.getIssueDate(), i.getDueDate(), i.getSubtotal(), i.getVatAmount(), i.getTotal(), i.getAmountPaid(),
                i.balance(), i.getStatus());
    }
}