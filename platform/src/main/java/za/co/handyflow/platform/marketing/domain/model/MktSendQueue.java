package za.co.handyflow.platform.marketing.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mkt_send_queue")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class MktSendQueue {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "campaign_id",         nullable = false) private UUID    campaignId;
    @Column(name = "campaign_contact_id", nullable = false) private UUID    campaignContactId;
    @Column(name = "tenant_id",           nullable = false) private UUID    tenantId;
    @Column(name = "to_email",            nullable = false) private String  toEmail;
    @Column(name = "to_name")                                private String  toName;
    @Column(nullable = false)                                private String  subject;
    @Column(name = "html_body",           nullable = false) private String  htmlBody;
    @Column(nullable = false)                                private String  status = "PENDING";
    @Column(name = "retry_count",         nullable = false) private int     retryCount = 0;
    @Column(name = "error_message")                          private String  errorMessage;
    @Column(name = "scheduled_at",        nullable = false) private Instant scheduledAt;
    @Column(name = "processed_at")                           private Instant processedAt;
    @Column(name = "created_at")                             private Instant createdAt;

    public static MktSendQueue create(UUID campaignId, UUID campaignContactId,
                                       UUID tenantId, String toEmail, String toName,
                                       String subject, String htmlBody, Instant scheduledAt) {
        MktSendQueue q       = new MktSendQueue();
        q.campaignId         = campaignId;
        q.campaignContactId  = campaignContactId;
        q.tenantId           = tenantId;
        q.toEmail            = toEmail.toLowerCase().trim();
        q.toName             = toName;
        q.subject            = subject;
        q.htmlBody           = htmlBody;
        q.scheduledAt        = scheduledAt != null ? scheduledAt : Instant.now();
        q.status             = "PENDING";
        q.createdAt          = Instant.now();
        return q;
    }

    public void markSent()             { this.status = "SENT";  this.processedAt = Instant.now(); }
    public void markFailed(String msg) {
        this.retryCount++;
        this.errorMessage = msg;
        this.status = retryCount >= 3 ? "DEAD" : "FAILED";
        this.processedAt = Instant.now();
    }
    public void resetForRetry()        { this.status = "PENDING"; }

    public boolean isDead() { return "DEAD".equals(status); }
}
