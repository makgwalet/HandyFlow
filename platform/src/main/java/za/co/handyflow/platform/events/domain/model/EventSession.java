package za.co.handyflow.platform.events.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * One slot in the event's programme: a keynote, a breakout, a break, setup, breakdown.
 * <p>
 * SCHEDULED -> IN_PROGRESS -> COMPLETED, or CANCELLED from SCHEDULED / IN_PROGRESS.
 * Times are wall-clock at the venue (same convention as Event.startDatetime). The planned
 * start/end never change when a session runs late; what actually happened is recorded
 * separately in actualStart / actualEnd, so the run sheet can show the gap.
 */
@Entity
@Table(name = "event_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventSession {

    public static final Set<String> TYPES = Set.of(
            "KEYNOTE", "SESSION", "BREAKOUT", "PANEL", "WORKSHOP", "BREAK", "MEAL",
            "REGISTRATION", "ENTERTAINMENT", "SETUP", "BREAKDOWN", "OTHER");

    /** Setup and breakdown legitimately happen before the doors open / after the event ends. */
    public static final Set<String> MAY_FALL_OUTSIDE_EVENT = Set.of("SETUP", "BREAKDOWN");

    public static final String SCHEDULED = "SCHEDULED";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)    private UUID tenantId;
    @Column(name = "event_id", nullable = false)     private UUID eventId;
    @Column(name = "title", nullable = false)        private String title;
    @Column(name = "description")                    private String description;
    @Column(name = "session_type", nullable = false) private String sessionType;
    @Column(name = "room")                           private String room;
    @Column(name = "speaker")                        private String speaker;
    @Column(name = "start_at", nullable = false)     private LocalDateTime startAt;
    @Column(name = "end_at", nullable = false)       private LocalDateTime endAt;
    @Column(name = "status", nullable = false)       private String status = SCHEDULED;
    @Column(name = "actual_start")                   private LocalDateTime actualStart;
    @Column(name = "actual_end")                     private LocalDateTime actualEnd;
    @Column(name = "notes")                          private String notes;
    @Column(name = "created_at", nullable = false)   private Instant createdAt;
    @Column(name = "updated_at", nullable = false)   private Instant updatedAt;

    public static EventSession create(TenantId tenantId, UUID eventId, String title, String description,
                                      String sessionType, String room, String speaker,
                                      LocalDateTime startAt, LocalDateTime endAt, String notes) {
        EventSession s = new EventSession();
        s.id        = UUID.randomUUID();
        s.tenantId  = tenantId.getValue();
        s.eventId   = eventId;
        s.status    = SCHEDULED;
        s.createdAt = Instant.now();
        s.apply(title, description, sessionType, room, speaker, startAt, endAt, notes);
        return s;
    }

    /** Edits a session that hasn't finished. Completed and cancelled sessions are history. */
    public void update(String title, String description, String sessionType, String room, String speaker,
                       LocalDateTime startAt, LocalDateTime endAt, String notes) {
        if (COMPLETED.equals(status) || CANCELLED.equals(status))
            throw new IllegalStateException("A " + status + " session can't be edited");
        apply(title, description, sessionType, room, speaker, startAt, endAt, notes);
    }

    private void apply(String title, String description, String sessionType, String room, String speaker,
                       LocalDateTime startAt, LocalDateTime endAt, String notes) {
        if (title == null || title.isBlank())
            throw new IllegalArgumentException("A session needs a title");
        String type = normaliseType(sessionType);
        if (!TYPES.contains(type))
            throw new IllegalArgumentException("Unknown session type '" + sessionType + "'. Valid: " + TYPES);
        if (startAt == null || endAt == null)
            throw new IllegalArgumentException("A session needs a start and an end time");
        if (!endAt.isAfter(startAt))
            throw new IllegalArgumentException("A session must end after it starts");

        this.title       = title.trim();
        this.description = description;
        this.sessionType = type;
        this.room        = blankToNull(room);
        this.speaker     = blankToNull(speaker);
        this.startAt     = startAt;
        this.endAt       = endAt;
        this.notes       = notes;
        this.updatedAt   = Instant.now();
    }

    /** Anything not cancelled still occupies its room and its speaker. */
    public boolean isActive() {
        return !CANCELLED.equals(status);
    }

    public void start(LocalDateTime now) {
        if (!SCHEDULED.equals(status))
            throw new IllegalStateException("Only a SCHEDULED session can be started (status is " + status + ")");
        this.status      = IN_PROGRESS;
        this.actualStart = now;
        this.updatedAt   = Instant.now();
    }

    public void complete(LocalDateTime now) {
        if (!IN_PROGRESS.equals(status))
            throw new IllegalStateException("Only an IN_PROGRESS session can be completed (status is " + status + ")");
        this.status    = COMPLETED;
        this.actualEnd = now;
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        if (!SCHEDULED.equals(status) && !IN_PROGRESS.equals(status))
            throw new IllegalStateException("Only a SCHEDULED or IN_PROGRESS session can be cancelled (status is " + status + ")");
        this.status    = CANCELLED;
        this.updatedAt = Instant.now();
    }

    /** "Running 20 minutes late": moves a not-yet-started session. Started or finished ones stay as they were. */
    public void shiftBy(long minutes) {
        if (!SCHEDULED.equals(status))
            throw new IllegalStateException("Only a SCHEDULED session can be shifted (status is " + status + ")");
        this.startAt   = startAt.plusMinutes(minutes);
        this.endAt     = endAt.plusMinutes(minutes);
        this.updatedAt = Instant.now();
    }

    /** Minutes the session actually started after (positive) or before (negative) its plan; null if not started. */
    public Long delayMinutes() {
        return actualStart == null ? null : Duration.between(startAt, actualStart).toMinutes();
    }

    static String normaliseType(String type) {
        return type == null ? "" : type.trim().toUpperCase().replace(' ', '_').replace('-', '_');
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}