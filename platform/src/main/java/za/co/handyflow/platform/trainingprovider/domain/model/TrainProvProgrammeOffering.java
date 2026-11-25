package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A programme offered at a site. For accredited programmes the offering is only made where a current approval covers it. Withdrawn, never deleted. */
@Entity(name = "TrainProvProgrammeOffering")
@Table(name = "trainprov_programme_offerings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvProgrammeOffering {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "programme_id", nullable = false)
    private UUID programmeId;
    @Column(name = "site_id", nullable = false)
    private UUID siteId;
    @Column(nullable = false)
    private String status = "ACTIVE";
    @Column(name = "offered_from", nullable = false)
    private LocalDate offeredFrom;
    @Column(name = "coverage_note")
    private String coverageNote;
    @Column(name = "withdrawn_reason")
    private String withdrawnReason;
    @Column(name = "withdrawn_by")
    private UUID withdrawnBy;
    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static TrainProvProgrammeOffering create(TenantId t, UUID programmeId, UUID siteId, LocalDate from, String coverageNote, UUID by) {
        TrainProvProgrammeOffering o = new TrainProvProgrammeOffering();
        o.tenantId = t.getValue();
        o.programmeId = programmeId;
        o.siteId = siteId;
        o.offeredFrom = from;
        o.coverageNote = coverageNote;
        o.createdBy = by;
        o.createdAt = Instant.now();
        o.updatedAt = o.createdAt;
        return o;
    }

    public void withdraw(String reason, UUID by) {
        if (!"ACTIVE".equals(status)) {
            throw new IllegalStateException("This offering is already withdrawn");
        }
        if (reason == null || reason.trim().length() < 10) {
            throw new IllegalArgumentException("Say why, in at least 10 characters");
        }
        this.status = "WITHDRAWN";
        this.withdrawnReason = reason.trim();
        this.withdrawnBy = by;
        this.withdrawnAt = Instant.now();
        this.updatedAt = this.withdrawnAt;
    }
}
