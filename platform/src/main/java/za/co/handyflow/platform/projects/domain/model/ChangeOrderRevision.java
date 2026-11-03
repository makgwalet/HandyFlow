package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * What was submitted, as submitted — one row per submission. A rejected change order that is
 * reopened, edited and resubmitted gets a new row; the old quotation stays readable.
 * Only the outcome fields are ever filled in afterwards (once, when a decision is made).
 */
@Entity
@Table(name = "change_order_revisions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChangeOrderRevision {

    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false)       private UUID tenantId;
    @Column(name = "change_order_id", nullable = false) private UUID changeOrderId;
    @Column(name = "revision", nullable = false)        private int revision;
    @Column(name = "title", nullable = false)           private String title;
    @Column(name = "description")                       private String description;
    @Column(name = "reason")                            private String reason;
    @Column(name = "cost_impact", nullable = false)     private BigDecimal costImpact;
    @Column(name = "schedule_impact", nullable = false) private int scheduleImpact;
    @Column(name = "detail_json")                       private String detailJson;
    @Column(name = "submitted_by")                      private UUID submittedBy;
    @Column(name = "submitted_by_name")                 private String submittedByName;
    @Column(name = "submitted_at", nullable = false)    private Instant submittedAt;
    @Column(name = "outcome")                           private String outcome;
    @Column(name = "outcome_reason")                    private String outcomeReason;
    @Column(name = "decided_by")                        private UUID decidedBy;
    @Column(name = "decided_at")                        private Instant decidedAt;

    public static ChangeOrderRevision open(ChangeOrder co, String detailJson, UUID by, String byName) {
        ChangeOrderRevision r = new ChangeOrderRevision();
        r.id              = UUID.randomUUID();
        r.tenantId        = co.getTenantId();
        r.changeOrderId   = co.getId();
        r.revision        = co.getRevision();
        r.title           = co.getTitle();
        r.description     = co.getDescription();
        r.reason          = co.getReason();
        r.costImpact      = co.getCostImpact();
        r.scheduleImpact  = co.getScheduleImpact();
        r.detailJson      = detailJson;
        r.submittedBy     = by;
        r.submittedByName = byName;
        r.submittedAt     = Instant.now();
        return r;
    }

    /** Records the decision once; a second call is ignored so history can't be rewritten. */
    public void decide(String outcome, String reason, UUID by) {
        if (this.outcome != null) return;
        this.outcome       = outcome;
        this.outcomeReason = reason;
        this.decidedBy     = by;
        this.decidedAt     = Instant.now();
    }
}