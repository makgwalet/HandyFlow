package za.co.handyflow.platform.debtcollection.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/** Append-only diagnostic record of what happened to each request an endpoint received (never the payload itself — it can hold personal data). */
@Entity
@Table(name = "debtcollection_webhook_delivery_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WebhookDeliveryLog extends AggregateRoot<WebhookDeliveryLog> {

    @Column(name = "endpoint_id", nullable = false)
    private UUID endpointId;

    /** ACCEPTED, REJECTED_SIGNATURE, BAD_PAYLOAD, PARTIAL_FAILURE. */
    @Column(nullable = false, length = 25)
    private String outcome;

    @Column(name = "http_status", nullable = false)
    private int httpStatus;

    @Column(length = 500)
    private String detail;

    public static WebhookDeliveryLog of(TenantId tenantId, UUID endpointId, String outcome, int httpStatus, String detail) {
        WebhookDeliveryLog l = new WebhookDeliveryLog();
        l.initTenantId(tenantId);
        l.endpointId = endpointId;
        l.outcome = outcome;
        l.httpStatus = httpStatus;
        l.detail = detail != null && detail.length() > 500 ? detail.substring(0, 500) : detail;
        return l;
    }
}
