package za.co.handyflow.platform.events.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Someone waiting for a place at a full event (or a full ticket tier).
 * FIFO by createdAt. An entry is WAITING until it is either PROMOTED
 * (a real EventGuest was created from it) or REMOVED (withdrawn by staff,
 * superseded by a direct registration, or closed because the event ended).
 */
@Entity
@Table(name = "event_waitlist")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventWaitlistEntry {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)  private UUID tenantId;
    @Column(name = "event_id", nullable = false)   private UUID eventId;
    @Column(name = "tier_id")                      private UUID tierId;
    @Column(name = "customer_id")                  private UUID customerId;
    @Column(name = "full_name", nullable = false)  private String fullName;
    @Column(name = "email", nullable = false)      private String email;
    @Column(name = "phone")                        private String phone;
    @Column(name = "company")                      private String company;
    @Column(name = "dietary_requirements")         private String dietaryRequirements;
    @Column(name = "status", nullable = false)     private String status = "WAITING";
    @Column(name = "promoted_guest_id")            private UUID promotedGuestId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public static EventWaitlistEntry join(TenantId tenantId, UUID eventId, UUID tierId, UUID customerId,
                                          String fullName, String email, String phone,
                                          String company, String dietaryRequirements) {
        EventWaitlistEntry e = new EventWaitlistEntry();
        e.id                   = UUID.randomUUID();
        e.tenantId             = tenantId.getValue();
        e.eventId              = eventId;
        e.tierId               = tierId;
        e.customerId           = customerId;
        e.fullName             = fullName;
        e.email                = email;
        e.phone                = phone;
        e.company              = company;
        e.dietaryRequirements  = dietaryRequirements;
        e.status               = "WAITING";
        e.createdAt            = Instant.now();
        e.updatedAt            = Instant.now();
        return e;
    }

    public boolean isWaiting() {
        return "WAITING".equals(status);
    }

    public void promote(UUID guestId) {
        if (!isWaiting()) throw new IllegalStateException("Only WAITING entries can be promoted (status is " + status + ")");
        this.status          = "PROMOTED";
        this.promotedGuestId = guestId;
        this.updatedAt       = Instant.now();
    }

    public void remove() {
        if (!isWaiting()) throw new IllegalStateException("Only WAITING entries can be removed (status is " + status + ")");
        this.status    = "REMOVED";
        this.updatedAt = Instant.now();
    }
}