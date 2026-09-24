package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A compliance registration tracked on a CLIENT's behalf — the
 * per-client counterpart to {@code compliancetender.ComplianceRegistration},
 * structurally similar by design but a genuinely independent table, not
 * shared schema. Two real reasons, not one taken for convenience:
 * <p>
 * 1. {@code compliancetender} cannot depend on {@code complianceservices}
 * — that dependency already runs the other way ({@code complianceservices}
 * depends on {@code compliancetender}), and Spring Modulith forbids a
 * circular module dependency. Adding an optional client dimension to
 * {@code compliancetender}'s own table would need either that circular
 * dependency (for a typed reference) or a raw, untyped UUID column with
 * no Java-level reference at all — technically possible, but still means
 * invasively modifying an already-shipped, tested entity, its repository,
 * its service, its controller, and its frontend, all for a use case
 * (many clients per tenant) that {@code compliancetender} was never
 * designed to hold.
 * <p>
 * 2. The two use cases are genuinely different shapes: {@code
 * compliancetender}'s own registrations are the tenant's OWN business
 * compliance, one flat list. A service provider's client registrations
 * are inherently grouped by client first — every query, every permission
 * check, every notification sweep for this data needs to know which
 * client, not just which tenant. Bolting that onto a schema built for
 * the simpler case would have made the simpler case more complex too.
 */
@Entity
@Table(name = "client_compliance_registrations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClientComplianceRegistration {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(nullable = false)
    private String authority;

    @Column(name = "registration_type", nullable = false)
    private String registrationType;

    @Column(name = "registration_number")
    private String registrationNumber;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "issued_date")
    private LocalDate issuedDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

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

    public static ClientComplianceRegistration create(TenantId tenantId, UUID clientId, String authority,
                                                       String registrationType, String registrationNumber,
                                                       LocalDate issuedDate, LocalDate expiryDate, String notes,
                                                       UUID createdBy) {
        if (expiryDate != null && issuedDate != null && expiryDate.isBefore(issuedDate))
            throw new IllegalArgumentException("expiryDate cannot be before issuedDate");
        ClientComplianceRegistration r = new ClientComplianceRegistration();
        r.tenantId = tenantId;
        r.clientId = clientId;
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
