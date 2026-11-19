package za.co.handyflow.platform.marketing.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

@Entity
@DynamicUpdate
@Table(name = "mkt_campaigns")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class MktCampaign {

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false) private String name;
    @Column(nullable = false) private String channel = "EMAIL";
    @Column(name = "template_id")   private UUID   templateId;
    private String subject;
    @Column(name = "html_body")     private String htmlBody;
    @Column(name = "audience_type", nullable = false) private String audienceType = "ALL_OPTED_IN";
    @Column(name = "audience_filter", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String audienceFilter;
    @Column(name = "segment_id")    private UUID   segmentId;
    @Column(name = "segment_name")  private String segmentName;
    @Column(nullable = false)       private String  status = "DRAFT";
    @Column(name = "scheduled_at")  private Instant scheduledAt;
    @Column(name = "sent_at")       private Instant sentAt;
    @Column(name = "recipient_count") private int recipientCount = 0;
    @Column(name = "sent_count")      private int sentCount      = 0;
    @Column(name = "delivered_count") private int deliveredCount = 0;
    @Column(name = "bounced_count")   private int bouncedCount   = 0;
    @Column(name = "unsubscribed_count") private int unsubscribedCount = 0;
    @Column(name = "open_count")      private int openCount      = 0;
    @Column(name = "click_count")     private int clickCount     = 0;
    @Column(name = "suppressed_count") private int suppressedCount = 0;
    @Column(name = "complained_count") private int complainedCount = 0;
    @Column(name = "failed_count")     private int failedCount     = 0;
    @Column(name = "pause_reason", columnDefinition = "text") private String pauseReason;
    @Column(name = "from_name")     private String fromName;
    @Column(name = "reply_to")      private String replyTo;
    @Column(name = "created_by")    private UUID   createdBy;
    @Column(name = "created_at")    private Instant createdAt;
    @Column(name = "updated_at")    private Instant updatedAt;
    @Column(name = "deleted_at")    private Instant deletedAt;

    // ── Chunk 5: versions and review ──
    @Column(name = "content_version", nullable = false) private int contentVersion = 1;
    @Column(name = "review_status", nullable = false)   private String reviewStatus = "NOT_SUBMITTED";
    @Column(name = "submitted_by")    private UUID    submittedBy;
    @Column(name = "submitted_at")    private Instant submittedAt;
    @Column(name = "reviewed_by")     private UUID    reviewedBy;
    @Column(name = "reviewed_at")     private Instant reviewedAt;
    @Column(name = "review_note", columnDefinition = "text") private String reviewNote;
    @Column(name = "approved_version") private Integer approvedVersion;
    @Column(name = "sent_version")     private Integer sentVersion;
    @Column(name = "kind", nullable = false) private String kind = "CAMPAIGN";
    @Column(name = "topic_id") private UUID topicId;

    @Version private Long version;

    public static MktCampaign create(TenantId tenantId, String name, String channel,
                                     UUID templateId, String subject, String htmlBody,
                                     String audienceType, String audienceFilter,
                                     UUID segmentId, String segmentName,
                                     Instant scheduledAt, String fromName,
                                     String replyTo, UUID createdBy) {
        MktCampaign c      = new MktCampaign();
        c.tenantId         = tenantId;
        c.name             = name;
        c.channel          = channel != null ? channel : "EMAIL";
        c.templateId       = templateId;
        c.subject          = subject;
        c.htmlBody         = htmlBody;
        c.audienceType     = audienceType != null ? audienceType : "ALL_OPTED_IN";
        c.audienceFilter   = audienceFilter;
        c.segmentId        = segmentId;
        c.segmentName      = segmentName;
        c.scheduledAt      = scheduledAt;
        c.fromName         = fromName;
        c.replyTo          = replyTo;
        c.createdBy        = createdBy;
        c.status           = "DRAFT";
        c.contentVersion   = 1;
        c.createdAt        = Instant.now();
        c.updatedAt        = Instant.now();
        return c;
    }

    // ── Editing ──────────────────────────────────────────────────────────────

    /**
     * Applies an edit. Only DRAFT/SCHEDULED campaigns are editable.
     * "Content" = subject, body, from, reply-to, audience. A content change bumps contentVersion and
     * voids any review/approval. Name, template reference and schedule are not content.
     * @return true if the content version was bumped.
     */
    public boolean applyEdit(String name, UUID templateId, String subject, String htmlBody,
                             String audienceType, UUID segmentId, String segmentName,
                             String fromName, String replyTo, Instant scheduledAt) {
        requireStatus("edit", "DRAFT", "SCHEDULED");
        boolean contentChanged = !Objects.equals(this.subject, subject)
                || !Objects.equals(this.htmlBody, htmlBody)
                || !Objects.equals(this.fromName, fromName)
                || !Objects.equals(this.replyTo, replyTo)
                || !Objects.equals(this.audienceType, audienceType)
                || !Objects.equals(this.segmentId, segmentId);

        this.name = name; this.templateId = templateId;
        this.subject = subject; this.htmlBody = htmlBody;
        this.audienceType = audienceType; this.segmentId = segmentId; this.segmentName = segmentName;
        this.fromName = fromName; this.replyTo = replyTo;

        if (contentChanged) { contentVersion++; resetReview(); }
        if (scheduledAt != null) { this.scheduledAt = scheduledAt; this.status = "SCHEDULED"; }
        else                     { this.scheduledAt = null;        this.status = "DRAFT"; }
        touch();
        return contentChanged;
    }

    private void resetReview() {
        reviewStatus = "NOT_SUBMITTED"; approvedVersion = null;
        submittedBy = null; submittedAt = null; reviewedBy = null; reviewedAt = null; reviewNote = null;
    }

    // ── Review workflow ──────────────────────────────────────────────────────

    public void submitForReview(UUID by) {
        requireStatus("submit for review", "DRAFT", "SCHEDULED");
        if (!"NOT_SUBMITTED".equals(reviewStatus) && !"CHANGES_REQUESTED".equals(reviewStatus)) {
            throw new IllegalStateException("Cannot submit a campaign whose review status is " + reviewStatus);
        }
        reviewStatus = "IN_REVIEW"; submittedBy = by; submittedAt = Instant.now();
        reviewedBy = null; reviewedAt = null; reviewNote = null; approvedVersion = null;
        touch();
    }

    public void withdrawReview() {
        requireReview("withdraw", "IN_REVIEW");
        reviewStatus = "NOT_SUBMITTED"; submittedBy = null; submittedAt = null; touch();
    }

    public void approve(UUID by, String note) {
        requireStatus("approve", "DRAFT", "SCHEDULED");
        requireReview("approve", "IN_REVIEW");
        reviewStatus = "APPROVED"; approvedVersion = contentVersion;
        reviewedBy = by; reviewedAt = Instant.now(); reviewNote = note; touch();
    }

    public void requestChanges(UUID by, String note) {
        requireStatus("request changes on", "DRAFT", "SCHEDULED");
        requireReview("request changes on", "IN_REVIEW");
        reviewStatus = "CHANGES_REQUESTED"; approvedVersion = null;
        reviewedBy = by; reviewedAt = Instant.now(); reviewNote = note; touch();
    }

    /** Called at launch: records which version went out. */
    public void recordSentVersion() { this.sentVersion = this.contentVersion; touch(); }

    // ── State machine: invalid transitions throw IllegalStateException (→ HTTP 409) ──

    public void schedule(Instant at) {
        requireStatus("schedule", "DRAFT", "SCHEDULED");
        this.scheduledAt = at; this.status = "SCHEDULED"; touch();
    }

    public void startSending(int count) {
        requireStatus("launch", "DRAFT", "SCHEDULED");
        this.status = "SENDING"; this.recipientCount = count; touch();
    }

    public void recordLaunchSuppressed(int count) { this.suppressedCount = count; touch(); }
    public void refreshSegmentName(String name) { this.segmentName = name; touch(); }
    public void markSent() { this.status = "SENT"; this.sentAt = Instant.now(); touch(); }
    /** Marks this as a hidden journey-step campaign (excluded from campaign lists and overviews). */
    public void markJourney() { this.kind = "JOURNEY"; touch(); }

    public void pause() {
        requireStatus("pause", "SENDING");
        this.status = "PAUSED"; this.pauseReason = null; touch();
    }

    public void resume() {
        requireStatus("resume", "PAUSED");
        this.status = "SENDING"; this.pauseReason = null; touch();
    }

    public void cancel() {
        requireStatus("cancel", "DRAFT", "SCHEDULED", "SENDING", "PAUSED");
        this.status = "CANCELLED"; touch();
    }

    public void markFailed() { markFailed(null); }

    public void markFailed(String reason) {
        requireStatus("fail", "DRAFT", "SCHEDULED", "SENDING");
        this.status = "FAILED";
        this.pauseReason = reason == null ? null : (reason.length() > 500 ? reason.substring(0, 500) : reason);
        touch();
    }

    public void incrementSent()          { this.sentCount++; touch(); }
    public void incrementBounced()       { this.bouncedCount++; touch(); }
    public void incrementUnsubscribed()  { this.unsubscribedCount++; touch(); }
    public void incrementOpened()        { this.openCount++; touch(); }
    public void incrementClicked()       { this.clickCount++; touch(); }

    private void requireStatus(String action, String... allowed) {
        if (!Arrays.asList(allowed).contains(status)) {
            throw new IllegalStateException("Cannot " + action + " a campaign that is " + status);
        }
    }
    private void requireReview(String action, String... allowed) {
        if (!Arrays.asList(allowed).contains(reviewStatus)) {
            throw new IllegalStateException("Cannot " + action + " a campaign whose review status is " + reviewStatus);
        }
    }

    /** Topic this campaign is about (null = none). Changing it is an audience change, so it voids any review. */
    public void changeTopic(UUID topicId) {
        requireStatus("change the topic of", "DRAFT", "SCHEDULED");
        if (!Objects.equals(this.topicId, topicId)) { this.topicId = topicId; resetReview(); }
        touch();
    }

    private void touch() { this.updatedAt = Instant.now(); }

    public boolean isDraft()     { return "DRAFT".equals(status); }
    public boolean isSending()   { return "SENDING".equals(status); }
}