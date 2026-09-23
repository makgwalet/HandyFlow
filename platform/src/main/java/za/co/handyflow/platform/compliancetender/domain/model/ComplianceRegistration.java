package za.co.handyflow.platform.compliancetender.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row per (tenant, authority, registration_type) — the tenant's own
 * CIPC company registration, SARS income tax registration, PSIRA business
 * registration, CSD supplier registration, cidb grading, NHBRC
 * registration, and so on. This is the tenant's OWN business registration
 * status, not a client's (that's complianceservices, a later module built
 * on top of this one) and not an individual employee's (e.g. a guard's own
 * PSiRA number, which security.PsiraComplianceScheduler already tracks
 * separately — a different granularity of the same regulator, a real
 * integration point rather than a duplication).
 */
@Entity
@Table(name = "compliance_registrations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceRegistration {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false)
    private String authority; // CIPC, SARS, UIF, PSIRA, CSD, CIDB, NHBRC, OTHER

    @Column(name = "registration_type", nullable = false)
    private String registrationType; // e.g. "Income Tax", "VAT", "Business Registration", "Grade 4GB"

    @Column(name = "registration_number")
    private String registrationNumber;

    @Column(nullable = false)
    private String status = "ACTIVE"; // ACTIVE, EXPIRED, LAPSED, PENDING, NOT_APPLICABLE

    @Column(name = "issued_date")
    private LocalDate issuedDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate; // nullable — some registrations (e.g. CIPC company reg itself) don't expire

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    private Long version;

    public static ComplianceRegistration create(TenantId tenantId, String authority, String registrationType,
                                                 String registrationNumber, LocalDate issuedDate,
                                                 LocalDate expiryDate, String notes, UUID createdBy) {
        if (expiryDate != null && issuedDate != null && expiryDate.isBefore(issuedDate))
            throw new IllegalArgumentException("expiryDate cannot be before issuedDate");
        ComplianceRegistration r = new ComplianceRegistration();
        r.tenantId = tenantId;
        r.authority = authority != null ? authority.toUpperCase() : "OTHER";
        r.registrationType = registrationType;
        r.registrationNumber = registrationNumber;
        r.issuedDate = issuedDate;
        r.expiryDate = expiryDate;
        r.notes = notes;
        r.createdAt = Instant.now();
        r.createdBy = createdBy;
        r.updatedAt = Instant.now();
        r.updatedBy = createdBy;
        return r;
    }

    public void update(String registrationNumber, String status, LocalDate issuedDate,
                       LocalDate expiryDate, String notes, UUID updatedBy) {
        if (expiryDate != null && issuedDate != null && expiryDate.isBefore(issuedDate))
            throw new IllegalArgumentException("expiryDate cannot be before issuedDate");
        this.registrationNumber = registrationNumber;
        this.status = status;
        this.issuedDate = issuedDate;
        this.expiryDate = expiryDate;
        this.notes = notes;
        this.updatedAt = Instant.now();
        this.updatedBy = updatedBy;
    }

    /** Called by the daily notification sweep once an ACTIVE registration's expiry date has passed. */
    public void markExpired() {
        if ("ACTIVE".equals(status)) {
            this.status = "EXPIRED";
            this.updatedAt = Instant.now();
        }
    }

    public boolean isExpired() {
        return "EXPIRED".equals(status)
                || ("ACTIVE".equals(status) && expiryDate != null && expiryDate.isBefore(LocalDate.now()));
    }

    public boolean isExpiringWithin(int days) {
        if (!"ACTIVE".equals(status) || expiryDate == null) return false;
        LocalDate cutoff = LocalDate.now().plusDays(days);
        return !expiryDate.isBefore(LocalDate.now()) && !expiryDate.isAfter(cutoff);
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
