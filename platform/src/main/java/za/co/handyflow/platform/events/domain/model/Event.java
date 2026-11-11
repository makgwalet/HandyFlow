package za.co.handyflow.platform.events.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "events")
@Getter
@NoArgsConstructor
public class Event {

    @Id UUID id;
    @Column(name = "tenant_id")     UUID tenantId;
    @Column(name = "event_number")  String eventNumber;
    String title;
    String description;
    @Column(name = "event_type")    String eventType;
    String status = "DRAFT";
    @Column(name = "venue_name")    String venueName;
    @Column(name = "venue_address") String venueAddress;
    @Column(name = "venue_capacity") Integer venueCapacity;
    @Column(name = "start_datetime") LocalDateTime startDatetime;
    @Column(name = "end_datetime")   LocalDateTime endDatetime;
    String timezone = "Africa/Johannesburg";
    @Column(name = "cover_image_url") String coverImageUrl;
    @Column(name = "is_free")        boolean isFree;
    @Column(name = "is_private")     boolean isPrivate;
    @Column(name = "registration_deadline") LocalDateTime registrationDeadline;
    @Column(name = "survey_id")      UUID surveyId;
    String notes;
    @Column(name = "created_by")     UUID createdBy;
    @Column(name = "created_at")     Instant createdAt;
    @Column(name = "updated_at")     Instant updatedAt;
    @Column(name = "deleted_at")     Instant deletedAt;

    public static Event create(TenantId tenantId, String eventNumber,
                               String title, String description,
                               String eventType, String venueName,
                               String venueAddress, Integer venueCapacity,
                               LocalDateTime startDatetime, LocalDateTime endDatetime,
                               boolean isFree, boolean isPrivate,
                               LocalDateTime registrationDeadline,
                               String notes, UUID createdBy) {
        validateDates(startDatetime, endDatetime, registrationDeadline);
        Event e = new Event();
        e.id                   = UUID.randomUUID();
        e.tenantId             = tenantId.getValue();
        e.eventNumber          = eventNumber;
        e.title                = title;
        e.description          = description;
        e.eventType            = eventType != null ? eventType : "GENERAL";
        e.venueName            = venueName;
        e.venueAddress         = venueAddress;
        e.venueCapacity        = venueCapacity;
        e.startDatetime        = startDatetime;
        e.endDatetime          = endDatetime;
        e.timezone             = "Africa/Johannesburg";
        e.isFree               = isFree;
        e.isPrivate            = isPrivate;
        e.registrationDeadline = registrationDeadline;
        e.notes                = notes;
        e.createdBy            = createdBy;
        e.status               = "DRAFT";
        e.createdAt            = Instant.now();
        e.updatedAt            = Instant.now();
        return e;
    }

    // ── Status transitions ────────────────────────────────────────────────────

    public void publish() {
        if (!"DRAFT".equals(status))
            throw new IllegalStateException("Only DRAFT events can be published");
        this.status    = "PUBLISHED";
        this.updatedAt = Instant.now();
    }

    public void markSoldOut() {
        if (!"PUBLISHED".equals(status))
            throw new IllegalStateException("Only PUBLISHED events can be marked sold out");
        this.status    = "SOLD_OUT";
        this.updatedAt = Instant.now();
    }

    /**
     * Reverses markSoldOut() when a cancellation frees a seat. No-op for any
     * other status — a LIVE/COMPLETED/CANCELLED event must never be pulled
     * back to PUBLISHED by a guest cancellation.
     */
    public void reopenSales() {
        if ("SOLD_OUT".equals(status)) {
            this.status    = "PUBLISHED";
            this.updatedAt = Instant.now();
        }
    }

    public void goLive() {
        if (!List.of("PUBLISHED", "SOLD_OUT").contains(status))
            throw new IllegalStateException("Event must be PUBLISHED or SOLD_OUT to go live");
        this.status    = "LIVE";
        this.updatedAt = Instant.now();
    }

    public void complete() {
        if (!"LIVE".equals(status))
            throw new IllegalStateException("Only LIVE events can be completed");
        this.status    = "COMPLETED";
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        if (List.of("COMPLETED", "CANCELLED").contains(status))
            throw new IllegalStateException("Cannot cancel a " + status + " event");
        this.status    = "CANCELLED";
        this.updatedAt = Instant.now();
    }

    public void linkSurvey(UUID surveyId) {
        this.surveyId  = surveyId;
        this.updatedAt = Instant.now();
    }

