package za.co.handyflow.platform.training.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One employee's enrollment in one {@link TrainingSession}.
 * <p>
 * {@code employeeId} references {@code hr.HrEmployee} BY ID ONLY — this
 * entity never imports an HR domain class or JPA-joins to it. The
 * employee's name and number are captured here as a snapshot at
 * enrollment time (resolved once via {@code HrFacade.findEmployeeById}),
 * the same "denormalized snapshot, not a live join" pattern
 * {@code DebtCollectionCase} uses for its debtor's name/email/phone —
 * this record should still read sensibly even if the employee is later
 * renamed or terminated in HR.
 * <p>
 * Deliberately NOT soft-deletable: a training/enrollment record is
 * exactly the kind of history a POPIA/BCEA/skills-audit request or a
 * BBBEE skills-development scorecard needs to be able to produce years
 * later. {@link #cancel()} is how an enrollment is retired without
 * erasing that it existed — the same append-only reasoning
 * {@code CollectionContactLog} already established for this codebase.
 */
@Entity
@Table(name = "training_enrollments")
@Getter
@NoArgsConstructor
public class TrainingEnrollment {

    @Id UUID id;
    @Column(name = "tenant_id") UUID tenantId;

    @Column(name = "session_id") UUID sessionId;

    @Column(name = "employee_id") UUID employeeId;
    @Column(name = "employee_name_snapshot") String employeeNameSnapshot;
    @Column(name = "employee_number_snapshot") String employeeNumberSnapshot;

    /** ENROLLED | ATTENDED | NO_SHOW | CANCELLED | COMPLETED | FAILED */
    String status;

    @Column(name = "enrolled_at") Instant enrolledAt;
    @Column(name = "completed_at") Instant completedAt;

    BigDecimal score;
    Boolean passed;

    String notes;
    @Column(name = "cancel_reason") String cancelReason;

    @Column(name = "created_at") Instant createdAt;
    @Column(name = "updated_at") Instant updatedAt;
    @Version long version;

    public static TrainingEnrollment create(TenantId tenantId, UUID sessionId, UUID employeeId,
                                             String employeeNameSnapshot, String employeeNumberSnapshot,
                                             String notes) {
        TrainingEnrollment e = new TrainingEnrollment();
        e.id = UUID.randomUUID();
        e.tenantId = tenantId.getValue();
        e.sessionId = sessionId;
        e.employeeId = employeeId;
        e.employeeNameSnapshot = employeeNameSnapshot;
        e.employeeNumberSnapshot = employeeNumberSnapshot;
        e.notes = notes;
        e.status = "ENROLLED";
        e.enrolledAt = Instant.now();
        e.createdAt = Instant.now();
        e.updatedAt = Instant.now();
        return e;
    }

    public void markAttended() {
        requireStatus("ENROLLED");
        this.status = "ATTENDED";
        this.updatedAt = Instant.now();
    }

    public void markNoShow() {
        requireStatus("ENROLLED");
        this.status = "NO_SHOW";
        this.updatedAt = Instant.now();
    }

    public void cancel(String reason) {
        if (isTerminal()) throw new IllegalStateException("Enrollment is already " + this.status);
        this.status = "CANCELLED";
        this.cancelReason = reason;
        this.updatedAt = Instant.now();
    }

    /**
     * Records the outcome and moves the enrollment to its final state.
     * Deliberately callable from ENROLLED as well as ATTENDED — this
     * first pass doesn't hard-require a separate "mark attended" step
     * before completion can be recorded (e.g. an online/self-paced
     * course with no attendance register). Flagged as a simplification,
     * not a business rule confirmation, in the status doc.
     */
    public void complete(BigDecimal score, boolean passed) {
        if (isTerminal()) throw new IllegalStateException("Enrollment is already " + this.status);
        this.score = score;
        this.passed = passed;
        this.status = passed ? "COMPLETED" : "FAILED";
        this.completedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean isTerminal() {
        return "CANCELLED".equals(this.status) || "COMPLETED".equals(this.status) || "FAILED".equals(this.status);
    }

    /** True only for a COMPLETED enrollment on a course that offers certification — the gate TrainingCertificateService checks before issuing. */
    public boolean isEligibleForCertificate() {
        return "COMPLETED".equals(this.status);
    }

    private void requireStatus(String required) {
        if (!required.equals(this.status)) {
            throw new IllegalStateException("Expected status " + required + " but was " + this.status);
        }
    }
}
