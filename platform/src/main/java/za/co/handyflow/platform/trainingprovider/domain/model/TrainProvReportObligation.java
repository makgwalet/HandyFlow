package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.ReportRules;

import java.time.Instant;
import java.util.UUID;

/** A report the provider owes an authority, set up by the provider with a written source note. Nothing is built in. Retired with a reason, never deleted. */
@Entity(name = "TrainProvReportObligation")
@Table(name = "trainprov_report_obligations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvReportObligation {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "obligation_no", nullable = false)
    private String obligationNo;
    @Column(name = "authority_id", nullable = false, updatable = false)
    private UUID authorityId;
    @Column(name = "programme_id", updatable = false)
    private UUID programmeId;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String cadence;
    @Column(name = "due_days_after_end", nullable = false)
    private int dueDaysAfterEnd;
    @Column(name = "source_note", nullable = false)
    private String sourceNote;
    @Column(nullable = false)
    private String status = "ACTIVE";
    @Column(name = "retired_reason")
    private String retiredReason;
    @Column(name = "retired_by")
    private UUID retiredBy;
    @Column(name = "retired_at")
    private Instant retiredAt;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static TrainProvReportObligation create(TenantId t, String no, UUID authorityId, UUID programmeId, String name, String cadence, Integer dueDays, String sourceNote, UUID by) {
        TrainProvReportObligation o = new TrainProvReportObligation();
        o.tenantId = t.getValue();
        o.obligationNo = no;
        o.authorityId = authorityId;
        o.programmeId = programmeId;
        o.createdBy = by;
        o.createdAt = Instant.now();
        o.apply(name, cadence, dueDays, sourceNote);
        return o;
    }

    public void change(String name, String cadence, Integer dueDays, String sourceNote) {
        if (!isActive()) {
            throw new IllegalStateException("A retired obligation cannot be changed");
        }
        apply(name, cadence, dueDays, sourceNote);
    }

    private void apply(String name, String cadence, Integer dueDays, String sourceNote) {
        String problem = ReportRules.obligationProblem(name, cadence, dueDays, sourceNote);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        this.name = name.trim();
        this.cadence = cadence;
        this.dueDaysAfterEnd = dueDays;
        this.sourceNote = sourceNote.trim();
        this.updatedAt = Instant.now();
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public void retire(String reason, UUID by) {
        if (!isActive()) {
            throw new IllegalStateException("This obligation is already retired");
        }
        String problem = za.co.handyflow.platform.trainingprovider.domain.rules.CohortRules.reasonProblem(reason);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        this.status = "RETIRED";
        this.retiredReason = reason.trim();
        this.retiredBy = by;
        this.retiredAt = Instant.now();
        this.updatedAt = this.retiredAt;
    }
}
