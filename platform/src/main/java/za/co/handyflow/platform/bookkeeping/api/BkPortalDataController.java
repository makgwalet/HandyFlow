package za.co.handyflow.platform.bookkeeping.api;

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
import za.co.handyflow.platform.bookkeeping.application.internal.BkPortalDataService;
import za.co.handyflow.platform.bookkeeping.domain.model.BkInvoice;
import za.co.handyflow.platform.bookkeeping.domain.model.BkTimeEntry;
import za.co.handyflow.platform.bookkeeping.dto.BkInvoiceResponse;
import za.co.handyflow.platform.bookkeeping.dto.BkTimeEntryResponse;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Client-facing reads, gated by the portal JWT (not the staff
 * BOOKKEEPING_* authorities) — same {@code PORTAL_USER} authority
 * convention every sibling portal-data controller in this codebase uses,
 * and the same "PortalJwtFilter stores the portal user's ID as the
 * Authentication principal" mechanism confirmed there. No FeatureGuard
 * here either, matching that same confirmed convention.
 */
@RestController
@RequestMapping("/api/v1/bookkeeping/portal/me")
@RequiredArgsConstructor
@Tag(name = "Bookkeeping Client Portal - Data", description = "What a logged-in client contact can see about their own organization")
public class BkPortalDataController {

    private final BkPortalDataService portalDataService;

    @GetMapping("/invoices")
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    @Operation(summary = "This client's own invoices")
    public ResponseEntity<ApiResponse<List<BkInvoiceResponse>>> getMyInvoices() {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        List<BkInvoiceResponse> invoices = portalDataService.getMyInvoices(tenantId, getPortalUserId())
                .stream().map(this::toResponse).toList();
        return ResponseEntity.ok(ApiResponse.success(invoices));
    }

    @GetMapping("/time-entries")
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    @Operation(summary = "This client's own logged time")
    public ResponseEntity<ApiResponse<Page<BkTimeEntryResponse>>> getMyTimeEntries(@PageableDefault(size = 20) Pageable pageable) {
        TenantId tenantId = TenantContext.getTenantIdAsObject();
        return ResponseEntity.ok(ApiResponse.success(
                portalDataService.getMyTimeEntries(tenantId, getPortalUserId(), pageable).map(this::toResponse)));
    }

    /** PortalJwtFilter stores the portal user's ID (UUID string) as the Authentication principal — confirmed against every sibling portal-data controller's own identical helper. */
    private UUID getPortalUserId() {
        try {
            return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
        } catch (Exception e) {
            throw new HandyFlowException("Invalid portal session", HttpStatus.UNAUTHORIZED, "INVALID_PORTAL_SESSION");
        }
    }

    private BkInvoiceResponse toResponse(BkInvoice i) {
        return new BkInvoiceResponse(i.getId(), i.getClientId(), i.getInvoiceNumber(), i.getPeriodStart(), i.getPeriodEnd(),
                i.getIssueDate(), i.getDueDate(), i.getSubtotal(), i.getVatAmount(), i.getTotal(), i.getAmountPaid(),
                i.balance(), i.getStatus(), i.getCreatedAt());
    }

    private BkTimeEntryResponse toResponse(BkTimeEntry t) {
        return new BkTimeEntryResponse(t.getId(), t.getClientId(), t.getPractitionerId(), t.getPractitionerName(),
                t.getEntryDate(), t.getActivityType(), t.getDescription(), t.getHours(), t.getHourlyRate(),
                t.lineTotal(), t.isBillable(), t.getStatus(), t.getInvoiceId(), t.getCreatedAt());
    }
}
