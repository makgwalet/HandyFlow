package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.ProgrammeHoursRules;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Optional workplace hours for a programme version, with the source note they come from. Withdrawn with a reason, never deleted. */
@Entity(name = "TrainProvProgrammeWorkplaceHours")
@Table(name = "trainprov_programme_workplace_hours")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvProgrammeWorkplaceHours {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "programme_version_id", nullable = false, updatable = false)
    private UUID programmeVersionId;
    @Column(nullable = false)
    private BigDecimal hours;
    private String covers;
    @Column(name = "source_type", nullable = false)
    private String sourceType;
    @Column(name = "source_reference", nullable = false)
    private String sourceReference;
    @Column(name = "source_version_date")
    private LocalDate sourceVersionDate;
    @Column(name = "source_section", nullable = false)
    private String sourceSection;
    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;
    @Column(name = "verified_by", nullable = false)
    private String verifiedBy;
    @Column(name = "verified_on", nullable = false)
    private LocalDate verifiedOn;
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
    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static TrainProvProgrammeWorkplaceHours create(TenantId t, UUID programmeVersionId, ProgrammeHoursRules.Entry e, String covers, UUID by) {
        TrainProvProgrammeWorkplaceHours h = new TrainProvProgrammeWorkplaceHours();
        h.tenantId = t.getValue();
        h.programmeVersionId = programmeVersionId;
        h.createdBy = by;
        h.createdAt = Instant.now();
        h.apply(e, covers, by);
        return h;
    }

    public void apply(ProgrammeHoursRules.Entry e, String covers, UUID by) {
        this.hours = e.hours();
        this.covers = covers == null || covers.isBlank() ? null : covers.trim();
        this.sourceType = e.sourceType();
        this.sourceReference = e.sourceReference().trim();
        this.sourceVersionDate = e.sourceVersionDate();
        this.sourceSection = e.sourceSection().trim();
        this.effectiveDate = e.effectiveDate();
        this.verifiedBy = e.verifiedBy().trim();
        this.verifiedOn = e.verifiedOn();
        this.updatedBy = by;
        this.updatedAt = Instant.now();
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public void withdraw(String reason, UUID by) {
        String problem = ProgrammeHoursRules.withdrawProblem(status, reason);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        this.status = "WITHDRAWN";
        this.withdrawnReason = reason.trim();
        this.withdrawnBy = by;
        this.withdrawnAt = Instant.now();
        this.updatedBy = by;
        this.updatedAt = this.withdrawnAt;
    }
}
