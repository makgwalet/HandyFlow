package za.co.handyflow.platform.bookings.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * BookingService — the "what can be booked" entity.
 *
 * WHY add buffer time and lead time here and not just in the service layer?
 *
 * These are service-level policies: a "Full Body Massage" ALWAYS needs 15
 * minutes of cleanup after it, regardless of which staff member performs it.
 * Storing the policy on the service entity means:
 *   1. SlotEngine reads the buffer directly from the service — no extra config.
 *   2. The API can't bypass the policy (it's enforced at domain level).
 *   3. Staff can see "this service has a 15-minute buffer" when scheduling.
 *
 * The alternative — hardcode buffer/lead time in application config — would
 * require a code change + deploy every time a business owner adjusts their
 * buffer time.  Per-service configuration is far more flexible.
 *
 * V97 migration added these four columns to booking_services:
 *   buffer_before_minutes, buffer_after_minutes,
 *   min_lead_time_minutes, max_advance_days
 */
@Entity
@Table(name = "booking_services")
@Getter
@NoArgsConstructor
public class BookingService {

    @Id UUID id;
    @Column(name = "tenant_id")        UUID tenantId;
    String name;
    String description;
    @Column(name = "duration_minutes") int durationMinutes;
    BigDecimal price;
    String currency = "ZAR";
    String color    = "#0D9488";
    boolean active  = true;

    // ── Buffer time (added by V97) ────────────────────────────────────────────
    //
    // bufferBeforeMinutes: time required BEFORE this service starts.
    // Use case: a nail technician needs 5 min to set up UV lamps before the
    // client sits down.  The slot engine subtracts this from available start
    // times so setup is never skipped.
    //
    // bufferAfterMinutes: time required AFTER this service ends.
    // Use case: a hairdresser needs 10 min to sweep hair and sanitise the chair.
    // The slot engine extends the booking's effective end by this amount so
    // the next client's slot never overlaps the cleanup window.
    //
    // WHY store in minutes and not as a Duration?
    // PostgreSQL INT columns map cleanly to int.  Duration serialisation is
    // fragile (ISO-8601 strings in JSON) and adds complexity with no benefit
    // for values that will always be a round number of minutes.
    @Column(name = "buffer_before_minutes") int bufferBeforeMinutes = 0;
    @Column(name = "buffer_after_minutes")  int bufferAfterMinutes  = 0;

    // ── Lead time & advance booking limits (added by V97) ────────────────────
    //
    // minLeadTimeMinutes: how far in advance a booking MUST be made.
    // Use case: a mobile car wash needs 2 hours to route a technician to the
    // client's location.  If a client tries to book for "right now", the slot
    // is rejected with a clear message: "Must book at least 2 hours in advance."
    // Value 0 = no restriction (default).
    //
    // maxAdvanceDays: how far in the FUTURE a booking can be made.
    // Use case: a photographer only publishes their calendar 3 months out.
    // A client trying to book 6 months ahead gets: "Can only book up to 90 days
    // in advance."  This also prevents clients from locking slots so far ahead
    // that cancellations dominate the calendar.
    // Default 90 days = industry standard for most SA service businesses.
    @Column(name = "min_lead_time_minutes") int minLeadTimeMinutes = 0;
    @Column(name = "max_advance_days")      int maxAdvanceDays     = 90;

    @Column(name = "created_at") Instant createdAt;
    @Column(name = "updated_at") Instant updatedAt;
    @Column(name = "deleted_at") Instant deletedAt;

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * WHY 10 parameters and not a builder?
     * All parameters are required for a valid service.  A builder with 10
     * optional fields would compile with a half-initialised service — e.g. a
     * service with no name — and the error would surface at runtime instead of
     * at construction.  The long factory signature is self-documenting: if you
     * can't supply all 10, the service isn't ready to be created.
     *
     * If this becomes unwieldy, introduce a CreateBookingServiceCommand record
     * and pass that instead.
     */
    public static BookingService create(TenantId tenantId, String name,
                                        String description, int durationMinutes,
                                        BigDecimal price, String color,
                                        int bufferBeforeMinutes, int bufferAfterMinutes,
                                        int minLeadTimeMinutes, int maxAdvanceDays) {
        BookingService s = new BookingService();
        s.id                   = UUID.randomUUID();
        s.tenantId             = tenantId.getValue();
        s.name                 = name;
        s.description          = description;
        s.durationMinutes      = durationMinutes;
        s.price                = price != null ? price : BigDecimal.ZERO;
        s.color                = color != null ? color : "#0D9488";
        s.currency             = "ZAR";
        s.active               = true;
        s.bufferBeforeMinutes  = bufferBeforeMinutes;
        s.bufferAfterMinutes   = bufferAfterMinutes;
        s.minLeadTimeMinutes   = minLeadTimeMinutes;
        s.maxAdvanceDays       = maxAdvanceDays;
        s.createdAt            = Instant.now();
        s.updatedAt            = Instant.now();
        return s;
    }

    // ── Domain methods ────────────────────────────────────────────────────────

    public void update(String name, String description,
                       int durationMinutes, BigDecimal price, String color,
                       int bufferBeforeMinutes, int bufferAfterMinutes,
                       int minLeadTimeMinutes, int maxAdvanceDays) {
        this.name                  = name;
        this.description           = description;
        this.durationMinutes       = durationMinutes;
        this.price                 = price;
        this.color                 = color;
        this.bufferBeforeMinutes   = bufferBeforeMinutes;
        this.bufferAfterMinutes    = bufferAfterMinutes;
        this.minLeadTimeMinutes    = minLeadTimeMinutes;
        this.maxAdvanceDays        = maxAdvanceDays;
        this.updatedAt             = Instant.now();
    }

    public void deactivate() {
        this.active    = false;
        this.updatedAt = Instant.now();
    }

    public void softDelete() {
        this.active    = false;
        this.deletedAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
