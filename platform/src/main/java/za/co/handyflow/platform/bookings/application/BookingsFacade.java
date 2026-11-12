package za.co.handyflow.platform.bookings.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.bookings.application.internal.BookingsService;
import za.co.handyflow.platform.bookings.dto.BookingResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * What other modules may do with Bookings: read services and their resources, find free slots, reserve, and change a
 * booking's outcome. Deliberately small and free of Bookings' own types, so callers never depend on its entities or DTOs.
 * Booking rules (lead time, advance limit, no double booking of a resource) stay in {@link BookingsService}.
 * Permissions are the caller's to check: this facade does not apply BOOKINGS_* authorities.
 */
@Service
@RequiredArgsConstructor
public class BookingsFacade {

    private final BookingsService bookings;

    public record ServiceInfo(UUID id, String name, int durationMinutes, BigDecimal price, boolean active) {}
    public record ResourceInfo(UUID id, String name) {}
    public record SlotInfo(LocalTime start, LocalTime end, String label) {}
    public record BookingInfo(UUID id, String bookingNumber, UUID serviceId, String serviceName, UUID resourceId, String resourceName,
                              String clientName, String clientPhone, LocalDate date, LocalTime start, LocalTime end,
                              String status, BigDecimal price, UUID invoiceId) {}

    @Transactional(readOnly = true)
    public List<ServiceInfo> services(TenantId tenantId) {
        return bookings.getServices(tenantId).stream()
                .map(s -> new ServiceInfo(s.id(), s.name(), s.durationMinutes(), s.price(), s.active())).toList();
    }

    /** The staff members (bays, bay attendants, stations) that can deliver a service. */
    @Transactional(readOnly = true)
    public List<ResourceInfo> resources(TenantId tenantId, UUID serviceId) {
        List<UUID> ids = bookings.getServiceStaff(tenantId, serviceId);
        return bookings.getStaff(tenantId).stream().filter(s -> ids.contains(s.id()))
                .map(s -> new ResourceInfo(s.id(), s.name())).toList();
    }

    @Transactional(readOnly = true)
    public List<SlotInfo> slots(TenantId tenantId, UUID serviceId, LocalDate date, UUID resourceId) {
        return bookings.getAvailableSlots(tenantId, serviceId, date, resourceId).stream()
                .map(s -> new SlotInfo(s.startTime(), s.endTime(), s.displayLabel())).toList();
    }

    /** Joins the caller's transaction: if the caller's own record then fails to save, the booking rolls back with it. */
    @Transactional
    public BookingInfo reserve(TenantId tenantId, UUID serviceId, UUID resourceId, LocalDate date, LocalTime start,
                               String clientName, String clientPhone, String clientEmail, String notes) {
        return info(bookings.createBooking(tenantId, new za.co.handyflow.platform.bookings.dto.CreateBookingRequest(
                serviceId, resourceId, null, clientName, clientEmail, clientPhone, date, start, notes)));
    }

    @Transactional(readOnly = true)
    public BookingInfo get(TenantId tenantId, UUID bookingId) { return info(bookings.getBooking(tenantId, bookingId)); }

    @Transactional
    public BookingInfo cancel(TenantId tenantId, UUID bookingId, String reason) { return info(bookings.cancelBooking(tenantId, bookingId, reason)); }

    @Transactional
    public BookingInfo complete(TenantId tenantId, UUID bookingId) { return info(bookings.completeBooking(tenantId, bookingId)); }

    @Transactional
    public BookingInfo noShow(TenantId tenantId, UUID bookingId) { return info(bookings.markNoShow(tenantId, bookingId)); }

    private static BookingInfo info(BookingResponse b) {
        return new BookingInfo(b.id(), b.bookingNumber(), b.serviceId(), b.serviceName(), b.staffId(), b.staffName(), b.clientName(),
                b.clientPhone(), b.bookingDate(), b.startTime(), b.endTime(), b.status(), b.price(), b.invoiceId());
    }
}
