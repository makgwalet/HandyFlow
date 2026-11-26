package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.LearnershipRules;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A record of the paper learnership agreement between learner, employer and provider. Ended or cancelled with a reason, never deleted. */
@Entity(name = "TrainProvLearnershipAgreement")
@Table(name = "trainprov_learnership_agreements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvLearnershipAgreement {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "agreement_no", nullable = false)
    private String agreementNo;
    @Column(name = "enrolment_id", nullable = false, updatable = false)
    private UUID enrolmentId;
    @Column(name = "client_id")
    private UUID clientId;
    @Column(name = "employer_name", nullable = false)
    private String employerName;
    @Column(name = "employer_contact")
    private String employerContact;
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    @Column(name = "end_date")
    private LocalDate endDate;
    @Column(nullable = false)
    private String status = LearnershipRules.DRAFT;
    @Column(name = "learner_signed_on")
    private LocalDate learnerSignedOn;
    @Column(name = "employer_signed_on")
    private LocalDate employerSignedOn;
    @Column(name = "provider_signed_on")
    private LocalDate providerSignedOn;
    @Column(name = "signing_recorded_by")
    private UUID signingRecordedBy;
    @Column(name = "registration_ref")
    private String registrationRef;
    @Column(name = "registration_recorded_on")
    private LocalDate registrationRecordedOn;
    @Column(name = "registration_recorded_by")
    private UUID registrationRecordedBy;
    @Column(name = "ended_on")
    private LocalDate endedOn;
    @Column(name = "end_reason")
    private String endReason;
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

    public static TrainProvLearnershipAgreement create(TenantId t, String no, UUID enrolmentId, UUID clientId, String employer, String contact, LocalDate start, LocalDate end, UUID by) {
        TrainProvLearnershipAgreement a = new TrainProvLearnershipAgreement();
        a.tenantId = t.getValue();
        a.agreementNo = no;
        a.enrolmentId = enrolmentId;
        a.createdBy = by;
        a.createdAt = Instant.now();
        a.apply(clientId, employer, contact, start, end);
        return a;
    }

    public void change(UUID clientId, String employer, String contact, LocalDate start, LocalDate end) {
        String problem = LearnershipRules.editProblem(status);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        apply(clientId, employer, contact, start, end);
    }

    private void apply(UUID clientId, String employer, String contact, LocalDate start, LocalDate end) {
        if (employer == null || employer.trim().length() < 2 || start == null || (end != null && end.isBefore(start))) {
            throw new IllegalArgumentException("Check the employer and the start and end days");
        }
        this.clientId = clientId;
        this.employerName = employer.trim();
        this.employerContact = contact == null || contact.isBlank() ? null : contact.trim();
        this.startDate = start;
        this.endDate = end;
        this.updatedAt = Instant.now();
    }

    public void sign(LocalDate learner, LocalDate employer, LocalDate provider, LocalDate today, UUID by) {
        String problem = LearnershipRules.signProblem(status, learner, employer, provider, today);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        this.learnerSignedOn = learner;
        this.employerSignedOn = employer;
        this.providerSignedOn = provider;
        this.signingRecordedBy = by;
        this.status = LearnershipRules.SIGNED;
        this.updatedAt = Instant.now();
    }

    public void recordRegistration(String reference, LocalDate on, LocalDate today, UUID by) {
        String problem = LearnershipRules.registrationProblem(status, registrationRef != null, reference, on, today);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        this.registrationRef = reference.trim();
        this.registrationRecordedOn = on;
        this.registrationRecordedBy = by;
        this.updatedAt = Instant.now();
    }

    public void end(LocalDate on, String reason, LocalDate today) {
        String problem = LearnershipRules.endProblem(status, on, LearnershipRules.latest(learnerSignedOn, employerSignedOn, providerSignedOn), today, reason);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        this.status = LearnershipRules.ENDED;
        this.endedOn = on;
        this.endReason = reason.trim();
        this.updatedAt = Instant.now();
    }

    public void cancel(String reason) {
        String problem = LearnershipRules.cancelProblem(status, registrationRef != null, reason);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        this.status = LearnershipRules.CANCELLED;
        this.cancelReason = reason.trim();
        this.updatedAt = Instant.now();
    }
}
