package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.WorkplaceRules;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A learner's placement with a host workplace and mentor. Cancelled with a reason, never deleted. */
@Entity(name = "TrainProvWorkplacePlacement")
@Table(name = "trainprov_workplace_placements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvWorkplacePlacement {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "placement_no", nullable = false)
    private String placementNo;
    @Column(name = "enrolment_id", nullable = false, updatable = false)
    private UUID enrolmentId;
    @Column(name = "host_name", nullable = false)
    private String hostName;
    @Column(name = "host_contact")
    private String hostContact;
    @Column(name = "mentor_name", nullable = false)
    private String mentorName;
    @Column(name = "mentor_contact")
    private String mentorContact;
    @Column(name = "agreement_ref")
    private String agreementRef;
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    @Column(name = "end_date")
    private LocalDate endDate;
    @Column(nullable = false)
    private String status = WorkplaceRules.PLANNED;
    @Column(name = "started_on")
    private LocalDate startedOn;
    @Column(name = "ended_on")
    private LocalDate endedOn;
    @Column(name = "cancel_reason")
    private String cancelReason;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static TrainProvWorkplacePlacement create(TenantId t, String no, UUID enrolmentId, String host, String hostContact, String mentor, String mentorContact,
                                                     String agreementRef, LocalDate start, LocalDate end, UUID by) {
        TrainProvWorkplacePlacement p = new TrainProvWorkplacePlacement();
        p.tenantId = t.getValue();
        p.placementNo = no;
        p.enrolmentId = enrolmentId;
        p.createdBy = by;
        p.createdAt = Instant.now();
        p.apply(host, hostContact, mentor, mentorContact, agreementRef, start, end);
        return p;
    }

    public void change(String host, String hostContact, String mentor, String mentorContact, String agreementRef, LocalDate start, LocalDate end) {
        String problem = WorkplaceRules.editProblem(status);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        apply(host, hostContact, mentor, mentorContact, agreementRef, start, end);
    }

    private void apply(String host, String hostContact, String mentor, String mentorContact, String agreementRef, LocalDate start, LocalDate end) {
        if (host == null || host.trim().length() < 2 || mentor == null || mentor.trim().length() < 2) {
            throw new IllegalArgumentException("Give the host workplace and the mentor");
        }
        if (start == null || (end != null && end.isBefore(start))) {
            throw new IllegalArgumentException("Check the start and end days");
        }
        this.hostName = host.trim();
        this.hostContact = blank(hostContact);
        this.mentorName = mentor.trim();
        this.mentorContact = blank(mentorContact);
        this.agreementRef = blank(agreementRef);
        this.startDate = start;
        this.endDate = end;
        this.updatedAt = Instant.now();
    }

    public void start(String enrolmentStatus, LocalDate on, LocalDate today) {
        String problem = WorkplaceRules.startProblem(status, enrolmentStatus, on, startDate, today);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        this.status = WorkplaceRules.ACTIVE;
        this.startedOn = on;
        this.updatedAt = Instant.now();
    }

    public void complete(LocalDate on, LocalDate today) {
        String problem = WorkplaceRules.completeProblem(status, on, startedOn, today);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        this.status = WorkplaceRules.COMPLETED;
        this.endedOn = on;
        this.updatedAt = Instant.now();
    }

    public void cancel(String reason) {
        String problem = WorkplaceRules.cancelProblem(status, reason);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        this.status = WorkplaceRules.CANCELLED;
        this.cancelReason = reason.trim();
        this.updatedAt = Instant.now();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
