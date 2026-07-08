package za.co.handyflow.platform.marketing.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

@Entity
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
    @Column(nullable = false)       private String  status = "DRAFT";
    @Column(name = "scheduled_at")  private Instant scheduledAt;
    @Column(name = "sent_at")       private Instant sentAt;
    @Column(name = "recipient_count") private int recipientCount = 0;
    @Column(name = "sent_count")      private int sentCount      = 0;
    @Column(name = "delivered_count") private int deliveredCount = 0;
    @Column(name = "bounced_count")   private int bouncedCount   = 0;
    @Column(name = "unsubscribed_count") private int unsubscribedCount = 0;
    // NEW: previously entirely absent — AnalyticsTab.tsx and CampaignsTab.tsx
    // both already referenced c.openCount/c.clickCount extensively (open
    // rate, click rate, CTOR, per-campaign detail view), silently rendering
    // as 0 for every campaign via their own ?? 0 fallbacks. The frontend was
    // built assuming this data existed; it just never did on the backend.
    @Column(name = "open_count")      private int openCount      = 0;
    @Column(name = "click_count")     private int clickCount     = 0;
    @Column(name = "from_name")     private String fromName;
    @Column(name = "reply_to")      private String replyTo;
    @Column(name = "created_by")    private UUID   createdBy;
    @Column(name = "created_at")    private Instant createdAt;
    @Column(name = "updated_at")    private Instant updatedAt;
    @Column(name = "deleted_at")    private Instant deletedAt;

    @Version private Long version;

    public static MktCampaign create(TenantId tenantId, String name, String channel,
                                     UUID templateId, String subject, String htmlBody,
                                     String audienceType, String audienceFilter,
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
        c.scheduledAt      = scheduledAt;
        c.fromName         = fromName;
        c.replyTo          = replyTo;
        c.createdBy        = createdBy;
        c.status           = "DRAFT";
        c.createdAt        = Instant.now();
        c.updatedAt        = Instant.now();
        return c;
    }

    public void schedule(Instant at)     { this.scheduledAt = at; this.status = "SCHEDULED"; touch(); }
    public void startSending(int count)  { this.status = "SENDING"; this.recipientCount = count; touch(); }
    public void markSent()               { this.status = "SENT"; this.sentAt = Instant.now(); touch(); }
    public void pause()                  { this.status = "PAUSED"; touch(); }
    public void resume()                 { this.status = "SENDING"; touch(); }
    public void cancel()                 { this.status = "CANCELLED"; touch(); }

    public void incrementSent()          { this.sentCount++; touch(); }
    public void incrementBounced()       { this.bouncedCount++; touch(); }
    public void incrementUnsubscribed()  { this.unsubscribedCount++; touch(); }
    public void incrementOpened()        { this.openCount++; touch(); }
    public void incrementClicked()       { this.clickCount++; touch(); }

    private void touch() { this.updatedAt = Instant.now(); }

    public boolean isDraft()     { return "DRAFT".equals(status); }
    public boolean isSending()   { return "SENDING".equals(status); }
}
