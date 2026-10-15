package za.co.handyflow.platform.internalaudit.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.internalaudit.application.internal.ControlLibrary.Control;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** One check on an engagement's programme, with its wording frozen from the library and the auditor's result. */
@Entity(name = "AuditProgrammeStep")
@Table(name = "audit_programme_steps")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditProgrammeStep {
    public static final int MIN_NOTE = 10;
    public static final Set<String> STATUSES = Set.of("IN_PROGRESS", "COMPLETED", "NOT_APPLICABLE");
    public static final Set<String> CONCLUSIONS = Set.of("EFFECTIVE", "PARTIALLY_EFFECTIVE", "INEFFECTIVE");

    @Id private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "engagement_id", nullable = false) private UUID engagementId;
    @Column(name = "control_code", nullable = false) private String controlCode;
    @Column(name = "domain", nullable = false) private String domain;
    @Column(name = "area", nullable = false) private String area;
    @Column(name = "objective", columnDefinition = "TEXT", nullable = false) private String objective;
    @Column(name = "procedure", columnDefinition = "TEXT", nullable = false) private String procedure;
    @Column(name = "reference") private String reference;
    @Column(name = "status", nullable = false) private String status = "NOT_STARTED";
    @Column(name = "conclusion") private String conclusion;
    @Column(name = "note", columnDefinition = "TEXT") private String note;
    @Column(name = "finding_id") private UUID findingId;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_by") private UUID updatedBy;
    @Column(name = "updated_by_name") private String updatedByName;
    @Column(name = "updated_at") private Instant updatedAt;

    public static AuditProgrammeStep from(UUID tenantId, UUID engagementId, Control c, UUID by) {
        AuditProgrammeStep s = new AuditProgrammeStep();
        s.tenantId = tenantId; s.engagementId = engagementId; s.controlCode = c.code(); s.domain = c.domain(); s.area = c.area();
        s.objective = c.objective(); s.procedure = c.procedure(); s.reference = c.reference().isBlank() ? null : c.reference(); s.createdBy = by;
        return s;
    }

    /**
     * IN_PROGRESS keeps any note. COMPLETED needs a conclusion and a note saying what was done. NOT_APPLICABLE needs a note saying why.
     * A finished step can be reopened to IN_PROGRESS (its earlier conclusion is cleared, and the audit trail keeps it).
     */
    public void record(String status, String conclusion, String note, UUID by, String byName) {
        String st = status == null ? "" : status.toUpperCase();
        if (!STATUSES.contains(st)) throw new IllegalArgumentException("Status must be IN_PROGRESS, COMPLETED or NOT_APPLICABLE");
        String n = note == null ? null : note.trim();
        String con = conclusion == null || conclusion.isBlank() ? null : conclusion.toUpperCase();
        switch (st) {
            case "COMPLETED" -> {
                if (con == null || !CONCLUSIONS.contains(con)) throw new IllegalArgumentException("Conclude EFFECTIVE, PARTIALLY_EFFECTIVE or INEFFECTIVE");
                if (n == null || n.length() < MIN_NOTE) throw new IllegalArgumentException("Say what was done (at least " + MIN_NOTE + " characters)");
            }
            case "NOT_APPLICABLE" -> {
                if (con != null) throw new IllegalArgumentException("A step that does not apply has no conclusion");
                if (n == null || n.length() < MIN_NOTE) throw new IllegalArgumentException("Say why it does not apply (at least " + MIN_NOTE + " characters)");
            }
            default -> con = null;
        }
        this.status = st; this.conclusion = con; this.note = n == null || n.isEmpty() ? null : n;
        this.updatedBy = by; this.updatedByName = byName; this.updatedAt = Instant.now();
    }

    public void linkFinding(UUID findingId) { this.findingId = findingId; }

    /** An ineffective or partly effective result that nobody has raised a finding for. */
    public boolean needsFinding() { return ("INEFFECTIVE".equals(conclusion) || "PARTIALLY_EFFECTIVE".equals(conclusion)) && findingId == null; }
}
