package za.co.handyflow.platform.events.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Something that went wrong, or might, on the day — a medical call-out, a security matter, a
 * power failure, a complaint.
 * <p>
 * OPEN -> IN_PROGRESS (someone has taken it) -> RESOLVED -> CLOSED, with ESCALATED reachable from
 * any unresolved state and REOPEN taking a resolved/closed incident back to IN_PROGRESS.
 * <p>
 * Escalation is a LEVEL (0-3), not just a status: an incident can be escalated more than once as it
 * goes up the chain, and the level says how far. It is capped, because "escalated" a fifth time
 * means nothing a person can act on.
 * <p>
 * @Version makes concurrent edits fail loudly rather than overwrite each other — two people
 * resolving and escalating the same incident at once is exactly when you want to know.
 */
@Entity
@Table(name = "event_incidents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventIncident {

    public static final Set<String> CATEGORIES = Set.of(
            "MEDICAL", "SECURITY", "SAFETY", "FACILITY", "LOGISTICS", "GUEST_COMPLAINT",
            "SUPPLIER", "WEATHER", "OTHER");
    public static final List<String> SEVERITIES = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");   // ascending
    public static final int MAX_ESCALATION_LEVEL = 3;

    public static final String OPEN = "OPEN";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String ESCALATED = "ESCALATED";
    public static final String RESOLVED = "RESOLVED";
    public static final String CLOSED = "CLOSED";

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)    private UUID tenantId;
    @Column(name = "event_id", nullable = false)     private UUID eventId;
    @Column(name = "reference", nullable = false)    private String reference;
    @Column(name = "title", nullable = false)        private String title;
    @Column(name = "description")                    private String description;
    @Column(name = "category", nullable = false)     private String category;
    @Column(name = "severity", nullable = false)     private String severity;
    @Column(name = "status", nullable = false)       private String status = OPEN;
    @Column(name = "location")                       private String location;

    @Column(name = "reported_by_name")               private String reportedByName;
    @Column(name = "reported_by_user_id")            private UUID reportedByUserId;
    @Column(name = "assigned_to_name")               private String assignedToName;
    @Column(name = "assigned_to_user_id")            private UUID assignedToUserId;

    @Column(name = "escalation_level", nullable = false) private int escalationLevel;
    @Column(name = "escalated_at")                   private Instant escalatedAt;
    @Column(name = "acknowledged_at")                private Instant acknowledgedAt;
    @Column(name = "resolved_at")                    private Instant resolvedAt;
    @Column(name = "resolution")                     private String resolution;

    @Version
    @Column(name = "version", nullable = false)      private long version;

    @Column(name = "created_at", nullable = false)   private Instant createdAt;
    @Column(name = "updated_at", nullable = false)   private Instant updatedAt;

    public static EventIncident report(TenantId tenantId, UUID eventId, String reference, String title,
                                       String description, String category, String severity, String location,
                                       UUID reporterId, String reporterName) {
        if (title == null || title.isBlank())
            throw new IllegalArgumentException("An incident needs a title");
        String cat = norm(category);
        String sev = norm(severity);
        if (!CATEGORIES.contains(cat))
            throw new IllegalArgumentException("Unknown incident category '" + category + "'. Valid: " + CATEGORIES);
        if (!SEVERITIES.contains(sev))
            throw new IllegalArgumentException("Unknown severity '" + severity + "'. Valid: " + SEVERITIES);

        EventIncident i = new EventIncident();
        i.id               = UUID.randomUUID();
        i.tenantId         = tenantId.getValue();
        i.eventId          = eventId;
        i.reference        = reference;
        i.title            = title.trim();
        i.description      = description;
        i.category         = cat;
        i.severity         = sev;
        i.status           = OPEN;
        i.location         = location;
        i.reportedByUserId = reporterId;
        i.reportedByName   = reporterName;
        i.createdAt        = Instant.now();
        i.updatedAt        = Instant.now();
        return i;
    }

    /** Anything not yet resolved or closed still needs someone's attention. */
    public boolean isUnresolved() {
        return OPEN.equals(status) || IN_PROGRESS.equals(status) || ESCALATED.equals(status);
    }

    /** Hands the incident to someone. An OPEN incident becomes IN_PROGRESS; an escalated one stays escalated. */
    public void assign(String name, UUID userId) {
        requireUnresolved("assigned");
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Say who the incident is assigned to");
        this.assignedToName   = name.trim();
        this.assignedToUserId = userId;
        if (OPEN.equals(status)) this.status = IN_PROGRESS;
        if (acknowledgedAt == null) this.acknowledgedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /** Someone takes it without handing it on — stops the "nobody has seen this" clock. */
    public void acknowledge() {
        requireUnresolved("acknowledged");
        if (OPEN.equals(status)) this.status = IN_PROGRESS;
        if (acknowledgedAt == null) this.acknowledgedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Moves the incident up the chain. Optionally raises the severity too — never lowers it, because
     * escalating an incident while quietly marking it less serious would be contradictory.
     */
    public void escalate(String raiseSeverityTo) {
        requireUnresolved("escalated");
        if (escalationLevel >= MAX_ESCALATION_LEVEL)
            throw new IllegalStateException("This incident is already at the highest escalation level");

        // Validate everything BEFORE changing anything, so a bad argument can't leave the incident half-escalated.
        String target = null;
        if (raiseSeverityTo != null && !raiseSeverityTo.isBlank()) {
            target = norm(raiseSeverityTo);
            if (!SEVERITIES.contains(target))
                throw new IllegalArgumentException("Unknown severity '" + raiseSeverityTo + "'. Valid: " + SEVERITIES);
        }

        this.escalationLevel++;
        this.status      = ESCALATED;
        this.escalatedAt = Instant.now();
        if (target != null && SEVERITIES.indexOf(target) > SEVERITIES.indexOf(severity)) this.severity = target;
        this.updatedAt = Instant.now();
    }

    public void resolve(String resolution) {
        requireUnresolved("resolved");
        if (resolution == null || resolution.isBlank())
            throw new IllegalArgumentException("Say how the incident was resolved");
        this.status     = RESOLVED;
        this.resolution = resolution.trim();
        this.resolvedAt = Instant.now();
        if (acknowledgedAt == null) this.acknowledgedAt = this.resolvedAt;
        this.updatedAt  = Instant.now();
    }

    public void close() {
        if (!RESOLVED.equals(status))
            throw new IllegalStateException("Only a RESOLVED incident can be closed (status is " + status + ")");
        this.status    = CLOSED;
        this.updatedAt = Instant.now();
    }

    public void reopen() {
        if (!RESOLVED.equals(status) && !CLOSED.equals(status))
            throw new IllegalStateException("Only a RESOLVED or CLOSED incident can be reopened (status is " + status + ")");
        this.status     = IN_PROGRESS;
        this.resolvedAt = null;
        this.resolution = null;
        this.updatedAt  = Instant.now();
    }

    private void requireUnresolved(String verb) {
        if (!isUnresolved())
            throw new IllegalStateException("A " + status + " incident can't be " + verb);
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toUpperCase().replace(' ', '_').replace('-', '_');
    }
}