    public void softDelete() {
        if (!"DRAFT".equals(status))
            throw new IllegalStateException("Only DRAFT events can be deleted");
        this.deletedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // ── Registration gates ────────────────────────────────────────────────────

    /** Registration is only meaningful once published, and until the event ends or is cancelled. */
    public boolean acceptsRegistrations() {
        return List.of("PUBLISHED", "SOLD_OUT", "LIVE").contains(status);
    }

    /** {@code now} must be in the event's own timezone (Africa/Johannesburg), same as the stored LocalDateTime. */
    public boolean isRegistrationClosed(LocalDateTime now) {
        return registrationDeadline != null && now.isAfter(registrationDeadline);
    }

    // ── Editing & rescheduling (Chunk 2) ──────────────────────────────────────

    /**
     * Full-replace edit. Returns a human-readable list of what actually changed
     * (empty if nothing did) so the caller can write it to the audit trail.
     * Business rules that need other data (guest counts, LIVE-event locks)
     * are enforced by EventsService before this is called; this method owns
     * the rules that depend only on the event itself.
     */
    public List<String> applyUpdate(String title, String description, String eventType,
                                    String venueName, String venueAddress, Integer venueCapacity,
                                    LocalDateTime startDatetime, LocalDateTime endDatetime,
                                    boolean isFree, boolean isPrivate,
                                    LocalDateTime registrationDeadline, String notes) {
        if (List.of("COMPLETED", "CANCELLED").contains(status))
            throw new IllegalStateException("Cannot edit a " + status + " event");
        validateDates(startDatetime, endDatetime, registrationDeadline);

        List<String> changes = new ArrayList<>();
        this.title                = track(changes, "title", this.title, title);
        this.description          = trackText(changes, "description", this.description, description);
        this.eventType            = track(changes, "type", this.eventType, eventType != null ? eventType : this.eventType);
        this.venueName            = track(changes, "venue", this.venueName, venueName);
        this.venueAddress         = track(changes, "venue address", this.venueAddress, venueAddress);
        this.venueCapacity        = track(changes, "venue capacity", this.venueCapacity, venueCapacity);
        this.startDatetime        = track(changes, "start", this.startDatetime, startDatetime);
        this.endDatetime          = track(changes, "end", this.endDatetime, endDatetime);
        this.isFree               = track(changes, "free event", this.isFree, isFree);
        this.isPrivate            = track(changes, "private event", this.isPrivate, isPrivate);
        this.registrationDeadline = track(changes, "registration deadline", this.registrationDeadline, registrationDeadline);
        this.notes                = trackText(changes, "notes", this.notes, notes);

        if (!changes.isEmpty()) this.updatedAt = Instant.now();
        return changes;
    }

    /**
     * Moves the event to new dates. A registration deadline, if set, is shifted
     * by the same amount so it keeps its position relative to the start.
     * Only DRAFT / PUBLISHED / SOLD_OUT events can be rescheduled — a LIVE,
     * COMPLETED or CANCELLED event has no meaningful "new date".
     */
    public List<String> reschedule(LocalDateTime newStart, LocalDateTime newEnd) {
        if (!List.of("DRAFT", "PUBLISHED", "SOLD_OUT").contains(status))
            throw new IllegalStateException("Only DRAFT, PUBLISHED or SOLD_OUT events can be rescheduled (status is " + status + ")");

        LocalDateTime newDeadline = registrationDeadline;
        if (registrationDeadline != null) {
            newDeadline = registrationDeadline.plus(Duration.between(startDatetime, newStart));
        }
        validateDates(newStart, newEnd, newDeadline);

        List<String> changes = new ArrayList<>();
        this.startDatetime        = track(changes, "start", this.startDatetime, newStart);
        this.endDatetime          = track(changes, "end", this.endDatetime, newEnd);
        this.registrationDeadline = track(changes, "registration deadline", this.registrationDeadline, newDeadline);

        if (!changes.isEmpty()) this.updatedAt = Instant.now();
        return changes;
    }

    private static void validateDates(LocalDateTime start, LocalDateTime end, LocalDateTime deadline) {
        if (start == null || end == null)
            throw new IllegalArgumentException("Start and end date/time are required");
        if (!end.isAfter(start))
            throw new IllegalArgumentException("Event end must be after its start");
        if (deadline != null && deadline.isAfter(start))
            throw new IllegalArgumentException("Registration deadline must be on or before the event start");
    }

    private static <T> T track(List<String> changes, String field, T oldValue, T newValue) {
        if (!Objects.equals(oldValue, newValue)) {
            changes.add(field + ": " + (oldValue == null ? "(none)" : oldValue)
                    + " -> " + (newValue == null ? "(none)" : newValue));
        }
        return newValue;
    }

    /** For long free text — records THAT it changed, not the full old/new content. */
    private static String trackText(List<String> changes, String field, String oldValue, String newValue) {
        if (!Objects.equals(oldValue, newValue)) changes.add(field + " updated");
        return newValue;
    }
}