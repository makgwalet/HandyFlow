package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.CohortRules;
import za.co.handyflow.platform.trainingprovider.domain.rules.TimetableRules;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** A dated block of teaching for a cohort. Cancelled with a reason, never deleted. */
@Entity(name = "TrainProvTimetableSlot")
@Table(name = "trainprov_timetable_slots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvTimetableSlot {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "cohort_id", nullable = false)
    private UUID cohortId;
    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
    @Column(name = "time_zone", nullable = false)
    private String timeZone = TimetableRules.DEFAULT_ZONE;
    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;
    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;
    @Column(nullable = false)
    private String title;
    @Column(name = "module_id")
    private UUID moduleId;
    @Column(name = "venue_id")
    private UUID venueId;
    @Column(name = "practitioner_id")
    private UUID practitionerId;
    @Column(nullable = false)
    private String status = "SCHEDULED";
    @Column(name = "cancelled_reason")
    private String cancelledReason;
    @Column(name = "cancelled_by")
    private UUID cancelledBy;
    @Column(name = "cancelled_at")
    private Instant cancelledAt;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static TrainProvTimetableSlot create(TenantId t, UUID cohortId, String title, LocalDate date, LocalTime start, LocalTime end,
                                                UUID moduleId, UUID venueId, UUID practitionerId, UUID by) {
        return create(t, cohortId, title, date, start, end, moduleId, venueId, practitionerId, null, by);
    }

    /** timeZone is an IANA zone id such as Africa/Johannesburg; blank means the default. */
    public static TrainProvTimetableSlot create(TenantId t, UUID cohortId, String title, LocalDate date, LocalTime start, LocalTime end,
                                                UUID moduleId, UUID venueId, UUID practitionerId, String timeZone, UUID by) {
        TrainProvTimetableSlot s = new TrainProvTimetableSlot();
        s.tenantId = t.getValue();
        s.cohortId = cohortId;
        s.createdBy = by;
        s.createdAt = Instant.now();
        s.set(title, date, start, end, moduleId, venueId, practitionerId, timeZone);
        return s;
    }

    public void change(String title, LocalDate date, LocalTime start, LocalTime end, UUID moduleId, UUID venueId, UUID practitionerId) {
        change(title, date, start, end, moduleId, venueId, practitionerId, null);
    }

    /** A blank timeZone keeps the zone the slot already has. */
    public void change(String title, LocalDate date, LocalTime start, LocalTime end, UUID moduleId, UUID venueId, UUID practitionerId, String timeZone) {
        if (!"SCHEDULED".equals(status)) {
            throw new IllegalStateException("A cancelled slot cannot be changed");
        }
        set(title, date, start, end, moduleId, venueId, practitionerId, timeZone);
    }

    private void set(String title, LocalDate date, LocalTime start, LocalTime end, UUID moduleId, UUID venueId, UUID practitionerId, String zone) {
        if (title == null || title.trim().length() < 2 || date == null || start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("Give the slot a title, a date and an end time after the start time");
        }
        String zoneId = zone == null || zone.isBlank() ? this.timeZone : zone.trim();
        String zoneProblem = TimetableRules.zoneProblem(zoneId);
        if (zoneProblem != null) {
            throw new IllegalArgumentException(zoneProblem);
        }
        this.timeZone = zoneId;
        this.startsAt = TimetableRules.instant(date, start, zoneId);
        this.endsAt = TimetableRules.instant(date, end, zoneId);
        this.title = title.trim();
        this.slotDate = date;
        this.startTime = start;
        this.endTime = end;
        this.moduleId = moduleId;
        this.venueId = venueId;
        this.practitionerId = practitionerId;
        this.updatedAt = Instant.now();
    }

    public void cancel(String reason, UUID by) {
        String problem = CohortRules.reasonProblem(reason);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        if (!"SCHEDULED".equals(status)) {
            throw new IllegalStateException("This slot is already cancelled");
        }
        this.status = "CANCELLED";
        this.cancelledReason = reason.trim();
        this.cancelledBy = by;
        this.cancelledAt = Instant.now();
        this.updatedAt = this.cancelledAt;
    }
}
