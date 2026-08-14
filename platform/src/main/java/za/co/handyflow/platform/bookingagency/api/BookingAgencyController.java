package za.co.handyflow.platform.bookingagency.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.bookingagency.application.internal.BookingAgencyService;
import za.co.handyflow.platform.bookingagency.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

/**
 * Foundation-layer endpoints only — agency profile and client
 * portfolio. FeatureGuard-gated the same as every other separately-
 * subscribable module in this platform.
 */
@RestController
@RequestMapping("/api/v1/booking-agency")
@RequiredArgsConstructor
@Tag(name = "Booking Agency", description = "Multi-client booking/scheduling agency practice management")
public class BookingAgencyController {

    private final BookingAgencyService agencyService;
    private final FeatureGuard featureGuard;

    // ── Agency profile ───────────────────────────────────────────────────────

    @GetMapping("/profile")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Get the agency's own practice profile")
    public ResponseEntity<ApiResponse<BookAgencyProfileResponse>> getProfile() {
        featureGuard.requireModule("bookingagency");
        return ResponseEntity.ok(ApiResponse.success(
                agencyService.getProfile(TenantContext.getTenantIdAsObject())));
    }

    @PutMapping("/profile")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @Operation(summary = "Create or update the agency's practice profile")
    public ResponseEntity<ApiResponse<BookAgencyProfileResponse>> upsertProfile(
            @Valid @RequestBody UpdateBookAgencyProfileRequest req) {
        featureGuard.requireModule("bookingagency");
        return ResponseEntity.ok(ApiResponse.success("Profile saved",
                agencyService.upsertProfile(TenantContext.getTenantIdAsObject(), req)));
    }

    // ── Client portfolio ──────────────────────────────────────────────────────

    @GetMapping("/clients")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List active agency clients")
    public ResponseEntity<ApiResponse<Page<BookAgencyClientResponse>>> getClients(
            @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("bookingagency");
        return ResponseEntity.ok(ApiResponse.success(
                agencyService.getClients(TenantContext.getTenantIdAsObject(), pageable)));
    }

