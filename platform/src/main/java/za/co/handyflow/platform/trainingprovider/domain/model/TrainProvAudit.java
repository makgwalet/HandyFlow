package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.AuditRules;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One audit run against a checklist version. Completed by a second person or with a written reason; cancelled with a reason, never deleted. */
@Entity(name = "TrainProvAudit")
@Table(name = "trainprov_audits")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvAudit {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "audit_no", nullable = false, updatable = false)
    private String auditNo;
    @Column(name = "template_version_id", nullable = false, updatable = false)
    private UUID templateVersionId;
    @Column(nullable = false)
    private String title;
    @Column(name = "scope_type", nullable = false, updatable = false)
    private String scopeType;
    @Column(name = "site_id", updatable = false)
    private UUID siteId;
    @Column(name = "cohort_id", updatable = false)
    private UUID cohortId;
    @Column(name = "programme_id", updatable = false)
    private UUID programmeId;
    @Column(name = "lead_user_id", nullable = false, updatable = false)
    private UUID leadUserId;
    @Column(name = "planned_on", nullable = false)
    private LocalDate plannedOn;
    @Column(nullable = false)
    private String status = AuditRules.PLANNED;
    @Column(name = "started_on")
    private LocalDate startedOn;
    @Column(name = "completed_on")
    private LocalDate completedOn;
    @Column(name = "completed_by")
    private UUID completedBy;
    @Column(name = "completed_alone_reason")
    private String completedAloneReason;
    private String summary;
    @Column(name = "cancelled_reason")
    private String cancelledReason;
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static TrainProvAudit create(TenantId t, String no, UUID templateVersionId, String title, String scope, UUID siteId, UUID cohortId, UUID programmeId,
                                        LocalDate plannedOn, UUID lead) {
        TrainProvAudit a = new TrainProvAudit();
        a.tenantId = t.getValue();
        a.auditNo = no;
        a.templateVersionId = templateVersionId;
        a.title = title.trim();
        a.scopeType = scope;
        a.siteId = siteId;
        a.cohortId = cohortId;
        a.programmeId = programmeId;
        a.plannedOn = plannedOn;
        a.leadUserId = lead;
        a.createdBy = lead;
        a.createdAt = Instant.now();
        a.updatedAt = a.createdAt;
        return a;
    }

    public void start(LocalDate today) {
        if (!AuditRules.PLANNED.equals(status)) {
            throw new IllegalStateException("Only a planned audit can be started");
        }
        status = AuditRules.IN_PROGRESS;
        startedOn = today;
        updatedAt = Instant.now();
    }

    public void requireInProgress() {
        if (!AuditRules.IN_PROGRESS.equals(status)) {
            throw new IllegalStateException("The audit is not in progress");
        }
    }

    public void complete(LocalDate today, String summary, UUID by, String aloneReason) {
        requireInProgress();
        completedOn = today;
        completedBy = by;
        completedAloneReason = aloneReason == null || aloneReason.isBlank() ? null : aloneReason.trim();
        this.summary = summary.trim();
        status = AuditRules.COMPLETED;
        updatedAt = Instant.now();
    }

    public void cancel(String reason) {
        if (AuditRules.COMPLETED.equals(status) || AuditRules.CANCELLED.equals(status)) {
            throw new IllegalStateException("This audit is already " + status.toLowerCase());
        }
        String p = AuditRules.noteProblem(reason);
        if (p != null) {
            throw new IllegalArgumentException(p);
        }
        status = AuditRules.CANCELLED;
        cancelledReason = reason.trim();
        updatedAt = Instant.now();
    }
}
