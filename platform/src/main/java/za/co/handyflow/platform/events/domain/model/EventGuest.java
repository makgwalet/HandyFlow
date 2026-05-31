package za.co.handyflow.platform.events.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_guests")
@Getter
@NoArgsConstructor
public class EventGuest {

    @Id UUID id;
    @Column(name = "tenant_id")           UUID tenantId;
    @Column(name = "event_id")            UUID eventId;
    @Column(name = "tier_id")             UUID tierId;
    @Column(name = "customer_id")         UUID customerId;
    @Column(name = "full_name")           String fullName;
    String email;
    String phone;
    String company;
    @Column(name = "dietary_requirements") String dietaryRequirements;
    @Column(name = "ticket_number")       String ticketNumber;
    @Column(name = "qr_code")            String qrCode;
    String status = "REGISTERED";
    @Column(name = "checked_in_at")       Instant checkedInAt;
    @Column(name = "checked_in_by")       UUID checkedInBy;
    @Column(name = "amount_paid")         BigDecimal amountPaid;
    @Column(name = "payment_status")      String paymentStatus;
    String notes;
    @Column(name = "created_at")          Instant createdAt;
    @Column(name = "updated_at")          Instant updatedAt;

    public static EventGuest create(TenantId tenantId, UUID eventId, UUID tierId,
                                    UUID customerId, String fullName, String email,
                                    String phone, String company,
                                    String dietaryRequirements, String ticketNumber,
                                    BigDecimal amountPaid, boolean isFree) {
        EventGuest g = new EventGuest();
        g.id                   = UUID.randomUUID();
        g.tenantId             = tenantId.getValue();
        g.eventId              = eventId;
        g.tierId               = tierId;
        g.customerId           = customerId;
        g.fullName             = fullName;
        g.email                = email;
        g.phone                = phone;
        g.company              = company;
        g.dietaryRequirements  = dietaryRequirements;
        g.ticketNumber         = ticketNumber;
        g.qrCode               = UUID.randomUUID().toString();
        g.status               = "REGISTERED";
        g.amountPaid           = amountPaid != null ? amountPaid : BigDecimal.ZERO;
        g.paymentStatus        = isFree ? "FREE" : "PENDING";
        g.createdAt            = Instant.now();
        g.updatedAt            = Instant.now();
        return g;
    }

    public void confirm() {
        this.status    = "CONFIRMED";
        this.updatedAt = Instant.now();
    }

    public void checkIn(UUID scannedBy) {
        this.status       = "CHECKED_IN";
        this.checkedInAt  = Instant.now();
        this.checkedInBy  = scannedBy;
        this.updatedAt    = Instant.now();
    }

    public void cancel() {
        this.status    = "CANCELLED";
        this.updatedAt = Instant.now();
    }

    public void markPaid(BigDecimal amount) {
        this.amountPaid    = amount;
        this.paymentStatus = "PAID";
        this.updatedAt     = Instant.now();
    }
}