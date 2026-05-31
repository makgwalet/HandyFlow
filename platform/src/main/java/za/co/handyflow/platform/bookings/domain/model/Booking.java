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

@Entity
@Table(name = "bookings")
@Getter
@NoArgsConstructor
public class Booking {

    @Id UUID id;
    @Column(name = "tenant_id")       UUID tenantId;
    @Column(name = "booking_number")  String bookingNumber;
    @Column(name = "service_id")      UUID serviceId;
    @Column(name = "staff_id")        UUID staffId;
    @Column(name = "customer_id")     UUID customerId;
    @Column(name = "client_name")     String clientName;
    @Column(name = "client_email")    String clientEmail;
    @Column(name = "client_phone")    String clientPhone;
    @Column(name = "booking_date")    LocalDate bookingDate;
    @Column(name = "start_time")      LocalTime startTime;
    @Column(name = "end_time")        LocalTime endTime;
    @Column(name = "duration_minutes") int durationMinutes;
    String status = "PENDING";
    BigDecimal price;
    String currency = "ZAR";
    @Column(name = "invoice_id")      UUID invoiceId;
    String notes;
    @Column(name = "internal_notes")  String internalNotes;
    @Column(name = "cancellation_reason") String cancellationReason;
    @Column(name = "reminder_sent")   boolean reminderSent;
    @Column(name = "reminder_sent_at") Instant reminderSentAt;
    @Column(name = "confirmed_at")    Instant confirmedAt;
    @Column(name = "completed_at")    Instant completedAt;
    @Column(name = "cancelled_at")    Instant cancelledAt;
    @Column(name = "created_at")      Instant createdAt;
    @Column(name = "updated_at")      Instant updatedAt;

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

    public void confirm() {
        if (!"PENDING".equals(status))
            throw new IllegalStateException("Only PENDING bookings can be confirmed");
        this.status      = "CONFIRMED";
        this.confirmedAt = Instant.now();
        this.updatedAt   = Instant.now();
    }

    public void start() {
        if (!java.util.List.of("PENDING","CONFIRMED").contains(status))
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
        if (java.util.List.of("COMPLETED","CANCELLED","NO_SHOW").contains(status))
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