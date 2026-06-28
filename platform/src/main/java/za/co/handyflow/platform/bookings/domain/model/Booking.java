package za.co.handyflow.platform.bookings.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Booking — the core appointment record.
 *
 * NEW in this version (V97 migration):
 *
 * originalBookingDate + originalStartTime — audit trail for reschedules.
 *
 * WHY NOT cancel + recreate for reschedule?
 * The alternative — cancel the existing booking and create a new one — destroys
 * the business's analytics:
 *   - No-show rate inflates (cancelled booking looks like a no-show lead)
 *   - Revenue history loses the original booking date
 *   - The client gets a new booking number for the same appointment
 *   - The invoice link (invoiceId) would break if an invoice was already raised
 *
 * Keeping the same record and storing the original slot as audit columns means:
 *   - Same booking number → same invoice → no orphaned invoices
 *   - Analytics can distinguish "rescheduled" from "cancelled"
 *   - Staff can see "this was originally for Monday, moved to Wednesday"
 *   - The frontend can show a "rescheduled" badge on affected bookings
 *
 * rescheduledAt — timestamp of the reschedule action.
 * originalBookingDate / originalStartTime — null until first reschedule.
 * If rescheduled again, the ORIGINAL dates are preserved (not overwritten)
 * so the audit trail always shows the first-ever slot.
 */
@Entity
@Table(name = "bookings")
@Getter
@NoArgsConstructor
public class Booking {

    @Id UUID id;
    @Column(name = "tenant_id")        UUID tenantId;
    @Column(name = "booking_number")   String bookingNumber;
    @Column(name = "service_id")       UUID serviceId;
    @Column(name = "staff_id")         UUID staffId;
    @Column(name = "customer_id")      UUID customerId;
    @Column(name = "client_name")      String clientName;
    @Column(name = "client_email")     String clientEmail;
    @Column(name = "client_phone")     String clientPhone;
    @Column(name = "booking_date")     LocalDate bookingDate;
    @Column(name = "start_time")       LocalTime startTime;
    @Column(name = "end_time")         LocalTime endTime;
    @Column(name = "duration_minutes") int durationMinutes;
    String status = "PENDING";
    BigDecimal price;
    String currency = "ZAR";
    @Column(name = "invoice_id")       UUID invoiceId;
    String notes;
    @Column(name = "internal_notes")   String internalNotes;
    @Column(name = "cancellation_reason") String cancellationReason;
    @Column(name = "reminder_sent")    boolean reminderSent;
    @Column(name = "reminder_sent_at") Instant reminderSentAt;
    @Column(name = "confirmed_at")     Instant confirmedAt;
    @Column(name = "completed_at")     Instant completedAt;
    @Column(name = "cancelled_at")     Instant cancelledAt;

    // ── Reschedule audit columns (added by V97) ───────────────────────────────
    //
    // These are NULL until the first reschedule.  We only write them once —
    // if a booking is rescheduled again, the ORIGINAL values are kept intact
    // so the audit trail always reflects where the appointment started.
    //
    // WHY @Column(insertable=false, updatable=true)?
    // These columns must be insertable=true (default) so Hibernate can write
    // them on the first update.  They start null and are only populated by
    // the reschedule() method.  No special mapping needed — standard nullable
    // columns with default JPA behaviour.
    @Column(name = "original_booking_date") LocalDate originalBookingDate;
    @Column(name = "original_start_time")   LocalTime originalStartTime;
    @Column(name = "rescheduled_at")        Instant   rescheduledAt;

    @Column(name = "created_at") Instant createdAt;
    @Column(name = "updated_at") Instant updatedAt;

    // ── Factory ───────────────────────────────────────────────────────────────

    public static Booking create(TenantId tenantId, String bookingNumber,
                                 UUID serviceId, UUID staffId, UUID customerId,
                                 String clientName, String clientEmail,
                                 String clientPhone, LocalDate bookingDate,
                                 LocalTime startTime, int durationMinutes,
                                 BigDecimal price, String notes) {
        Booking b = new Booking();
        b.id              = UUID.randomUUID();
        b.tenantId        = tenantId.getValue();
        b.bookingNumber   = bookingNumber;
        b.serviceId       = serviceId;
        b.staffId         = staffId;
        b.customerId      = customerId;
        b.clientName      = clientName;
        b.clientEmail     = clientEmail;
        b.clientPhone     = clientPhone;
        b.bookingDate     = bookingDate;
        b.startTime       = startTime;
        b.endTime         = startTime.plusMinutes(durationMinutes);
        b.durationMinutes = durationMinutes;
        b.status          = "PENDING";
        b.price           = price;
        b.currency        = "ZAR";
        b.notes           = notes;
        b.reminderSent    = false;
        b.createdAt       = Instant.now();
        b.updatedAt       = Instant.now();
        return b;
    }

    // ── Domain methods ────────────────────────────────────────────────────────

    public void confirm() {
        if (!"PENDING".equals(status))
            throw new IllegalStateException("Only PENDING bookings can be confirmed");
        this.status      = "CONFIRMED";
        this.confirmedAt = Instant.now();
        this.updatedAt   = Instant.now();
    }

    public void start() {
        if (!java.util.List.of("PENDING", "CONFIRMED").contains(status))
            throw new IllegalStateException("Booking must be PENDING or CONFIRMED to start");
        this.status    = "IN_PROGRESS";
        this.updatedAt = Instant.now();
    }

    public void complete() {
        this.status      = "COMPLETED";
        this.completedAt = Instant.now();
        this.updatedAt   = Instant.now();
    }

    public void cancel(String reason) {
        if (java.util.List.of("COMPLETED", "CANCELLED", "NO_SHOW").contains(status))
            throw new IllegalStateException("Cannot cancel a " + status + " booking");
        this.status             = "CANCELLED";
        this.cancellationReason = reason;
        this.cancelledAt        = Instant.now();
        this.updatedAt          = Instant.now();
    }

    public void markNoShow() {
        this.status      = "NO_SHOW";
        this.cancelledAt = Instant.now();
        this.updatedAt   = Instant.now();
    }

    /**
     * Reschedule this booking to a new date and time.
     *
     * WHY preserve originalBookingDate only on the FIRST reschedule?
     * If a booking is rescheduled Monday→Wednesday→Friday, we want the audit
     * trail to show "originally Monday", not "previously Wednesday".
     * The originalBookingDate and originalStartTime are only written when they
     * are null — i.e. the first time this booking is rescheduled.
     *
     * @param newDate       The new booking date
     * @param newStartTime  The new start time
     * @param newEndTime    Pre-calculated end time (startTime + durationMinutes)
     */
    public void reschedule(LocalDate newDate, LocalTime newStartTime, LocalTime newEndTime) {
        // Only capture original on the FIRST reschedule
        if (this.originalBookingDate == null) {
            this.originalBookingDate = this.bookingDate;
            this.originalStartTime   = this.startTime;
        }

        this.bookingDate   = newDate;
        this.startTime     = newStartTime;
        this.endTime       = newEndTime;
        this.rescheduledAt = Instant.now();
        this.updatedAt     = Instant.now();
    }

    public void markReminderSent() {
        this.reminderSent   = true;
        this.reminderSentAt = Instant.now();
        this.updatedAt      = Instant.now();
    }

    public void linkInvoice(UUID invoiceId) {
        this.invoiceId = invoiceId;
        this.updatedAt = Instant.now();
    }
}
