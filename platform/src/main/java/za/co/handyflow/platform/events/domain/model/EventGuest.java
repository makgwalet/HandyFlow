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

    /** STAFF | GROUP | WALK_IN | IMPORT | WAITLIST | TRANSFER | CHECKOUT */
    @Column(name = "source", nullable = false)
    String source = "STAFF";

    /** Shared by every member of one group registration; null otherwise. */
    @Column(name = "group_ref")           UUID groupRef;

    /** For a TRANSFER ticket: the id of the guest record whose ticket it replaced. */
    @Column(name = "transferred_from")    UUID transferredFrom;

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
        g.source               = "STAFF";
        g.amountPaid           = amountPaid != null ? amountPaid : BigDecimal.ZERO;
        g.paymentStatus        = isFree ? "FREE" : "PENDING";
        g.createdAt            = Instant.now();
        g.updatedAt            = Instant.now();
        return g;
    }

    /** Records how this registration came about. Returns this for chaining after create(). */
    public EventGuest withProvenance(String source, UUID groupRef, UUID transferredFrom) {
        this.source          = source != null ? source : "STAFF";
        this.groupRef        = groupRef;
        this.transferredFrom = transferredFrom;
        return this;
    }

    public void confirm() {
        this.status    = "CONFIRMED";
        this.updatedAt = Instant.now();
    }

    /** Only a live (REGISTERED/CONFIRMED) ticket can be redeemed — a second scan must never re-check-in. */
    public void checkIn(UUID scannedBy) {
        if (!"REGISTERED".equals(status) && !"CONFIRMED".equals(status))
            throw new IllegalStateException("Only REGISTERED or CONFIRMED guests can be checked in (status is " + status + ")");
        this.status       = "CHECKED_IN";
        this.checkedInAt  = Instant.now();
        this.checkedInBy  = scannedBy;
        this.updatedAt    = Instant.now();
    }

    /** A guest who has already been admitted, or whose ticket moved to someone else, cannot be cancelled. */
    public void cancel() {
        if ("CHECKED_IN".equals(status))
            throw new IllegalStateException("Cannot cancel a guest who has already checked in");
        if ("TRANSFERRED".equals(status))
            throw new IllegalStateException("Cannot cancel a ticket that has been transferred");
        if ("EXPIRED".equals(status))
            throw new IllegalStateException("This reservation has already expired");
        this.status    = "CANCELLED";
        this.updatedAt = Instant.now();
    }

    /**
     * Ends this holder's claim on the ticket. The seat itself is NOT released —
     * it moves to the new holder's record. The old QR code stops working
     * because check-in reports TRANSFERRED_TICKET for this status.
     */
    public void markTransferred() {
        if (!"REGISTERED".equals(status) && !"CONFIRMED".equals(status))
            throw new IllegalStateException("Only active, not-yet-checked-in tickets can be transferred (status is " + status + ")");
        this.status    = "TRANSFERRED";
        this.updatedAt = Instant.now();
    }

    /** A transferred ticket carries the original payment position — the new holder doesn't pay twice. */
    public void inheritPaymentFrom(EventGuest from) {
        this.amountPaid    = from.amountPaid;
        this.paymentStatus = from.paymentStatus;
        this.updatedAt     = Instant.now();
    }

    // ── Paid-checkout lifecycle (Chunk 6) ─────────────────────────────────────
    // RESERVED = a seat is held for someone who hasn't paid yet. It counts against
    // capacity and tier inventory (that is the point of holding it) but the ticket
    // is NOT valid: check-in refuses a RESERVED guest, and the hold is released if
    // payment doesn't arrive in time (EXPIRED) or fails.

    /** Turns a freshly created registration into an unpaid hold. */
    public void reserve() {
        if (!"REGISTERED".equals(status))
            throw new IllegalStateException("Only a new registration can be reserved (status is " + status + ")");
        this.status        = "RESERVED";
        this.paymentStatus = "PENDING";
        this.updatedAt     = Instant.now();
    }

    /** Payment arrived in time: the hold becomes a real, valid ticket. */
    public void confirmPaid(BigDecimal amount) {
        if (!"RESERVED".equals(status))
            throw new IllegalStateException("Only a RESERVED guest can be confirmed (status is " + status + ")");
        this.status        = "REGISTERED";
        this.amountPaid    = amount;
        this.paymentStatus = "PAID";
        this.updatedAt     = Instant.now();
    }

    /** The hold ran out (or payment failed) — the seat goes back to inventory. */
    public void expire() {
        if (!"RESERVED".equals(status))
            throw new IllegalStateException("Only a RESERVED guest can expire (status is " + status + ")");
        this.status    = "EXPIRED";
        this.updatedAt = Instant.now();
    }

    /** Payment arrived AFTER the hold expired and a seat was still available: honour the ticket. */
    public void reinstate(BigDecimal amount) {
        if (!"EXPIRED".equals(status))
            throw new IllegalStateException("Only an EXPIRED reservation can be reinstated (status is " + status + ")");
        this.status        = "REGISTERED";
        this.amountPaid    = amount;
        this.paymentStatus = "PAID";
        this.updatedAt     = Instant.now();
    }

    /** Reflects a refund on the guest: REFUNDED once everything is back, PARTIALLY_REFUNDED before that. */
    public void markRefund(boolean fullyRefunded) {
        this.paymentStatus = fullyRefunded ? "REFUNDED" : "PARTIALLY_REFUNDED";
        this.updatedAt     = Instant.now();
    }

    public void markPaid(BigDecimal amount) {
        this.amountPaid    = amount;
        this.paymentStatus = "PAID";
        this.updatedAt     = Instant.now();
    }
}