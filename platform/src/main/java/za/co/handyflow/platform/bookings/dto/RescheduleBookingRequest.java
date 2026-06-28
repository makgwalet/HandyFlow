package za.co.handyflow.platform.bookings.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * RescheduleBookingRequest — move an existing booking to a new date/time.
 *
 * WHY not cancel + create?
 * Cancelling and recreating loses the booking number continuity, the
 * original creation date, and the audit trail showing this was a
 * reschedule rather than a new booking.  Revenue reporting, no-show
 * tracking, and client history all benefit from keeping one record.
 *
 * The reschedule endpoint stores original_booking_date and
 * original_start_time for audit, and sets rescheduled_at timestamp.
 */
public record RescheduleBookingRequest(
        @NotNull LocalDate newDate,
        @NotNull LocalTime newStartTime,
        String             reason
) {}
