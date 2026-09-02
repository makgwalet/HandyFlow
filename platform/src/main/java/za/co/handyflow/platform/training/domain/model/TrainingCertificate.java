package za.co.handyflow.platform.training.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A certificate issued for a completed, certification-eligible {@link
 * TrainingEnrollment}. One certificate per enrollment — enforced by a
 * unique constraint on {@code enrollment_id} at the DB level (see the
 * migration), not just in the service layer.
 * <p>
 * {@code courseTitleSnapshot} is captured at issue time so a later edit
 * or archival of the course doesn't change what a previously-issued
 * certificate says it was for — same reasoning as the enrollment's own
 * employee-name snapshot.
 */
@Entity
@Table(name = "training_certificates")
@Getter
@NoArgsConstructor
public class TrainingCertificate {

    @Id UUID id;
    @Column(name = "tenant_id") UUID tenantId;

    @Column(name = "enrollment_id") UUID enrollmentId;
    @Column(name = "employee_id") UUID employeeId;
    @Column(name = "employee_name_snapshot") String employeeNameSnapshot;
    @Column(name = "course_title_snapshot") String courseTitleSnapshot;

    @Column(name = "certificate_number") String certificateNumber;

    @Column(name = "issue_date") LocalDate issueDate;
    @Column(name = "expiry_date") LocalDate expiryDate;

    /** VALID | EXPIRED | REVOKED */
    String status;

    @Column(name = "revoked_reason") String revokedReason;
    @Column(name = "revoked_at") Instant revokedAt;

    @Column(name = "created_at") Instant createdAt;
    @Column(name = "updated_at") Instant updatedAt;
    @Version long version;

    public static TrainingCertificate create(TenantId tenantId, UUID enrollmentId, UUID employeeId,
                                              String employeeNameSnapshot, String courseTitleSnapshot,
                                              String certificateNumber, LocalDate issueDate, LocalDate expiryDate) {
        TrainingCertificate c = new TrainingCertificate();
        c.id = UUID.randomUUID();
        c.tenantId = tenantId.getValue();
        c.enrollmentId = enrollmentId;
        c.employeeId = employeeId;
        c.employeeNameSnapshot = employeeNameSnapshot;
        c.courseTitleSnapshot = courseTitleSnapshot;
        c.certificateNumber = certificateNumber;
        c.issueDate = issueDate;
        c.expiryDate = expiryDate;
        c.status = "VALID";
        c.createdAt = Instant.now();
        c.updatedAt = Instant.now();
        return c;
    }

    public void revoke(String reason) {
        if ("REVOKED".equals(this.status)) throw new IllegalStateException("Certificate is already revoked");
        this.status = "REVOKED";
        this.revokedReason = reason;
        this.revokedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /** Called by the daily notification sweep — never fires on a REVOKED certificate, and is a no-op once already EXPIRED. */
    public void markExpired() {
        if ("REVOKED".equals(this.status) || "EXPIRED".equals(this.status)) return;
        this.status = "EXPIRED";
        this.updatedAt = Instant.now();
    }

    public boolean isExpiringWithin(int days) {
        return this.expiryDate != null
                && "VALID".equals(this.status)
                && !this.expiryDate.isAfter(LocalDate.now().plusDays(days));
    }

    public boolean isExpired() {
        return this.expiryDate != null && this.expiryDate.isBefore(LocalDate.now());
    }
}
