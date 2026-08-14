package za.co.handyflow.platform.recruitmentagency.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.recruitmentagency.application.internal.RecruitmentAgencyPortalDataService;
import za.co.handyflow.platform.recruitmentagency.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;

import java.util.List;
import java.util.UUID;

/**
 * The missing piece — RecruitmentAgencyPortalDataService already existed,
 * fully implemented (getMyPlacements, getMyInvoices, requireAccess all
 * confirmed directly against real source), with no controller ever
 * wired to it. Confirmed via a real NoResourceFoundException on
 * GET /api/v1/recruitment-agency/portal/clients: nothing was mapped at
 * that path at all. Same exact gap as PayrollBureauPortalAuthController
 * earlier this session, just on the data side instead of auth.
 * <p>
 * Structure mirrors BookingAgencyPortalDataController exactly — that
 * one is confirmed working (real 200 tonight) — including the plain
 * "/clients" path with no "/me/" prefix (that's specific to
 * AccountantPortalDataController, not a platform-wide convention).
 * <p>
 * UNVERIFIED: getMyRequisitions(UUID, UUID) and getMyClients(UUID)
 * method signatures on RecruitmentAgencyPortalDataService are inferred
 * from the sibling pattern (BookingAgencyPortalDataService's
 * getMyResources/getMyOfferings/getMyBookings all take
 * (portalUserId, clientId)) and from what the already-fixed
 * recruitmentAgencyPortal.api.ts frontend calls — not confirmed
 * directly against this specific service's source the way
 * getMyPlacements/getMyInvoices/requireAccess were. If this doesn't
 * compile, check the real service method names first.
 */
@RestController
@RequestMapping("/api/v1/recruitment-agency/portal")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PORTAL_USER')")
@Tag(name = "Recruitment Agency Client Portal", description = "Client-facing data access")
public class RecruitmentAgencyPortalDataController {

    private final RecruitmentAgencyPortalDataService portalDataService;

    @GetMapping("/clients")
    @Operation(summary = "List every client this portal user has access to")
    public ResponseEntity<ApiResponse<List<PortalClientSummaryResponse>>> getMyClients() {
        return ResponseEntity.ok(ApiResponse.success(portalDataService.getMyClients(getPortalUserId())));
    }

    @GetMapping("/clients/{clientId}/requisitions")
    @Operation(summary = "List requisitions for a client this portal user has access to")
    public ResponseEntity<ApiResponse<List<RequisitionResponse>>> getMyRequisitions(@PathVariable UUID clientId) {
        return ResponseEntity.ok(ApiResponse.success(
                portalDataService.getMyRequisitions(getPortalUserId(), clientId)));
    }

    @GetMapping("/clients/{clientId}/requisitions/{requisitionId}/placements")
    @Operation(summary = "List candidate placements/pipeline for a specific requisition")
    public ResponseEntity<ApiResponse<List<PlacementResponse>>> getMyPlacements(
            @PathVariable UUID clientId, @PathVariable UUID requisitionId) {
        return ResponseEntity.ok(ApiResponse.success(
                portalDataService.getMyPlacements(getPortalUserId(), clientId, requisitionId)));
    }

    @GetMapping("/clients/{clientId}/invoices")
    @Operation(summary = "List placement-fee invoices for a client this portal user has access to")
    public ResponseEntity<ApiResponse<Page<AgencyInvoiceResponse>>> getMyInvoices(
            @PathVariable UUID clientId, @PageableDefault(size = 24) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                portalDataService.getMyInvoices(getPortalUserId(), clientId, pageable)));
    }

    private UUID getPortalUserId() {
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
    }
}