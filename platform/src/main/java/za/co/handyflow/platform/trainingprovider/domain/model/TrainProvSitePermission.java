package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.SitePermissionRules;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** The provider's record that a practitioner may work in a role at a delivery site. Withdrawn with a reason, never deleted. */
@Entity(name = "TrainProvSitePermission")
@Table(name = "trainprov_site_permissions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvSitePermission {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "practitioner_id", nullable = false, updatable = false)
    private UUID practitionerId;
    @Column(name = "site_id", nullable = false, updatable = false)
    private UUID siteId;
    @Column(nullable = false, updatable = false)
    private String role;
    @Column(name = "programme_id", updatable = false)
    private UUID programmeId;
    @Column(name = "valid_from", nullable = false, updatable = false)
    private LocalDate validFrom;
    @Column(name = "valid_to", updatable = false)
    private LocalDate validTo;
    @Column(nullable = false, updatable = false)
    private String basis;
    @Column(nullable = false)
    private String status = "ACTIVE";
    @Column(name = "withdrawn_reason")
    private String withdrawnReason;
    @Column(name = "withdrawn_by")
    private UUID withdrawnBy;
    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Version
    private Long version;

    public static TrainProvSitePermission create(TenantId t, UUID practitionerId, UUID siteId, String role, UUID programmeId, LocalDate from, LocalDate to, String basis, UUID by) {
        TrainProvSitePermission p = new TrainProvSitePermission();
        p.tenantId = t.getValue();
        p.practitionerId = practitionerId;
        p.siteId = siteId;
        p.role = role;
        p.programmeId = programmeId;
        p.validFrom = from;
        p.validTo = to;
        p.basis = basis.trim();
        p.createdBy = by;
        p.createdAt = Instant.now();
        return p;
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public SitePermissionRules.Permission asRule() {
        return new SitePermissionRules.Permission(programmeId, validFrom, validTo, isActive());
    }

    public void withdraw(String reason, UUID by) {
        String problem = SitePermissionRules.withdrawProblem(status, reason);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        this.status = "WITHDRAWN";
        this.withdrawnReason = reason.trim();
        this.withdrawnBy = by;
        this.withdrawnAt = Instant.now();
    }
}
