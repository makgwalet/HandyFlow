package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.FundingRules;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A link from an enrolment to a funder paying for it. Records who is paying and a reference, never an amount. Ended with a reason, never deleted. */
@Entity(name = "TrainProvEnrolmentFunding")
@Table(name = "trainprov_enrolment_funding")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvEnrolmentFunding {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "enrolment_id", nullable = false, updatable = false)
    private UUID enrolmentId;
    @Column(name = "funder_id", nullable = false, updatable = false)
    private UUID funderId;
    @Column(name = "funded_from", nullable = false)
    private LocalDate fundedFrom;
    @Column(name = "agreement_ref")
    private String agreementRef;
    @Column(name = "funder_reference")
    private String funderReference;
    private String note;
    @Column(nullable = false)
    private String status = FundingRules.ACTIVE;
    @Column(name = "ended_on")
    private LocalDate endedOn;
    @Column(name = "end_reason")
    private String endReason;
    @Column(name = "ended_by")
    private UUID endedBy;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static TrainProvEnrolmentFunding link(TenantId t, UUID enrolmentId, UUID funderId, LocalDate from, String agreementRef, String funderReference, String note, UUID by) {
        TrainProvEnrolmentFunding l = new TrainProvEnrolmentFunding();
        l.tenantId = t.getValue();
        l.enrolmentId = enrolmentId;
        l.funderId = funderId;
        l.fundedFrom = from;
        l.agreementRef = blank(agreementRef);
        l.funderReference = blank(funderReference);
        l.note = blank(note);
        l.createdBy = by;
        l.createdAt = Instant.now();
        l.updatedAt = l.createdAt;
        return l;
    }

    public void end(LocalDate on, String reason, LocalDate today, UUID by) {
        String problem = FundingRules.endProblem(status, on, fundedFrom, today, reason);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        this.status = FundingRules.ENDED;
        this.endedOn = on;
        this.endReason = reason.trim();
        this.endedBy = by;
        this.updatedAt = Instant.now();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
