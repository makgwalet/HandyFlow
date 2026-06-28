package za.co.handyflow.platform.bookings.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * BookingResponse — full booking detail returned by every booking endpoint.
 *
 * WHY add cancellationReason, originalBookingDate, rescheduledAt here?
 * The frontend needs these to:
 *   - Show "Cancellation reason: family emergency" in the detail modal
 *   - Show the "rescheduled" badge on booking cards
 *   - Display "Originally booked for Monday 14 July" in the modal header
 *
 * WHY were they missing originally?
 * The initial BookingResponse was built before reschedule and cancellation
 * reason were implemented.  Now that the DB columns exist and the domain
 * methods write them, the DTO must expose them.
 *
 * The mapBookingResponse() JDBC mapper in BookingsService must be updated
 * to read these three new fields — see BookingsService_additions.java.
 */
public record BookingResponse(
        UUID      id,
        String    bookingNumber,
        UUID      serviceId,
        String    serviceName,
        UUID      staffId,
        String    staffName,
        String    clientName,
        String    clientEmail,
        String    clientPhone,
        LocalDate bookingDate,
        LocalTime startTime,
        LocalTime endTime,
        int       durationMinutes,
        String    status,
        BigDecimal price,
        String    notes,
        UUID      invoiceId,
        Instant   confirmedAt,
        Instant   completedAt,
        Instant   createdAt,

        // ── Added fields ───────────────────────────────────────────────────────
        String    cancellationReason,   // null unless status = CANCELLED
        LocalDate originalBookingDate,  // null unless rescheduled at least once
        Instant   rescheduledAt         // null unless rescheduled at least once
) {}
