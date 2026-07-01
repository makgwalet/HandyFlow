// security/domain/model/WebhookDelivery.java
package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "security_webhook_deliveries")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class WebhookDelivery {

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "subscription_id", nullable = false) private UUID subscriptionId;
    @Column(name = "event_type", nullable = false, length = 50) private String eventType;
    @Column(name = "event_id", nullable = false) private UUID eventId;
    @Column(name = "payload_hash", length = 64) private String payloadHash;
    @Column(name = "attempt_number", nullable = false) private int attemptNumber = 1;
    @Column(name = "http_status") private Integer httpStatus;
    @Column(name = "response_body") private String responseBody;
    @Column(name = "delivered_at") private Instant deliveredAt;
    @Column(name = "failed_at") private Instant failedAt;
    @Column(name = "failure_reason") private String failureReason;
    @Column(name = "next_retry_at") private Instant nextRetryAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    public static WebhookDelivery attempt(TenantId tenantId, UUID subscriptionId,
                                          String eventType, UUID eventId,
                                          String payloadHash, int attemptNumber) {
        WebhookDelivery d = new WebhookDelivery();
        d.tenantId = tenantId; d.subscriptionId = subscriptionId;
        d.eventType = eventType; d.eventId = eventId;
        d.payloadHash = payloadHash; d.attemptNumber = attemptNumber;
        d.createdAt = Instant.now();
        return d;
    }

    public void markDelivered(int httpStatus, String responseBody) {
        this.httpStatus = httpStatus; this.responseBody = truncate(responseBody);
        this.deliveredAt = Instant.now(); this.nextRetryAt = null;
    }

    public void markFailed(int httpStatus, String reason, Instant nextRetryAt) {
        this.httpStatus = httpStatus; this.failureReason = reason;
        this.failedAt = Instant.now(); this.nextRetryAt = nextRetryAt;
    }

    public void markNetworkFailed(String reason, Instant nextRetryAt) {
        this.failureReason = reason; this.failedAt = Instant.now(); this.nextRetryAt = nextRetryAt;
    }

    public boolean succeeded() { return deliveredAt != null; }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 1024 ? s.substring(0, 1024) + "...[truncated]" : s;
    }
}