package za.co.handyflow.platform.events.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.events.application.internal.EventsService;
import za.co.handyflow.platform.events.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Tag(name = "Events", description = "Event management, ticketing, QR check-in and vendor coordination")
public class EventsController {

    private final EventsService eventsService;

    // ── Events ────────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize(EventsPermissions.READ)
    @Operation(summary = "List events with optional status and type filters")
    public ResponseEntity<ApiResponse<Page<EventResponse>>> getEvents(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                eventsService.getEvents(TenantContext.getTenantIdAsObject(),
                        status, type, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize(EventsPermissions.READ)
    @Operation(summary = "Get event detail")
    public ResponseEntity<ApiResponse<EventResponse>> getEvent(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                eventsService.getEvent(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize(EventsPermissions.WRITE)
    @Operation(summary = "Create a new event")
    public ResponseEntity<ApiResponse<EventResponse>> createEvent(
            @Valid @RequestBody CreateEventRequest req) {
        UUID userId = TenantContext.getCurrentUserId();
        return ResponseEntity.status(201).body(ApiResponse.success("Event created",
                eventsService.createEvent(TenantContext.getTenantIdAsObject(), req, userId)));
    }

    @PutMapping("/{id}")
    @PreAuthorize(EventsPermissions.WRITE)
    @Operation(summary = "Edit an event (full replace) — not allowed once COMPLETED or CANCELLED")
    public ResponseEntity<ApiResponse<EventResponse>> updateEvent(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEventRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Event updated",
                eventsService.updateEvent(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize(EventsPermissions.APPROVE)
    @Operation(summary = "Publish event — opens registration")
    public ResponseEntity<ApiResponse<EventResponse>> publish(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Event published",
                eventsService.publishEvent(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/go-live")
    @PreAuthorize(EventsPermissions.APPROVE)
    @Operation(summary = "Mark event as live — enables QR check-in")
    public ResponseEntity<ApiResponse<EventResponse>> goLive(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Event is now live",
                eventsService.goLive(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize(EventsPermissions.APPROVE)
    @Operation(summary = "Complete the event")
    public ResponseEntity<ApiResponse<EventResponse>> complete(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Event completed",
                eventsService.completeEvent(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(EventsPermissions.CANCEL)
    @Operation(summary = "Cancel an event — optional body records a reason and can email registered guests")
    public ResponseEntity<ApiResponse<EventResponse>> cancel(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) CancelEventRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Event cancelled",
                eventsService.cancelEvent(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/{id}/postpone")
    @PreAuthorize(EventsPermissions.WRITE)
    @Operation(summary = "Postpone — move to new dates with a recorded reason; optionally email registered guests")
    public ResponseEntity<ApiResponse<EventResponse>> postpone(
            @PathVariable UUID id,
            @Valid @RequestBody PostponeEventRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Event postponed",
                eventsService.postponeEvent(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/{id}/duplicate")
    @PreAuthorize(EventsPermissions.WRITE)
    @Operation(summary = "Duplicate an event as a new DRAFT — copies details and ticket tiers, not guests or vendors")
    public ResponseEntity<ApiResponse<EventResponse>> duplicate(
            @PathVariable UUID id,
            @Valid @RequestBody DuplicateEventRequest req) {
        UUID userId = TenantContext.getCurrentUserId();
        return ResponseEntity.status(201).body(ApiResponse.success("Event duplicated",
                eventsService.duplicateEvent(TenantContext.getTenantIdAsObject(), id, req, userId)));
    }

    @GetMapping("/{id}/audit")
    @PreAuthorize(EventsPermissions.AUDIT_READ)
    @Operation(summary = "Event audit trail — who changed what and when, newest first")
    public ResponseEntity<ApiResponse<Page<AuditEntryResponse>>> getAuditTrail(
            @PathVariable UUID id,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                eventsService.getAuditTrail(TenantContext.getTenantIdAsObject(), id, pageable)));
    }

    @GetMapping("/{id}/stats")
    @PreAuthorize(EventsPermissions.READ)
    @Operation(summary = "Live event stats — registered, checked-in, cancelled, vendors")
    public ResponseEntity<ApiResponse<EventStatsResponse>> getStats(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                eventsService.getStats(TenantContext.getTenantIdAsObject(), id)));
    }

    // ── Ticket tiers ──────────────────────────────────────────────────────────

    @GetMapping("/{id}/tiers")
    @PreAuthorize(EventsPermissions.READ)
    @Operation(summary = "List ticket tiers for an event")
    public ResponseEntity<ApiResponse<List<TierResponse>>> getTiers(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                eventsService.getTiers(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/tiers")
    @PreAuthorize(EventsPermissions.TICKETING)
    @Operation(summary = "Create a ticket tier — Early Bird, VIP, General, etc.")
    public ResponseEntity<ApiResponse<TierResponse>> createTier(
            @PathVariable UUID id,
            @Valid @RequestBody CreateTierRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Tier created",
                eventsService.createTier(TenantContext.getTenantIdAsObject(), id, req)));
    }

    // ── Guests ────────────────────────────────────────────────────────────────

    @GetMapping("/{id}/guests")
    @PreAuthorize(EventsPermissions.READ)
    @Operation(summary = "List guests with optional status and tier filters")
    public ResponseEntity<ApiResponse<Page<GuestResponse>>> getGuests(
            @PathVariable UUID id,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID tierId,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                eventsService.getGuests(TenantContext.getTenantIdAsObject(),
                        id, status, tierId, pageable)));
    }

    @PostMapping("/{id}/guests")
    @PreAuthorize(EventsPermissions.REGISTRATION)
    @Operation(summary = "Register a guest — generates ticket number and QR code")
    public ResponseEntity<ApiResponse<GuestResponse>> registerGuest(
            @PathVariable UUID id,
            @Valid @RequestBody RegisterGuestRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Guest registered",
                eventsService.registerGuest(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/{id}/guests/{guestId}/cancel")
    @PreAuthorize(EventsPermissions.REGISTRATION)
    @Operation(summary = "Cancel a guest registration")
    public ResponseEntity<ApiResponse<GuestResponse>> cancelGuest(
            @PathVariable UUID id, @PathVariable UUID guestId) {
        return ResponseEntity.ok(ApiResponse.success("Guest registration cancelled",
                eventsService.cancelGuest(TenantContext.getTenantIdAsObject(), id, guestId)));
    }

    // ── Check-in ──────────────────────────────────────────────────────────────

    @PostMapping("/{id}/check-in")
    @PreAuthorize(EventsPermissions.CHECKIN)
    @Operation(summary = "Scan QR code or ticket number to check in a guest — event must be LIVE")
    public ResponseEntity<ApiResponse<CheckInResponse>> checkIn(
            @PathVariable UUID id,
            @Valid @RequestBody CheckInRequest req) {
        UUID scannedBy = TenantContext.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("Scanned",
                eventsService.checkIn(TenantContext.getTenantIdAsObject(),
                        id, req, scannedBy)));
    }

    // ── Vendors ───────────────────────────────────────────────────────────────

    @GetMapping("/{id}/vendors")
    @PreAuthorize(EventsPermissions.READ)
    @Operation(summary = "List vendors for an event")
    public ResponseEntity<ApiResponse<List<VendorResponse>>> getVendors(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                eventsService.getVendors(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/vendors")
    @PreAuthorize(EventsPermissions.SUPPLIER)
    @Operation(summary = "Add a vendor — caterer, AV, security, photographer, etc.")
    public ResponseEntity<ApiResponse<VendorResponse>> addVendor(
            @PathVariable UUID id,
            @Valid @RequestBody AddVendorRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Vendor added",
                eventsService.addVendor(TenantContext.getTenantIdAsObject(), id, req)));
    }

    @PostMapping("/{id}/vendors/{vendorId}/confirm")
    @PreAuthorize(EventsPermissions.SUPPLIER)
    @Operation(summary = "Confirm a vendor booking")
    public ResponseEntity<ApiResponse<VendorResponse>> confirmVendor(
            @PathVariable UUID id, @PathVariable UUID vendorId) {
        return ResponseEntity.ok(ApiResponse.success("Vendor confirmed",
                eventsService.confirmVendor(TenantContext.getTenantIdAsObject(),
                        id, vendorId)));
    }
}