package za.co.handyflow.platform.crm.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * CustomerActivity — immutable audit record.
 *
 * WHY immutable?
 * An activity record represents something that HAPPENED in the past.
 * You can never un-happen it.  Making it immutable in code (no setters,
 * only a factory method, no JPA cascading updates) enforces this at the
 * language level.  If you need to correct a mistake, you add a new
 * CORRECTION activity — you never edit the old one.
 *
 * This is the Event Sourcing principle applied pragmatically: we're not
 * full event-sourced (the customer state is still a mutable entity), but
 * the audit log is append-only, which gives us auditability for free.
 */
@Entity
@Table(name = "customer_activities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * WHY store tenant_id here too?
     * So we can query activities by tenant without joining to customers.
     * Useful for cross-tenant admin reports or compliance exports.
     */
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /**
     * ManyToOne with LAZY loading.
     * WHY LAZY?  When we load a Customer we don't always need all its
     * activities.  LAZY means Hibernate only fetches them when you
     * actually access the collection, preventing expensive joins on
     * every customer load.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 50)
    private ActivityType activityType;

    /**
     * Flexible payload stored as JSONB.
     * For UPDATED: {"field": {"from": "old", "to": "new"}}
     * For STATUS_CHANGED: {"from": "ACTIVE", "to": "INACTIVE"}
     * For BOOKING_LINKED: {"bookingId": "uuid-here"}
     * Stored as Map<String, Object> so we don't need to define every
     * possible shape upfront.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payload;

    /** Free-text note — only populated for NOTE_ADDED activity type. */
    @Column(columnDefinition = "text")
    private String note;

    /** The user who triggered this activity. Null = system-initiated. */
    @Column(name = "performed_by")
    private UUID performedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ── Factory ──────────────────────────────────────────────────────────────

    /**
     * Static factory — the only way to create a CustomerActivity.
     * We don't expose a public constructor because activities should
     * only be created through Customer domain methods, never standalone.
     */
    static CustomerActivity of(
            Customer customer,
            ActivityType type,
            Map<String, ?> payload,
            String note,
            UUID performedBy
    ) {
        var a = new CustomerActivity();
        a.customer     = customer;
        a.tenantId     = customer.getTenantId().getValue();  // denormalized for query perf
        a.activityType = type;
        a.payload      = payload != null ? Map.copyOf((Map<String, Object>) payload) : null;
        a.note         = note;
        a.performedBy  = performedBy;
        a.createdAt    = Instant.now();
        return a;
    }

    /**
     * Factory for system-generated activities that are not attached to a
     * Customer JPA entity — used by schedulers and background jobs.
     *
     * WHY separate from of()?
     * of() requires a Customer entity (so it can copy tenantId and set the
     * bidirectional relationship).  Schedulers don't load Customer entities —
     * they work with IDs only.  This factory creates a standalone activity
     * row using raw IDs, which is correct for system-generated events like
     * inactivity flags and retention reviews.
     *
     * performedBy = null signals "system-triggered" in the timeline UI.
     */
    public static CustomerActivity systemEvent(TenantId tenantId,
                                               UUID customerId,
                                               ActivityType type,
                                               String note) {
        var a = new CustomerActivity();
        a.tenantId     = tenantId.getValue();
        a.activityType = type;
        a.note         = note;
        a.performedBy  = null;   // null = system (not a human user)
        a.createdAt    = Instant.now();
        // customer relationship left null — this activity is saved directly
        // via CustomerActivityRepository, not through the Customer aggregate.
        return a;
    }
}
