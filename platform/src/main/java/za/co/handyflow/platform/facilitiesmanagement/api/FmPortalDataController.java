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
import za.co.handyflow.platform.facilitiesmanagement.dto.FmInvoiceResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmSiteResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmWorkOrderResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Client-facing reads, gated by the portal JWT (not the staff
 * FACILITIESMANAGEMENT_* authorities) — same {@code PORTAL_USER} authority
 * convention every sibling portal-data controller in this codebase uses
 * (TrainProvPortalDataController, CollAgencyPortalDataController), and the
 * same "PortalJwtFilter stores the portal user's ID as the Authentication
 * principal" mechanism confirmed there. No FeatureGuard here either,
 * matching that same confirmed convention.
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
    public ResponseEntity<ApiResponse<Page<FmSiteResponse>>> getMySites(@PageableDefault(size = 20) Pageable pageable) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                portalDataService.getMySites(tenantId, getPortalUserId(), pageable).map(this::toResponse)));
    }

    @GetMapping("/work-orders")
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    @Operation(summary = "This client's own work orders")
    public ResponseEntity<ApiResponse<Page<FmWorkOrderResponse>>> getMyWorkOrders(
            @RequestParam(required = false) String status, @PageableDefault(size = 20) Pageable pageable) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                portalDataService.getMyWorkOrders(tenantId, getPortalUserId(), status, pageable).map(this::toResponse)));
    }

    @GetMapping("/invoices")
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    @Operation(summary = "This client's own invoices")
    public ResponseEntity<ApiResponse<List<FmInvoiceResponse>>> getMyInvoices() {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        List<FmInvoiceResponse> invoices = portalDataService.getMyInvoices(tenantId, getPortalUserId())
                .stream().map(this::toResponse).toList();
        return ResponseEntity.ok(ApiResponse.success(invoices));
    }

    /** PortalJwtFilter stores the portal user's ID (UUID string) as the Authentication principal — confirmed against every sibling portal-data controller's own identical helper. */
    private UUID getPortalUserId() {
        try {
            return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
        } catch (Exception e) {
            throw new HandyFlowException("Invalid portal session", HttpStatus.UNAUTHORIZED, "INVALID_PORTAL_SESSION");
        }
    }

    private FmSiteResponse toResponse(FmSite s) {
        return new FmSiteResponse(s.getId(), s.getClientId(), s.getName(), s.getSiteType(), s.getAddress(),
                s.getNotes(), s.getStatus(), s.getCreatedAt());
    }

    private FmWorkOrderResponse toResponse(FmWorkOrder w) {
        return new FmWorkOrderResponse(w.getId(), w.getWorkOrderNumber(), w.getClientId(), w.getSiteId(), w.getAssetId(),
                w.getPpmScheduleId(), w.getCategory(), w.getPriority(), w.getStatus(), w.getDescription(), w.getReportedBy(),
                w.getTechnicianId(), w.getTechnicianName(), w.getVendorId(), w.getVendorName(), w.getScheduledDate(),
                w.getCompletedAt(), w.getCompletionNotes(), w.getCost(), w.isInvoiced(), w.getCancellationReason(), w.getCreatedAt());
    }

    private FmInvoiceResponse toResponse(FmInvoice i) {
        return new FmInvoiceResponse(i.getId(), i.getClientId(), i.getInvoiceNumber(), i.getPeriodStart(), i.getPeriodEnd(),
                i.getIssueDate(), i.getDueDate(), i.getSubtotal(), i.getVatAmount(), i.getTotal(), i.getAmountPaid(),
                i.balance(), i.getStatus(), i.getCreatedAt());
    }
}
