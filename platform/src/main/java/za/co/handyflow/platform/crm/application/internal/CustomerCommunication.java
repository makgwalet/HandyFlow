package za.co.handyflow.platform.crm.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * CustomerCommunication — actual email/call/meeting history with the
 * customer, not internal system events.
 *
 * FIX: "no email/communication log" gap — CustomerActivity's timeline
 * captures created/updated/tagged/booking-linked/etc, but nothing tracked
 * what was actually SAID to the customer and when. The audit's own
 * framing: "closing the biggest structural gap versus dedicated CRM
 * tools."
 * <p>
 * WHY its own entity, not a new ActivityType on CustomerActivity?
 * Same reasoning CustomerFollowUp already established for this module:
 * CustomerActivity is a fixed timeline of internal events a user would
 * filter through to find "did we call this customer last month?" —
 * mixing genuine communication history into that same stream makes
 * exactly the query a sales rep actually wants ("show me every call and
 * email with this customer") require filtering out everything else. A
 * dedicated log answers that question directly.
 * <p>
 * Manual logging only for now, not automatic capture — this system
 * doesn't have inbound call/email capture (no telephony integration, no
 * inbound-email parsing), so "log what happened" is the honest starting
 * point, not "we detected this automatically." If HandyFlow's own
 * outbound emails (invoices, quotes) get wired to auto-log here later,
 * that's an additive change on top of this, not a redesign.
 */
@Entity
@Table(name = "customer_communications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerCommunication {

    public enum Type      { CALL, EMAIL, MEETING, WHATSAPP, SMS, OTHER }
    public enum Direction { INBOUND, OUTBOUND }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Direction direction;

    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    /** When the communication actually happened — may be backdated if logged after the fact. */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "logged_by")
    private UUID loggedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static CustomerCommunication create(TenantId tenantId, UUID customerId, Type type,
                                               Direction direction, String summary,
                                               Instant occurredAt, UUID loggedBy) {
        var c = new CustomerCommunication();
        c.tenantId   = tenantId;
        c.customerId = customerId;
        c.type       = type;
        c.direction  = direction;
        c.summary    = summary;
        c.occurredAt = occurredAt;
        c.loggedBy   = loggedBy;
        c.createdAt  = Instant.now();
        return c;
    }
}