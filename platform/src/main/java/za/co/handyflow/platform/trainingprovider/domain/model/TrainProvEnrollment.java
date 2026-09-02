package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One delegate's enrollment in one {@link TrainProvSession}.
 * {@code clientId} is captured as a snapshot at enrollment time (from
 * the delegate's own client at that moment) — this is the field
 * {@link za.co.handyflow.platform.trainingprovider.application.internal.TrainProvBillingService}
 * groups by when computing what to bill each client for a period, so
 * it must stay stable even if the delegate is later reassigned to a
 * different client record. Never soft-deletable, same append-only
 * reasoning as Module 4a's own {@code TrainingEnrollment} — this is a
 * training record a client or auditor may need to see years later.
 */
@Entity
@Table(name = "trainprov_enrollments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvEnrollment {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "delegate_id", nullable = false)
    private UUID delegateId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "delegate_name_snapshot")
    private String delegateNameSnapshot;

    /** ENROLLED | ATTENDED | NO_SHOW | CANCELLED | COMPLETED | FAILED */
    private String status;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    private BigDecimal score;
    private Boolean passed;

    private String notes;

    @Column(name = "cancel_reason")
    private String cancelReason;

    /** Set once this enrollment has been included on a TrainProvInvoice — so a period's billing run never double-bills the same delegate-session. */
    @Column(name = "invoiced")
    private boolean invoiced;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static TrainProvEnrollment create(TenantId tenantId, UUID sessionId, UUID delegateId, UUID clientId,
                                              String delegateNameSnapshot, String notes) {
        TrainProvEnrollment e = new TrainProvEnrollment();
        e.tenantId = tenantId.getValue();
        e.sessionId = sessionId;
        e.delegateId = delegateId;
        e.clientId = clientId;
        e.delegateNameSnapshot = delegateNameSnapshot;
        e.notes = notes;
        e.status = "ENROLLED";
        e.invoiced = false;
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
        if (this.invoiced) {
            throw new IllegalStateException("Cannot cancel an enrollment that has already been invoiced — issue a credit note instead");
        }
        this.status = "CANCELLED";
        this.cancelReason = reason;
        this.updatedAt = Instant.now();
    }

    public void complete(BigDecimal score, boolean passed) {
        if (isTerminal()) throw new IllegalStateException("Enrollment is already " + this.status);
        this.score = score;
        this.passed = passed;
        this.status = passed ? "COMPLETED" : "FAILED";
        this.completedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markInvoiced() {
        this.invoiced = true;
        this.updatedAt = Instant.now();
    }

    public boolean isTerminal() {
        return "CANCELLED".equals(this.status) || "COMPLETED".equals(this.status) || "FAILED".equals(this.status);
    }

    public boolean isEligibleForCertificate() {
        return "COMPLETED".equals(this.status);
    }

    /** Billable = ever actually delivered to (or reserved for) the delegate — everything except a CANCELLED enrollment, which was withdrawn before delivery. */
    public boolean isBillable() {
        return !"CANCELLED".equals(this.status) && !this.invoiced;
    }

    private void requireStatus(String required) {
        if (!required.equals(this.status)) {
            throw new IllegalStateException("Expected status " + required + " but was " + this.status);
        }
    }
}
