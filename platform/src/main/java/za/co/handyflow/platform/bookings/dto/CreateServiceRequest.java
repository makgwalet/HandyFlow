package za.co.handyflow.platform.bookings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * CreateServiceRequest — DTO for creating or updating a bookable service.
 *
 * WHY add bufferBeforeMinutes / bufferAfterMinutes?
 * Without buffer time, the slot engine packs bookings back-to-back.
 * For a haircut (30 min), the next client's slot starts the moment the
 * previous one ends — leaving no time to clean the station, prepare tools,
 * or escort the previous client out.  In every commercial booking system
 * (Fresha, Acuity, SimplyBook.me), buffer time is a standard service config.
 *
 * WHY minLeadTimeMinutes / maxAdvanceDays?
 * minLeadTimeMinutes: "book at least 2 hours ahead" prevents last-minute
 * bookings the business can't service (staff not in position, supplies
 * not ready).  maxAdvanceDays: "only book up to 90 days ahead" prevents
 * calendar pollution and reduces no-shows from bookings made too far
 * in advance that are forgotten.
 */
public record CreateServiceRequest(
        @NotBlank
        String name,

        String description,

        @Min(5)
        int durationMinutes,

        BigDecimal price,

        String color,

        /**
         * Minutes to leave blocked immediately before this service starts.
         * e.g. 10 = prep time; the previous slot ends at least 10 min before.
         * Default: 0 (no buffer).
         */
        @Min(0) @Max(120)
        int bufferBeforeMinutes,

        /**
         * Minutes to leave blocked immediately after this service ends.
         * e.g. 15 = cleanup/travel; next slot starts at least 15 min later.
         * Default: 0 (no buffer).
         */
        @Min(0) @Max(120)
        int bufferAfterMinutes,

        /**
         * Minimum minutes between now and the booking slot.
         * e.g. 120 = client must book at least 2 hours before their slot.
         * Default: 0 (book any time).
         */
        @Min(0)
        int minLeadTimeMinutes,

        /**
         * Maximum days in the future a booking can be made.
         * e.g. 90 = no bookings beyond 90 days from today.
         * Default: 365.
         */
        @Min(1) @Max(730)
        int maxAdvanceDays
) {}