    @GetMapping("/clients/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<BookAgencyClientResponse>> getClient(@PathVariable UUID id) {
        featureGuard.requireModule("bookingagency");
        return ResponseEntity.ok(ApiResponse.success(
                agencyService.getClient(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/clients")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Onboard a new agency client")
    public ResponseEntity<ApiResponse<BookAgencyClientResponse>> createClient(
            @Valid @RequestBody CreateBookAgencyClientRequest req) {
        featureGuard.requireModule("bookingagency");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Client onboarded",
                agencyService.createClient(TenantContext.getTenantIdAsObject(), req)));
    }

    @PutMapping("/clients/{id}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<ApiResponse<BookAgencyClientResponse>> updateClient(
            @PathVariable UUID id, @Valid @RequestBody CreateBookAgencyClientRequest req) {
        featureGuard.requireModule("bookingagency");
        return ResponseEntity.ok(ApiResponse.success("Client updated",
                agencyService.updateClient(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/clients/{id}/deactivate")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<ApiResponse<BookAgencyClientResponse>> deactivateClient(@PathVariable UUID id) {
        featureGuard.requireModule("bookingagency");
        return ResponseEntity.ok(ApiResponse.success("Client deactivated",
                agencyService.deactivateClient(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/clients/{id}/reactivate")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<ApiResponse<BookAgencyClientResponse>> reactivateClient(@PathVariable UUID id) {
        featureGuard.requireModule("bookingagency");
        return ResponseEntity.ok(ApiResponse.success("Client reactivated",
                agencyService.reactivateClient(TenantContext.getTenantIdAsObject(), id)));
    }

    @DeleteMapping("/clients/{id}")
    @PreAuthorize("hasAuthority('USER_DELETE')")
    public ResponseEntity<ApiResponse<Void>> deleteClient(@PathVariable UUID id) {
        featureGuard.requireModule("bookingagency");
        agencyService.deleteClient(TenantContext.getTenantIdAsObject(), id);
        return ResponseEntity.ok(ApiResponse.success("Client deleted", null));
    }

    @PostMapping("/clients/{id}/portal-invites")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @io.swagger.v3.oas.annotations.tags.Tag(name = "Booking Agency Client Portal")
    public ResponseEntity<ApiResponse<PortalAccessGrantResponse>> invitePortalUser(
            @PathVariable UUID id, @Valid @RequestBody InvitePortalUserRequest req) {
        featureGuard.requireModule("bookingagency");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Invite sent",
                agencyService.invitePortalUser(TenantContext.getTenantIdAsObject(), id,
                        req.email(), TenantContext.getCurrentUserId())));
    }

    @GetMapping("/clients/{id}/portal-invites")
    @PreAuthorize("hasAuthority('USER_READ')")
    @io.swagger.v3.oas.annotations.tags.Tag(name = "Booking Agency Client Portal")
    public ResponseEntity<ApiResponse<List<PortalAccessGrantResponse>>> getPortalAccessGrants(@PathVariable UUID id) {
        featureGuard.requireModule("bookingagency");
        return ResponseEntity.ok(ApiResponse.success(
                agencyService.getPortalAccessGrants(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/clients/{clientId}/portal-invites/{grantId}/revoke")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    @io.swagger.v3.oas.annotations.tags.Tag(name = "Booking Agency Client Portal")
    public ResponseEntity<ApiResponse<PortalAccessGrantResponse>> revokePortalAccess(
            @PathVariable UUID clientId, @PathVariable UUID grantId) {
        featureGuard.requireModule("bookingagency");
        return ResponseEntity.ok(ApiResponse.success("Portal access revoked",
                agencyService.revokePortalAccess(TenantContext.getTenantIdAsObject(), clientId, grantId,
                        TenantContext.getCurrentUserId())));
    }

    @PostMapping("/resources")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    public ResponseEntity<ApiResponse<ResourceResponse>> createResource(
            @Valid @RequestBody CreateResourceRequest req) {
        featureGuard.requireModule("bookingagency");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Resource added",
                agencyService.createResource(TenantContext.getTenantIdAsObject(), req)));
    }

    @GetMapping("/clients/{clientId}/resources")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<List<ResourceResponse>>> getResourcesForClient(@PathVariable UUID clientId) {
        featureGuard.requireModule("bookingagency");
        return ResponseEntity.ok(ApiResponse.success(
                agencyService.getResourcesForClient(TenantContext.getTenantIdAsObject(), clientId)));
    }

    @PutMapping("/resources/{id}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<ApiResponse<ResourceResponse>> updateResource(
            @PathVariable UUID id, @Valid @RequestBody CreateResourceRequest req) {
        featureGuard.requireModule("bookingagency");
        return ResponseEntity.ok(ApiResponse.success("Resource updated",
                agencyService.updateResource(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/resources/{id}/deactivate")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<ApiResponse<ResourceResponse>> deactivateResource(@PathVariable UUID id) {
        featureGuard.requireModule("bookingagency");
        return ResponseEntity.ok(ApiResponse.success("Resource deactivated",
                agencyService.deactivateResource(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/resources/{id}/reactivate")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<ApiResponse<ResourceResponse>> reactivateResource(@PathVariable UUID id) {
        featureGuard.requireModule("bookingagency");
        return ResponseEntity.ok(ApiResponse.success("Resource reactivated",
                agencyService.reactivateResource(TenantContext.getTenantIdAsObject(), id)));
    }

    // ── Offerings ─────────────────────────────────────────────────────────

    @PostMapping("/offerings")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    public ResponseEntity<ApiResponse<OfferingResponse>> createOffering(
            @Valid @RequestBody CreateOfferingRequest req) {
        featureGuard.requireModule("bookingagency");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Offering added",
                agencyService.createOffering(TenantContext.getTenantIdAsObject(), req)));
    }

    @GetMapping("/clients/{clientId}/offerings")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<List<OfferingResponse>>> getOfferingsForClient(@PathVariable UUID clientId) {
        featureGuard.requireModule("bookingagency");
        return ResponseEntity.ok(ApiResponse.success(
                agencyService.getOfferingsForClient(TenantContext.getTenantIdAsObject(), clientId)));
    }

    @PutMapping("/offerings/{id}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<ApiResponse<OfferingResponse>> updateOffering(
            @PathVariable UUID id, @Valid @RequestBody CreateOfferingRequest req) {
        featureGuard.requireModule("bookingagency");
        return ResponseEntity.ok(ApiResponse.success("Offering updated",
                agencyService.updateOffering(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/offerings/{id}/deactivate")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<ApiResponse<OfferingResponse>> deactivateOffering(@PathVariable UUID id) {
        featureGuard.requireModule("bookingagency");
        return ResponseEntity.ok(ApiResponse.success("Offering deactivated",
                agencyService.deactivateOffering(TenantContext.getTenantIdAsObject(), id)));
    }

    // ── Bookings ──────────────────────────────────────────────────────────

    @PostMapping("/clients/{clientId}/bookings")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Create a booking — checks for overlap on the resource before confirming")
    public ResponseEntity<ApiResponse<BookingResponse>> createBooking(
            @PathVariable UUID clientId, @Valid @RequestBody CreateBookingRequest req) {
        featureGuard.requireModule("bookingagency");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Booking created",
                agencyService.createBooking(TenantContext.getTenantIdAsObject(), clientId, req)));
    }

    @GetMapping("/clients/{clientId}/bookings")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<Page<BookingResponse>>> getBookingsForClient(
            @PathVariable UUID clientId, @PageableDefault(size = 50) Pageable pageable) {
        featureGuard.requireModule("bookingagency");
        return ResponseEntity.ok(ApiResponse.success(
                agencyService.getBookingsForClient(TenantContext.getTenantIdAsObject(), clientId, pageable)));
    }

    @PostMapping("/bookings/{id}/cancel")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<ApiResponse<BookingResponse>> cancelBooking(@PathVariable UUID id) {
        featureGuard.requireModule("bookingagency");
        return ResponseEntity.ok(ApiResponse.success("Booking cancelled",
                agencyService.cancelBooking(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/bookings/{id}/complete")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<ApiResponse<BookingResponse>> completeBooking(@PathVariable UUID id) {
        featureGuard.requireModule("bookingagency");
        return ResponseEntity.ok(ApiResponse.success("Booking marked complete",
                agencyService.completeBooking(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/bookings/{id}/no-show")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<ApiResponse<BookingResponse>> markNoShow(@PathVariable UUID id) {
        featureGuard.requireModule("bookingagency");
        return ResponseEntity.ok(ApiResponse.success("Booking marked no-show",
                agencyService.markNoShow(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/clients/{clientId}/invoices")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "Generate a retainer invoice for one billing period")
    public ResponseEntity<ApiResponse<BookAgencyInvoiceResponse>> generateInvoice(
            @PathVariable UUID clientId, @Valid @RequestBody GenerateRetainerInvoiceRequest req) {
        featureGuard.requireModule("bookingagency");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Invoice generated",
                agencyService.generateInvoice(TenantContext.getTenantIdAsObject(), clientId, req)));
    }

    @GetMapping("/clients/{clientId}/invoices")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<Page<BookAgencyInvoiceResponse>>> getInvoices(
            @PathVariable UUID clientId, @PageableDefault(size = 24) Pageable pageable) {
        featureGuard.requireModule("bookingagency");
        return ResponseEntity.ok(ApiResponse.success(
                agencyService.getInvoices(TenantContext.getTenantIdAsObject(), clientId, pageable)));
    }

    @PostMapping("/invoices/{id}/send")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<ApiResponse<BookAgencyInvoiceResponse>> sendInvoice(@PathVariable UUID id) {
        featureGuard.requireModule("bookingagency");
        return ResponseEntity.ok(ApiResponse.success("Invoice sent",
                agencyService.sendInvoice(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/invoices/{id}/payments")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<ApiResponse<BookAgencyInvoiceResponse>> recordPayment(
            @PathVariable UUID id, @Valid @RequestBody RecordBookAgencyPaymentRequest req) {
        featureGuard.requireModule("bookingagency");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Payment recorded",
                agencyService.recordPayment(TenantContext.getTenantIdAsObject(), id, req,
                        TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName())));
    }
}