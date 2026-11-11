package za.co.handyflow.platform.events.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * One person on one shift at one event — staff, volunteer or contractor.
 * ASSIGNED -> CONFIRMED -> ARRIVED, with NO_SHOW and CANCELLED as the other exits.
 * <p>
 * userId is set when the person is a HandyFlow user; that is what lets the service refuse to
 * put the same person on two overlapping shifts, including across different events. People who
 * aren't platform users can't be matched reliably (names and phone numbers aren't identities),
 * so no clash check is attempted for them.
 */
@Entity
@Table(name = "event_staff")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventStaffAssignment {

    public static final Set<String> ROLES = Set.of(
            "COORDINATOR", "STEWARD", "REGISTRATION", "SECURITY", "FIRST_AID",
            "AV_TECH", "CATERING", "DRIVER", "VOLUNTEER_LEAD", "OTHER");
    public static final Set<String> KINDS = Set.of("STAFF", "VOLUNTEER", "CONTRACTOR");

    public static final String ASSIGNED = "ASSIGNED";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String ARRIVED = "ARRIVED";
    public static final String NO_SHOW = "NO_SHOW";
    public static final String CANCELLED = "CANCELLED";

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)    private UUID tenantId;
    @Column(name = "event_id", nullable = false)     private UUID eventId;
    @Column(name = "user_id")                        private UUID userId;
    @Column(name = "full_name", nullable = false)    private String fullName;
    @Column(name = "kind", nullable = false)         private String kind;
    @Column(name = "role", nullable = false)         private String role;
    @Column(name = "phone")                          private String phone;
    @Column(name = "email")                          private String email;
    @Column(name = "post")                           private String post;
    @Column(name = "shift_start", nullable = false)  private LocalDateTime shiftStart;
    @Column(name = "shift_end", nullable = false)    private LocalDateTime shiftEnd;
    @Column(name = "status", nullable = false)       private String status = ASSIGNED;
    @Column(name = "arrived_at")                     private Instant arrivedAt;
    @Column(name = "notes")                          private String notes;
    @Column(name = "created_at", nullable = false)   private Instant createdAt;
    @Column(name = "updated_at", nullable = false)   private Instant updatedAt;

    public static EventStaffAssignment create(TenantId tenantId, UUID eventId, UUID userId, String fullName,
                                              String kind, String role, String phone, String email, String post,
                                              LocalDateTime shiftStart, LocalDateTime shiftEnd, String notes) {
        EventStaffAssignment a = new EventStaffAssignment();
        a.id        = UUID.randomUUID();
        a.tenantId  = tenantId.getValue();
        a.eventId   = eventId;
        a.status    = ASSIGNED;
        a.createdAt = Instant.now();
        a.apply(userId, fullName, kind, role, phone, email, post, shiftStart, shiftEnd, notes);
        return a;
    }

    public void update(UUID userId, String fullName, String kind, String role, String phone, String email,
                       String post, LocalDateTime shiftStart, LocalDateTime shiftEnd, String notes) {
        if (!ASSIGNED.equals(status) && !CONFIRMED.equals(status))
            throw new IllegalStateException("Only an ASSIGNED or CONFIRMED assignment can be edited (status is " + status + ")");
        apply(userId, fullName, kind, role, phone, email, post, shiftStart, shiftEnd, notes);
    }

    private void apply(UUID userId, String fullName, String kind, String role, String phone, String email,
                       String post, LocalDateTime shiftStart, LocalDateTime shiftEnd, String notes) {
        if (fullName == null || fullName.isBlank())
            throw new IllegalArgumentException("A name is required");
        String k = norm(kind);
        String r = norm(role);
        if (!KINDS.contains(k))
            throw new IllegalArgumentException("Unknown kind '" + kind + "'. Valid: " + KINDS);
        if (!ROLES.contains(r))
            throw new IllegalArgumentException("Unknown role '" + role + "'. Valid: " + ROLES);
        if (shiftStart == null || shiftEnd == null || !shiftEnd.isAfter(shiftStart))
            throw new IllegalArgumentException("A shift must end after it starts");

        this.userId     = userId;
        this.fullName   = fullName.trim();
        this.kind       = k;
        this.role       = r;
        this.phone      = phone;
        this.email      = email;
        this.post       = post;
        this.shiftStart = shiftStart;
        this.shiftEnd   = shiftEnd;
        this.notes      = notes;
        this.updatedAt  = Instant.now();
    }

    /** Counts toward coverage: assigned, confirmed or arrived. */
    public boolean isActive() {
        return ASSIGNED.equals(status) || CONFIRMED.equals(status) || ARRIVED.equals(status);
    }

    public void confirm() {
        if (!ASSIGNED.equals(status))
            throw new IllegalStateException("Only an ASSIGNED shift can be confirmed (status is " + status + ")");
        this.status    = CONFIRMED;
        this.updatedAt = Instant.now();
    }

    public void arrive() {
        if (!ASSIGNED.equals(status) && !CONFIRMED.equals(status))
            throw new IllegalStateException("Only an ASSIGNED or CONFIRMED shift can be marked arrived (status is " + status + ")");
        this.status    = ARRIVED;
        this.arrivedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markNoShow() {
        if (!ASSIGNED.equals(status) && !CONFIRMED.equals(status))
            throw new IllegalStateException("Only an ASSIGNED or CONFIRMED shift can be a no-show (status is " + status + ")");
        this.status    = NO_SHOW;
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        if (!ASSIGNED.equals(status) && !CONFIRMED.equals(status))
            throw new IllegalStateException("Only an ASSIGNED or CONFIRMED shift can be cancelled (status is " + status + ")");
        this.status    = CANCELLED;
        this.updatedAt = Instant.now();
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toUpperCase().replace(' ', '_').replace('-', '_');
    }
}