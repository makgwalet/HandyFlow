package za.co.handyflow.platform.events.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDateTime;
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

    public void publish() {
        if (!"DRAFT".equals(status))
            throw new IllegalStateException("Only DRAFT events can be published");
        this.status    = "PUBLISHED";
        this.updatedAt = Instant.now();
    }

    public void markSoldOut() {
        this.status    = "SOLD_OUT";
        this.updatedAt = Instant.now();
    }

    public void goLive() {
        if (!java.util.List.of("PUBLISHED","SOLD_OUT").contains(status))
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
        if (java.util.List.of("COMPLETED","CANCELLED").contains(status))
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
}