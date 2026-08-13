package za.co.handyflow.platform.bookingagency.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An actual appointment booked on a client's behalf. Mirrors
 * bookings.Booking's role, scoped to an agency client rather than the
 * agency's own tenant.
 */
@Entity
@Table(name = "booka_bookings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookAgencyBooking {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "offering_id", nullable = false)
    private UUID offeringId;

    @Column(name = "booking_number", nullable = false)
    private String bookingNumber;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_phone")
    private String customerPhone;

    @Column(name = "customer_email")
    private String customerEmail;

    @Column(name = "start_datetime", nullable = false)
    private LocalDateTime startDatetime;

    @Column(name = "end_datetime", nullable = false)
    private LocalDateTime endDatetime;

    @Column(name = "status", nullable = false)
    private String status = "CONFIRMED"; // CONFIRMED | CANCELLED | COMPLETED | NO_SHOW

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static BookAgencyBooking create(UUID tenantId, UUID clientId, UUID resourceId, UUID offeringId,
                                           String bookingNumber, String customerName, String customerPhone,
                                           String customerEmail, LocalDateTime startDatetime, LocalDateTime endDatetime,
                                           String notes) {
        BookAgencyBooking b = new BookAgencyBooking();
        b.tenantId = tenantId;
        b.clientId = clientId;
        b.resourceId = resourceId;
        b.offeringId = offeringId;
        b.bookingNumber = bookingNumber;
        b.customerName = customerName;
        b.customerPhone = customerPhone;
        b.customerEmail = customerEmail;
        b.startDatetime = startDatetime;
        b.endDatetime = endDatetime;
        b.status = "CONFIRMED";
        b.notes = notes;
        b.createdAt = Instant.now();
        b.updatedAt = Instant.now();
        return b;
    }

    public void cancel() {
        if ("COMPLETED".equals(status)) {
            throw new IllegalStateException("Cannot cancel a completed booking");
        }
        this.status = "CANCELLED";
        this.updatedAt = Instant.now();
    }

    public void complete() {
        if (!"CONFIRMED".equals(status)) {
            throw new IllegalStateException("Only a CONFIRMED booking can be marked complete");
        }
        this.status = "COMPLETED";
        this.updatedAt = Instant.now();
    }

    public void markNoShow() {
        if (!"CONFIRMED".equals(status)) {
            throw new IllegalStateException("Only a CONFIRMED booking can be marked no-show");
        }
        this.status = "NO_SHOW";
        this.updatedAt = Instant.now();
    }

    public boolean isActive() {
        return "CONFIRMED".equals(status);
    }

    /** Simple half-open interval overlap check — [start, end) vs [start, end). */
    public boolean overlaps(LocalDateTime otherStart, LocalDateTime otherEnd) {
        return this.startDatetime.isBefore(otherEnd) && otherStart.isBefore(this.endDatetime);
    }
}