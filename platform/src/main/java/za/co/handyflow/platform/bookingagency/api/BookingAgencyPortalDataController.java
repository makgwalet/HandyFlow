package za.co.handyflow.platform.bookingagency.api;

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
import za.co.handyflow.platform.bookingagency.application.internal.BookingAgencyPortalDataService;
import za.co.handyflow.platform.bookingagency.dto.*;

import java.util.List;
import java.util.UUID;

import za.co.handyflow.platform.bookingagency.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;

@RestController
@RequestMapping("/api/v1/booking-agency/portal")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PORTAL_USER')")
@Tag(name = "Booking Agency Client Portal", description = "Client-facing data access")
public class BookingAgencyPortalDataController {

    private final BookingAgencyPortalDataService portalDataService;

    @GetMapping("/clients")
    @Operation(summary = "List every client this portal user has access to")
    public ResponseEntity<ApiResponse<List<PortalClientSummaryResponse>>> getMyClients() {
        return ResponseEntity.ok(ApiResponse.success(portalDataService.getMyClients(getPortalUserId())));
    }

    @GetMapping("/clients/{clientId}/resources")
    public ResponseEntity<ApiResponse<List<ResourceResponse>>> getMyResources(@PathVariable UUID clientId) {
        return ResponseEntity.ok(ApiResponse.success(
                portalDataService.getMyResources(getPortalUserId(), clientId)));
    }

    @GetMapping("/clients/{clientId}/offerings")
    public ResponseEntity<ApiResponse<List<OfferingResponse>>> getMyOfferings(@PathVariable UUID clientId) {
        return ResponseEntity.ok(ApiResponse.success(
                portalDataService.getMyOfferings(getPortalUserId(), clientId)));
    }

    @GetMapping("/clients/{clientId}/bookings")
    public ResponseEntity<ApiResponse<Page<BookingResponse>>> getMyBookings(
            @PathVariable UUID clientId, @PageableDefault(size = 24) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                portalDataService.getMyBookings(getPortalUserId(), clientId, pageable)));
    }

    private UUID getPortalUserId() {
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
    }
}