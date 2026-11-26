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

/** One question of an audit, copied from the checklist when the audit was set up. Answered by the auditor's own judgement. */
@Entity(name = "TrainProvAuditItem")
@Table(name = "trainprov_audit_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvAuditItem {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "audit_id", nullable = false, updatable = false)
    private UUID auditId;
    @Column(nullable = false, updatable = false)
    private int position;
    @Column(nullable = false, updatable = false)
    private String question;
    private String outcome;
    private String note;
    @Column(name = "answered_by")
    private UUID answeredBy;
    @Column(name = "answered_at")
    private Instant answeredAt;
    @Version
    private Long version;

    public static TrainProvAuditItem of(TenantId t, UUID auditId, int position, String question) {
        TrainProvAuditItem i = new TrainProvAuditItem();
        i.tenantId = t.getValue();
        i.auditId = auditId;
        i.position = position;
        i.question = question;
        return i;
    }

    public void answer(String outcome, String note, UUID by) {
        String p = AuditRules.answerProblem(outcome, note);
        if (p != null) {
            throw new IllegalArgumentException(p);
        }
        this.outcome = outcome;
        this.note = note == null || note.isBlank() ? null : note.trim();
        this.answeredBy = by;
        this.answeredAt = Instant.now();
    }

    public boolean answered() {
        return outcome != null;
    }
}
