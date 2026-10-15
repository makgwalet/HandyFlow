package za.co.handyflow.platform.internalaudit.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * An engagement report as a document. DRAFT -> FACTUAL_REVIEW (named reviewers comment on facts; the audit team accepts or declines each) ->
 * READY (no open comments) -> ISSUED (signed off through the approval, content fingerprinted and frozen). A new version of an issued report starts
 * as DRAFT; issuing it marks the earlier one SUPERSEDED. Sections are separate rows ({@link AuditReportSection}).
 */
@Entity(name = "AuditReport")
@Table(name = "audit_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditReport {
    public static final int MIN_TEXT = 10;

    @Id private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "engagement_id", nullable = false) private UUID engagementId;
    @Column(name = "version", nullable = false) private int version;
    @Column(name = "template", nullable = false) private String template;
    @Column(name = "title", nullable = false) private String title;
    @Column(name = "status", nullable = false) private String status = "DRAFT";
    @Column(name = "content_hash") private String contentHash;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();
    @Column(name = "fact_review_started_at") private Instant factReviewStartedAt;
    @Column(name = "fact_review_closed_at") private Instant factReviewClosedAt;
    @Column(name = "issued_by") private UUID issuedBy;
    @Column(name = "issued_at") private Instant issuedAt;

    public static AuditReport draft(UUID tenantId, UUID engagementId, int version, String template, String title, UUID by) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A report title is required");
        AuditReport r = new AuditReport();
        r.tenantId = tenantId; r.engagementId = engagementId; r.version = version; r.template = template; r.title = title.trim(); r.createdBy = by;
        return r;
    }

    private void touch() { this.updatedAt = Instant.now(); }

    public boolean isFinal() { return "ISSUED".equals(status) || "SUPERSEDED".equals(status); }
    public boolean contentEditable() { return "DRAFT".equals(status) || "FACTUAL_REVIEW".equals(status); }

    public void requireEditable() {
        if (!contentEditable()) throw new IllegalStateException("The report is " + status.toLowerCase().replace('_', ' ') + " and cannot be edited. Reopen it first, or start a new version if it was issued");
    }

    public void rename(String newTitle) {
        requireEditable();
        if (newTitle == null || newTitle.isBlank()) throw new IllegalArgumentException("A report title is required");
        this.title = newTitle.trim(); touch();
    }

    public void startFactualReview(int reviewers) {
        if (!"DRAFT".equals(status)) throw new IllegalStateException("Only a draft can go for factual-accuracy review");
        if (reviewers < 1) throw new IllegalStateException("Name at least one reviewer before starting the factual-accuracy review");
        this.status = "FACTUAL_REVIEW"; this.factReviewStartedAt = Instant.now(); this.factReviewClosedAt = null; touch();
    }

    public void closeFactualReview(int openItems) {
        if (!"FACTUAL_REVIEW".equals(status)) throw new IllegalStateException("The report is not in factual-accuracy review");
        if (openItems > 0) throw new IllegalStateException(openItems + " reviewer comment" + (openItems == 1 ? " is" : "s are") + " still waiting for a response");
        this.status = "READY"; this.factReviewClosedAt = Instant.now(); touch();
    }

    /** Back to editing. A report that has been through review has to go through it again after any change. */
    public void reopen() {
        if (!"FACTUAL_REVIEW".equals(status) && !"READY".equals(status)) throw new IllegalStateException("Only a report in review or ready to issue can be reopened");
        this.status = "DRAFT"; this.factReviewClosedAt = null; touch();
    }

    public void issue(UUID by, String hash, boolean signedOff) {
        if (!"READY".equals(status)) throw new IllegalStateException("A report can be issued only after its factual-accuracy review is closed");
        if (!signedOff) throw new IllegalStateException("The report has not been signed off yet. Submit it for sign-off and wait for approval before issuing");
        this.status = "ISSUED"; this.issuedBy = by; this.issuedAt = Instant.now(); this.contentHash = hash; touch();
    }

    public void supersede() {
        if (!"ISSUED".equals(status)) throw new IllegalStateException("Only an issued report can be superseded");
        this.status = "SUPERSEDED"; touch();
    }
}
