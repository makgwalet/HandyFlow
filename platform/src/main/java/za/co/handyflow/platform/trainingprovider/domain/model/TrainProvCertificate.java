package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A certificate issued for a completed, certification-eligible {@link
 * TrainProvEnrollment} — carries the course's accreditation reference
 * (unit standard, NQF level) as a snapshot, since a real accredited
 * certificate must still be verifiable even after the course catalogue
 * entry is later edited or archived.
 */
@Entity
@Table(name = "trainprov_certificates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvCertificate {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "enrollment_id", nullable = false)
    private UUID enrollmentId;

    @Column(name = "delegate_id", nullable = false)
    private UUID delegateId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "delegate_name_snapshot")
    private String delegateNameSnapshot;

    @Column(name = "client_name_snapshot")
    private String clientNameSnapshot;

    @Column(name = "course_title_snapshot")
    private String courseTitleSnapshot;

    @Column(name = "unit_standard_snapshot")
    private String unitStandardSnapshot;

    @Column(name = "certificate_number", nullable = false)
    private String certificateNumber;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /** VALID | EXPIRED | REVOKED */
    private String status;

    @Column(name = "revoked_reason")
    private String revokedReason;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static TrainProvCertificate create(TenantId tenantId, UUID enrollmentId, UUID delegateId, UUID clientId,
                                               String delegateNameSnapshot, String clientNameSnapshot,
                                               String courseTitleSnapshot, String unitStandardSnapshot,
                                               String certificateNumber, LocalDate issueDate, LocalDate expiryDate) {
        TrainProvCertificate c = new TrainProvCertificate();
        c.tenantId = tenantId.getValue();
        c.enrollmentId = enrollmentId;
        c.delegateId = delegateId;
        c.clientId = clientId;
        c.delegateNameSnapshot = delegateNameSnapshot;
        c.clientNameSnapshot = clientNameSnapshot;
        c.courseTitleSnapshot = courseTitleSnapshot;
        c.unitStandardSnapshot = unitStandardSnapshot;
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
