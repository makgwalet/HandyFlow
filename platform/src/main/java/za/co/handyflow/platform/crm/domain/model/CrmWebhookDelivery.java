package za.co.handyflow.platform.crm.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

/** One attempt to tell a webhook about an event. */
@Entity
@Table(name = "crm_webhook_deliveries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CrmWebhookDelivery {
    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "webhook_id", nullable = false)
    private UUID webhookId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event", nullable = false, length = 30)
    private WebhookEvent event;

    @Column(name = "outcome", nullable = false, length = 10)
    private String outcome;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "detail", length = 300)
    private String detail;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    public static CrmWebhookDelivery of(TenantId tenantId, UUID webhookId, WebhookEvent event, boolean ok, Integer statusCode, String detail) {
        var d = new CrmWebhookDelivery();
        d.tenantId = tenantId;
        d.webhookId = webhookId;
        d.event = event;
        d.outcome = ok ? "DONE" : "FAILED";
        d.statusCode = statusCode;
        d.detail = detail == null ? null : detail.length() > 300 ? detail.substring(0, 299) + "…" : detail;
        d.sentAt = Instant.now();
        return d;
    }
}
