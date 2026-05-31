package za.co.handyflow.platform.bookings.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.bookings.application.internal.BookingsService;
import za.co.handyflow.platform.bookings.dto.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings", description = "Appointment scheduling, availability and booking management")
public class BookingsController {

    private final BookingsService bookingsService;

    // ── Services ──────────────────────────────────────────────────────────────

    @GetMapping("/services")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List all active booking services")
    public ResponseEntity<ApiResponse<List<ServiceResponse>>> getServices() {
        return ResponseEntity.ok(ApiResponse.success("Success",
                bookingsService.getServices(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/services")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Create a new bookable service")
    public ResponseEntity<ApiResponse<ServiceResponse>> createService(
            @Valid @RequestBody CreateServiceRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Service created",
                bookingsService.createService(TenantContext.getTenantIdAsObject(), req)));
    }

    @PutMapping("/services/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Update a booking service")
    public ResponseEntity<ApiResponse<ServiceResponse>> updateService(
            @PathVariable UUID id,
            @Valid @RequestBody CreateServiceRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Service updated",
                bookingsService.updateService(TenantContext.getTenantIdAsObject(), id, req)));
    }

    // ── Staff ─────────────────────────────────────────────────────────────────

    @GetMapping("/staff")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List all active booking staff")
    public ResponseEntity<ApiResponse<List<StaffResponse>>> getStaff() {
        return ResponseEntity.ok(ApiResponse.success("Success",
                bookingsService.getStaff(TenantContext.getTenantIdAsObject())));
    }

    @PostMapping("/staff")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Add a staff member for bookings")
    public ResponseEntity<ApiResponse<StaffResponse>> createStaff(
            @Valid @RequestBody CreateStaffRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Staff added",
                bookingsService.createStaff(TenantContext.getTenantIdAsObject(), req)));
    }

    // ── Availability ──────────────────────────────────────────────────────────

    @PostMapping("/availability")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Set working hours for a staff member or the whole business")
    public ResponseEntity<ApiResponse<Void>> setAvailability(
            @Valid @RequestBody SetAvailabilityRequest req) {
        bookingsService.setAvailability(TenantContext.getTenantIdAsObject(), req);
        return ResponseEntity.ok(ApiResponse.success("Availability set", null));
    }

    @PostMapping("/blocks")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Block time — lunch, holiday, leave")
    public ResponseEntity<ApiResponse<Void>> addBlock(
            @Valid @RequestBody AddBlockRequest req) {
        bookingsService.addBlock(TenantContext.getTenantIdAsObject(), req);
        return ResponseEntity.status(201).body(ApiResponse.success("Block added", null));
    }

    // ── Available slots ───────────────────────────────────────────────────────

    @GetMapping("/available-slots")
    @Operation(summary = "Get available time slots for a service on a date")
    public ResponseEntity<ApiResponse<List<AvailableSlot>>> getAvailableSlots(
            @RequestParam UUID serviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) UUID staffId) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                bookingsService.getAvailableSlots(
                        TenantContext.getTenantIdAsObject(), serviceId, date, staffId)));
    }

    // ── Bookings ──────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "List bookings with optional status, date and staff filters")
    public ResponseEntity<ApiResponse<Page<BookingResponse>>> getBookings(
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) UUID staffId,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                bookingsService.getBookings(TenantContext.getTenantIdAsObject(),
                        status, date, staffId, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Get booking detail")
    public ResponseEntity<ApiResponse<BookingResponse>> getBooking(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Success",
                bookingsService.getBooking(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Create a new booking — checks slot availability automatically")
    public ResponseEntity<ApiResponse<BookingResponse>> createBooking(
            @Valid @RequestBody CreateBookingRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.success("Booking created",
                bookingsService.createBooking(TenantContext.getTenantIdAsObject(), req)));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Confirm a pending booking")
    public ResponseEntity<ApiResponse<BookingResponse>> confirm(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Booking confirmed",
                bookingsService.confirmBooking(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Mark booking as in progress")
    public ResponseEntity<ApiResponse<BookingResponse>> start(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Booking started",
                bookingsService.startBooking(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Complete a booking")
    public ResponseEntity<ApiResponse<BookingResponse>> complete(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Booking completed",
                bookingsService.completeBooking(TenantContext.getTenantIdAsObject(), id)));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Cancel a booking")
    public ResponseEntity<ApiResponse<BookingResponse>> cancel(
            @PathVariable UUID id,
            @RequestBody CancelBookingRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Booking cancelled",
                bookingsService.cancelBooking(TenantContext.getTenantIdAsObject(),
                        id, req.reason())));
    }

    @PostMapping("/{id}/no-show")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Mark a booking as no-show")
    public ResponseEntity<ApiResponse<BookingResponse>> noShow(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Marked as no-show",
                bookingsService.markNoShow(TenantContext.getTenantIdAsObject(), id)));
    }
}