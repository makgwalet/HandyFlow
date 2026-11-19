package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An audit engagement, internal or external. PLANNED → FIELDWORK → REPORTING → COMPLETED (or CANCELLED).
 * Findings can only be raised during fieldwork or reporting. Completing needs an overall rating and a written conclusion.
 */
@Entity
@Table(name = "legalcompliance_audits")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Audit extends AggregateRoot<Audit> {

    public static final int MIN_TEXT = 15;
    public static final int MIN_CONCLUSION = 30;

    @Column(nullable = false, length = 20) private String reference;
    @Column(nullable = false, length = 255) private String title;
    @Enumerated(EnumType.STRING) @Column(name = "audit_type", nullable = false, length = 10) private AuditType auditType;
    @Column(nullable = false, columnDefinition = "TEXT") private String scope;
    @Column(nullable = false, columnDefinition = "TEXT") private String objectives;
    @Column(name = "work_programme", columnDefinition = "TEXT") private String workProgramme;
    @Column(name = "auditor_id") private UUID auditorId;
    @Column(name = "lead_auditor_name", nullable = false, length = 255) private String leadAuditorName;
    @Column(name = "auditing_firm", length = 255) private String auditingFirm;
    @Column(name = "planned_start", nullable = false) private LocalDate plannedStart;
    @Column(name = "planned_end", nullable = false) private LocalDate plannedEnd;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) private AuditStatus status = AuditStatus.PLANNED;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "control_ids", nullable = false, columnDefinition = "jsonb") private List<UUID> controlIds = new ArrayList<>();
    @Column(name = "started_at") private Instant startedAt;
    @Enumerated(EnumType.STRING) @Column(name = "overall_rating", length = 20) private AuditRating overallRating;
    @Column(columnDefinition = "TEXT") private String conclusion;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "completed_by_name", length = 255) private String completedByName;
    @Column(name = "cancel_reason", columnDefinition = "TEXT") private String cancelReason;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_by_name", length = 255) private String createdByName;

    public static Audit plan(TenantId tenantId, String reference, String title, AuditType type, String scope, String objectives, String workProgramme,
                             UUID auditorId, String leadAuditorName, String auditingFirm, LocalDate start, LocalDate end, List<UUID> controlIds, UUID by, String byName) {
        Audit a = new Audit();
        a.initTenantId(tenantId);
        a.reference = reference;
        a.createdBy = by;
        a.createdByName = byName;
        a.apply(title, type, scope, objectives, workProgramme, auditorId, leadAuditorName, auditingFirm, start, end, controlIds);
        return a;
    }

    public void update(String title, AuditType type, String scope, String objectives, String workProgramme, UUID auditorId, String leadAuditorName,
                       String auditingFirm, LocalDate start, LocalDate end, List<UUID> controlIds) {
        if (status != AuditStatus.PLANNED && status != AuditStatus.FIELDWORK) throw new IllegalStateException("An audit can only be edited while planned or in fieldwork — current: " + status);
        apply(title, type, scope, objectives, workProgramme, auditorId, leadAuditorName, auditingFirm, start, end, controlIds);
    }

    private void apply(String title, AuditType type, String scope, String objectives, String workProgramme, UUID auditorId, String leadAuditorName,
                       String auditingFirm, LocalDate start, LocalDate end, List<UUID> controlIds) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A title is required");
        if (type == null) throw new IllegalArgumentException("An audit type is required");
        if (scope == null || scope.trim().length() < MIN_TEXT) throw new IllegalArgumentException("Describe the scope (at least " + MIN_TEXT + " characters)");
        if (objectives == null || objectives.trim().length() < MIN_TEXT) throw new IllegalArgumentException("State the objectives (at least " + MIN_TEXT + " characters)");
        if (leadAuditorName == null || leadAuditorName.isBlank()) throw new IllegalArgumentException("A lead auditor is required");
        if (type == AuditType.EXTERNAL && (auditingFirm == null || auditingFirm.isBlank())) throw new IllegalArgumentException("An external audit needs the auditing firm");
        if (start == null || end == null) throw new IllegalArgumentException("Planned start and end dates are required");
        if (end.isBefore(start)) throw new IllegalArgumentException("The planned end cannot be before the start");
        this.title = title.trim();
        this.auditType = type;
        this.scope = scope.trim();
        this.objectives = objectives.trim();
        this.workProgramme = workProgramme == null || workProgramme.isBlank() ? null : workProgramme.trim();
        this.auditorId = auditorId;
        this.leadAuditorName = leadAuditorName.trim();
        this.auditingFirm = type == AuditType.EXTERNAL ? auditingFirm.trim() : null;
        this.plannedStart = start;
        this.plannedEnd = end;
        this.controlIds = controlIds == null ? new ArrayList<>() : new ArrayList<>(controlIds.stream().distinct().toList());
    }

    public void startFieldwork() {
        if (status != AuditStatus.PLANNED) throw new IllegalStateException("Only a planned audit can start fieldwork — current: " + status);
        this.status = AuditStatus.FIELDWORK;
        this.startedAt = Instant.now();
    }

    public void moveToReporting() {
        if (status != AuditStatus.FIELDWORK) throw new IllegalStateException("Only an audit in fieldwork can move to reporting — current: " + status);
        this.status = AuditStatus.REPORTING;
    }

    public void complete(AuditRating rating, String conclusion, String byName) {
        if (status != AuditStatus.REPORTING) throw new IllegalStateException("Only an audit in reporting can be completed — current: " + status);
        if (rating == null) throw new IllegalArgumentException("An overall rating is required");
        if (conclusion == null || conclusion.trim().length() < MIN_CONCLUSION) throw new IllegalArgumentException("Write the audit conclusion (at least " + MIN_CONCLUSION + " characters)");
        this.overallRating = rating;
        this.conclusion = conclusion.trim();
        this.completedAt = Instant.now();
        this.completedByName = byName;
        this.status = AuditStatus.COMPLETED;
    }

    public void cancel(String reason) {
        if (status == AuditStatus.COMPLETED || status == AuditStatus.CANCELLED) throw new IllegalStateException("The audit is already " + status);
        if (reason == null || reason.trim().length() < MIN_TEXT) throw new IllegalArgumentException("A reason of at least " + MIN_TEXT + " characters is required");
        this.cancelReason = reason.trim();
        this.status = AuditStatus.CANCELLED;
    }

    public boolean acceptsFindings() { return status == AuditStatus.FIELDWORK || status == AuditStatus.REPORTING; }
    public boolean isFinished() { return status == AuditStatus.COMPLETED || status == AuditStatus.CANCELLED; }
    public boolean isOverdue(LocalDate today) { return !isFinished() && plannedEnd.isBefore(today); }
}
